package com.vaultkeep.app.ui.drivesetup

import android.content.Intent
import android.content.IntentSender
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaultkeep.app.data.auth.DriveAuthManager
import com.vaultkeep.app.data.crypto.DestinationEncryptor
import com.vaultkeep.app.data.destination.GoogleDriveDestination
import com.vaultkeep.app.data.local.BackupSetsPreferences
import com.vaultkeep.app.data.local.BackupSetupPreferences
import com.vaultkeep.app.data.remote.DriveApi
import com.vaultkeep.app.di.DriveHttpClient
import com.vaultkeep.app.domain.model.BackupSetKind
import com.vaultkeep.app.domain.usecase.BackupProgressEvent
import com.vaultkeep.app.domain.usecase.BackupRunSummary
import com.vaultkeep.app.domain.usecase.RunAllEnabledBackupsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
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
    private val driveApi: DriveApi,
    @DriveHttpClient private val driveHttpClient: OkHttpClient,
    private val backupSetupPreferences: BackupSetupPreferences,
    private val backupSetsPreferences: BackupSetsPreferences,
    private val runAllEnabledBackupsUseCase: RunAllEnabledBackupsUseCase,
    private val destinationEncryptor: DestinationEncryptor,
) : ViewModel() {

    private val _uiState = MutableStateFlow(GoogleDriveSetupUiState())
    val uiState: StateFlow<GoogleDriveSetupUiState> = _uiState.asStateFlow()

    // Deliberately in-memory only, for this process's lifetime — see DriveAuthManager
    // for why there's no refresh token to persist.
    private var cachedAccessToken: String? = null

    fun connect() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isAuthorizing = true, errorMessage = null)
            try {
                when (val outcome = driveAuthManager.authorize()) {
                    is DriveAuthManager.AuthorizeOutcome.Authorized -> {
                        cachedAccessToken = outcome.accessToken
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
            cachedAccessToken = driveAuthManager.accessTokenFromConsentResult(data)
            _uiState.value = _uiState.value.copy(isAuthorized = true, errorMessage = null)
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(errorMessage = e.message ?: "Google Drive access was not granted")
        }
    }

    fun onConsentCancelled() {
        _uiState.value = _uiState.value.copy(pendingConsent = null, errorMessage = "Google Drive access was not granted")
    }

    /** Reuses the source folder from the USB setup flow, same pattern as NAS and Void Cloud. */
    fun runBackupNow() {
        val token = cachedAccessToken
        if (token == null) {
            _uiState.value = _uiState.value.copy(errorMessage = "Connect Google Drive first")
            return
        }
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
            val destination = destinationEncryptor.wrap(GoogleDriveDestination(driveApi, driveHttpClient, token))
            try {
                val summary = runAllEnabledBackupsUseCase.execute(
                    sourceTreeUri = sourceUriString?.let(Uri::parse),
                    destination = destination,
                    onProgress = { event -> _uiState.value = _uiState.value.copy(backupProgress = event) },
                )
                _uiState.value = _uiState.value.copy(backupRunning = false, backupSummary = summary)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(backupRunning = false, errorMessage = e.message ?: "Drive backup failed")
            }
        }
    }
}
