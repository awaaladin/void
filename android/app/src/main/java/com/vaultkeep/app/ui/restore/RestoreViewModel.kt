package com.vaultkeep.app.ui.restore

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaultkeep.app.data.auth.DriveAuthManager
import com.vaultkeep.app.data.crypto.CryptoKeyManager
import com.vaultkeep.app.data.crypto.DecryptingBackupSource
import com.vaultkeep.app.data.destination.CloudSource
import com.vaultkeep.app.data.destination.GoogleDriveSource
import com.vaultkeep.app.data.destination.SmbSource
import com.vaultkeep.app.data.destination.UsbSource
import com.vaultkeep.app.data.destination.WebDavSource
import com.vaultkeep.app.data.local.NasCredentialsStore
import com.vaultkeep.app.data.local.dao.BackupRecordDao
import com.vaultkeep.app.data.local.dao.RestorableFileRow
import com.vaultkeep.app.data.remote.DriveApi
import com.vaultkeep.app.data.remote.VoidApi
import com.vaultkeep.app.domain.BackupSource
import com.vaultkeep.app.domain.model.NasProtocol
import com.vaultkeep.app.domain.model.destinationLabel
import com.vaultkeep.app.domain.usecase.RestoreProgressEvent
import com.vaultkeep.app.domain.usecase.RestoreRunSummary
import com.vaultkeep.app.domain.usecase.RestoreUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RestorableDestination(val id: String, val label: String, val fileCount: Int, val totalBytes: Long)

data class RestoreUiState(
    val destinations: List<RestorableDestination> = emptyList(),
    val selectedDestinationId: String? = null,
    val files: List<RestorableFileRow> = emptyList(),
    val selectedFileIds: Set<Long> = emptySet(),
    val targetUri: Uri? = null,
    val isRunning: Boolean = false,
    val progress: RestoreProgressEvent? = null,
    val summary: RestoreRunSummary? = null,
    val errorMessage: String? = null,
) {
    val canStart: Boolean get() = targetUri != null && selectedFileIds.isNotEmpty() && !isRunning
}

@HiltViewModel
class RestoreViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val backupRecordDao: BackupRecordDao,
    private val restoreUseCase: RestoreUseCase,
    private val nasCredentialsStore: NasCredentialsStore,
    private val voidApi: VoidApi,
    private val driveApi: DriveApi,
    private val driveAuthManager: DriveAuthManager,
    private val keyManager: CryptoKeyManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(RestoreUiState())
    val uiState: StateFlow<RestoreUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            backupRecordDao.observeDestinationSummaries().collectLatest { rows ->
                _uiState.value = _uiState.value.copy(
                    destinations = rows.map {
                        RestorableDestination(it.destinationId, destinationLabel(it.destinationId), it.fileCount, it.totalBytes)
                    },
                )
            }
        }
    }

    fun selectDestination(destinationId: String) {
        viewModelScope.launch {
            val files = backupRecordDao.restorableFiles(destinationId)
            _uiState.value = _uiState.value.copy(
                selectedDestinationId = destinationId,
                files = files,
                selectedFileIds = files.map { it.fileId }.toSet(),
                summary = null,
                errorMessage = null,
            )
        }
    }

    fun onTargetPicked(uri: Uri) {
        _uiState.value = _uiState.value.copy(targetUri = uri)
    }

    fun toggleFile(fileId: Long) {
        val current = _uiState.value.selectedFileIds
        _uiState.value = _uiState.value.copy(
            selectedFileIds = if (fileId in current) current - fileId else current + fileId,
        )
    }

    fun toggleSelectAll() {
        val state = _uiState.value
        val allSelected = state.selectedFileIds.size == state.files.size
        _uiState.value = state.copy(
            selectedFileIds = if (allSelected) emptySet() else state.files.map { it.fileId }.toSet(),
        )
    }

    fun startRestore() {
        val state = _uiState.value
        val destinationId = state.selectedDestinationId ?: return
        val targetUri = state.targetUri ?: return
        val filesToRestore = state.files.filter { it.fileId in state.selectedFileIds }
        if (filesToRestore.isEmpty()) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRunning = true, summary = null, errorMessage = null)
            try {
                val source = sourceFor(destinationId)
                val decryptingSource = DecryptingBackupSource(source, keyManager)
                val summary = restoreUseCase.execute(
                    files = filesToRestore,
                    source = decryptingSource,
                    targetTreeUri = targetUri,
                    onProgress = { event -> _uiState.value = _uiState.value.copy(progress = event) },
                )
                _uiState.value = _uiState.value.copy(isRunning = false, summary = summary)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isRunning = false, errorMessage = e.message ?: "Restore failed")
            }
        }
    }

    private suspend fun sourceFor(destinationId: String): BackupSource = when {
        destinationId.startsWith("usb:") -> UsbSource(
            context = context,
            id = destinationId,
            treeUri = Uri.parse(destinationId.removePrefix("usb:")),
        )
        destinationId.startsWith("smb:") -> {
            val config = nasCredentialsStore.load()?.takeIf { it.protocol == NasProtocol.SMB }
                ?: error("This NAS isn't configured on this device anymore — set it up again under 'Set up home NAS' first.")
            SmbSource(config, destinationId)
        }
        destinationId.startsWith("webdav:") -> {
            val config = nasCredentialsStore.load()?.takeIf { it.protocol == NasProtocol.WEBDAV }
                ?: error("This NAS isn't configured on this device anymore — set it up again under 'Set up home NAS' first.")
            WebDavSource(config, destinationId)
        }
        destinationId.startsWith("cloud:") -> CloudSource(voidApi, destinationId)
        destinationId.startsWith("drive:") -> {
            // Restore intentionally doesn't wire up its own consent UI: if Drive was ever
            // connected via "Set up Google Drive" this session or earlier, Play Services
            // returns a fresh token here non-interactively. If it hasn't, ask for that
            // instead of launching a second consent flow from inside Restore.
            val outcome = driveAuthManager.authorize()
            val token = (outcome as? DriveAuthManager.AuthorizeOutcome.Authorized)?.accessToken
                ?: error("Open 'Set up Google Drive' and connect it first, then come back to restore.")
            GoogleDriveSource(driveApi, token, destinationId)
        }
        else -> error("Unknown destination: $destinationId")
    }
}
