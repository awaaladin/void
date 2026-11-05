package com.vaultkeep.app.ui.backupsetup

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaultkeep.app.data.destination.UsbDestination
import com.vaultkeep.app.data.local.BackupSetupPreferences
import com.vaultkeep.app.domain.model.BackupSetKind
import com.vaultkeep.app.domain.usecase.BackupProgressEvent
import com.vaultkeep.app.domain.usecase.BackupRunSummary
import com.vaultkeep.app.domain.usecase.RunUsbBackupUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import android.content.Context
import javax.inject.Inject

data class BackupSetupUiState(
    val sourceUri: String? = null,
    val destinationUri: String? = null,
    val isRunning: Boolean = false,
    val progress: BackupProgressEvent? = null,
    val summary: BackupRunSummary? = null,
    val errorMessage: String? = null,
) {
    val canStart: Boolean get() = sourceUri != null && destinationUri != null && !isRunning
}

@HiltViewModel
class BackupSetupViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferences: BackupSetupPreferences,
    private val runUsbBackupUseCase: RunUsbBackupUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(BackupSetupUiState())
    val uiState: StateFlow<BackupSetupUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            preferences.sourceUri.collect { uri ->
                _uiState.value = _uiState.value.copy(sourceUri = uri)
            }
        }
        viewModelScope.launch {
            preferences.destinationUri.collect { uri ->
                _uiState.value = _uiState.value.copy(destinationUri = uri)
            }
        }
    }

    /** Called after the ACTION_OPEN_DOCUMENT_TREE result already took persistable permission. */
    fun onSourcePicked(uri: Uri) {
        viewModelScope.launch { preferences.setSourceUri(uri.toString()) }
    }

    fun onDestinationPicked(uri: Uri) {
        viewModelScope.launch { preferences.setDestinationUri(uri.toString()) }
    }

    fun startBackup() {
        val state = _uiState.value
        val sourceUri = state.sourceUri?.let(Uri::parse) ?: return
        val destinationUri = state.destinationUri?.let(Uri::parse) ?: return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRunning = true, summary = null, errorMessage = null)

            val destination = UsbDestination(
                context = context,
                id = "usb:$destinationUri",
                displayName = "USB/SSD destination",
                treeUri = destinationUri,
            )

            try {
                val summary = runUsbBackupUseCase.execute(
                    sourceTreeUri = sourceUri,
                    destination = destination,
                    setType = BackupSetKind.CUSTOM,
                    onProgress = { event ->
                        _uiState.value = _uiState.value.copy(progress = event)
                    },
                )
                _uiState.value = _uiState.value.copy(isRunning = false, summary = summary)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isRunning = false, errorMessage = e.message ?: "Backup failed")
            }
        }
    }
}
