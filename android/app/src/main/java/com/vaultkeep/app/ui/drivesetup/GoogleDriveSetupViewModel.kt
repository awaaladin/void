package com.vaultkeep.app.ui.drivesetup

import android.content.Intent
import android.content.IntentSender
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaultkeep.app.data.auth.DriveAuthManager
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GoogleDriveSetupUiState(
    val isAuthorized: Boolean = false,
    val isAuthorizing: Boolean = false,
    val pendingConsent: IntentSender? = null,
    val errorMessage: String? = null,
    val backupRunning: Boolean = false,
    val backupProgress: BackupProgressEvent? = null,
    val backupSummary: BackupRunSummary? = null,
    val noSourceConfigured: Boolean = false,
)

@HiltViewModel
class GoogleDriveSetupViewModel @Inject constructor(
    private val driveAuthManager: DriveAuthManager,
    private val backupSetupPreferences: BackupSetupPreferences,
    private val backupSetsPreferences: BackupSetsPreferences,
    private val backupScheduler: BackupScheduler,
) : ViewModel() {

    private val _uiState = MutableStateFlow(GoogleDriveSetupUiState())
    val uiState: StateFlow<GoogleDriveSetupUiState> = _uiState.asStateFlow()

    init {
        observeBackupWork()
    }

    fun connect() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isAuthorizing = true, errorMessage = null)
            try {
                when (val outcome = driveAuthManager.authorize()) {
                    is DriveAuthManager.AuthorizeOutcome.Authorized -> {
                        _uiState.value = _uiState.value.copy(isAuthorizing = false, isAuthorized = true)
                    }
                    is DriveAuthManager.AuthorizeOutcome.NeedsConsent -> {
                        _uiState.value = _uiState.value.copy(isAuthorizing = false, pendingConsent = outcome.intentSender)
                    }
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isAuthorizing = false, errorMessage = e.message ?: "Could not connect to Google Drive")
            }
        }
    }

    /** Called from the screen's ActivityResultLauncher callback after launching [pendingConsent]. */
    fun onConsentResult(data: Intent?) {
        _uiState.value = _uiState.value.copy(pendingConsent = null)
        try {
            driveAuthManager.accessTokenFromConsentResult(data)
            _uiState.value = _uiState.value.copy(isAuthorized = true, errorMessage = null)
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(errorMessage = e.message ?: "Google Drive access was not granted")
        }
    }

    fun onConsentCancelled() {
        _uiState.value = _uiState.value.copy(pendingConsent = null, errorMessage = "Google Drive access was not granted")
    }

    /**
     * Watches the real WorkManager state for this destination's backups — see
     * [BackupScheduler] for why this isn't just run in this ViewModel's own scope.
     */
    private fun observeBackupWork() = viewModelScope.launch {
        backupScheduler.observe(BackupWorker.UNIQUE_ONE_TIME_PREFIX + BackupWorker.DESTINATION_DRIVE).collect { workInfo ->
            val state = workInfo.toBackupWorkState()
            _uiState.value = _uiState.value.copy(
                backupRunning = state.isRunning,
                backupProgress = state.progress ?: _uiState.value.backupProgress.takeIf { state.isRunning },
                backupSummary = state.summary ?: _uiState.value.backupSummary,
                errorMessage = state.error ?: _uiState.value.errorMessage,
            )
        }
    }

    /** Reuses the source folder from the USB setup flow, same pattern as NAS and Void Cloud. */
    fun runBackupNow() {
        viewModelScope.launch {
            val sourceUriString = backupSetupPreferences.sourceUri.first()
            val anySetEnabled = backupSetsPreferences.isEnabled(BackupSetKind.PHOTOS).first() ||
                backupSetsPreferences.isEnabled(BackupSetKind.VIDEOS).first()
            if (sourceUriString == null && !anySetEnabled) {
                _uiState.value = _uiState.value.copy(noSourceConfigured = true)
                return@launch
            }
            _uiState.value = _uiState.value.copy(
                backupRunning = true, backupSummary = null, errorMessage = null, noSourceConfigured = false,
            )
            backupScheduler.runNowTo(BackupWorker.DESTINATION_DRIVE)
        }
    }
}
