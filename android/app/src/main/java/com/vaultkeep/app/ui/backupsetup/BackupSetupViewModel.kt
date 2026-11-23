package com.vaultkeep.app.ui.backupsetup

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaultkeep.app.data.local.BackupSetsPreferences
import com.vaultkeep.app.data.local.BackupSetupPreferences
import com.vaultkeep.app.domain.model.BackupSetKind
import com.vaultkeep.app.domain.usecase.BackupProgressEvent
import com.vaultkeep.app.domain.usecase.BackupRunSummary
import com.vaultkeep.app.worker.BackupScheduler
import com.vaultkeep.app.worker.BackupWorker
import com.vaultkeep.app.worker.toBackupWorkState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BackupSetupUiState(
    val sourceUri: String? = null,
    val destinationUri: String? = null,
    val anyBackupSetEnabled: Boolean = false,
    val isRunning: Boolean = false,
    val progress: BackupProgressEvent? = null,
    val summary: BackupRunSummary? = null,
    val errorMessage: String? = null,
) {
    val canStart: Boolean get() = destinationUri != null && (sourceUri != null || anyBackupSetEnabled) && !isRunning
}

@HiltViewModel
class BackupSetupViewModel @Inject constructor(
    private val preferences: BackupSetupPreferences,
    private val backupSetsPreferences: BackupSetsPreferences,
    private val backupScheduler: BackupScheduler,
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
        viewModelScope.launch {
            combine(
                backupSetsPreferences.isEnabled(BackupSetKind.PHOTOS),
                backupSetsPreferences.isEnabled(BackupSetKind.VIDEOS),
            ) { photos, videos -> photos || videos }.collect { anyEnabled ->
                _uiState.value = _uiState.value.copy(anyBackupSetEnabled = anyEnabled)
            }
        }
        observeBackupWork()
    }

    /** Called after the ACTION_OPEN_DOCUMENT_TREE result already took persistable permission. */
    fun onSourcePicked(uri: Uri) {
        viewModelScope.launch { preferences.setSourceUri(uri.toString()) }
    }

    fun onDestinationPicked(uri: Uri) {
        viewModelScope.launch { preferences.setDestinationUri(uri.toString()) }
    }

    /**
     * Watches the real WorkManager state for this destination's backups — see
     * [BackupScheduler] for why this isn't just run in this ViewModel's own scope.
     */
    private fun observeBackupWork() = viewModelScope.launch {
        backupScheduler.observe(BackupWorker.UNIQUE_ONE_TIME_PREFIX + BackupWorker.DESTINATION_USB).collect { workInfo ->
            val state = workInfo.toBackupWorkState()
            _uiState.value = _uiState.value.copy(
                isRunning = state.isRunning,
                progress = state.progress ?: _uiState.value.progress.takeIf { state.isRunning },
                summary = state.summary ?: _uiState.value.summary,
                errorMessage = state.error ?: _uiState.value.errorMessage,
            )
        }
    }

    fun startBackup() {
        if (_uiState.value.destinationUri == null) return
        _uiState.value = _uiState.value.copy(isRunning = true, summary = null, errorMessage = null)
        backupScheduler.runNowTo(BackupWorker.DESTINATION_USB)
    }
}
