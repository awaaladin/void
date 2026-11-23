package com.vaultkeep.app.ui.cloudauth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaultkeep.app.data.local.BackupSetsPreferences
import com.vaultkeep.app.data.local.BackupSetupPreferences
import com.vaultkeep.app.data.local.CloudAuthPreferences
import com.vaultkeep.app.data.remote.LoginRequest
import com.vaultkeep.app.data.remote.RegisterRequest
import com.vaultkeep.app.data.remote.VoidApi
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

data class CloudAuthUiState(
    val signedIn: Boolean = false,
    val username: String = "",
    val email: String = "",
    val password: String = "",
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
    val usedBytes: Long? = null,
    val quotaBytes: Long? = null,
    val backupRunning: Boolean = false,
    val backupProgress: BackupProgressEvent? = null,
    val backupSummary: BackupRunSummary? = null,
    val backupError: String? = null,
    val noSourceConfigured: Boolean = false,
)

@HiltViewModel
class CloudAuthViewModel @Inject constructor(
    private val api: VoidApi,
    private val authPreferences: CloudAuthPreferences,
    private val backupSetupPreferences: BackupSetupPreferences,
    private val backupSetsPreferences: BackupSetsPreferences,
    private val backupScheduler: BackupScheduler,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CloudAuthUiState(signedIn = authPreferences.isSignedIn()))
    val uiState: StateFlow<CloudAuthUiState> = _uiState.asStateFlow()

    private var lastUsageRefreshAt = 0

    init {
        if (authPreferences.isSignedIn()) {
            refreshUsage()
            observeBackupWork()
        }
    }

    fun onUsernameChanged(value: String) = update { it.copy(username = value) }
    fun onEmailChanged(value: String) = update { it.copy(email = value) }
    fun onPasswordChanged(value: String) = update { it.copy(password = value) }

    private fun update(block: (CloudAuthUiState) -> CloudAuthUiState) {
        _uiState.value = block(_uiState.value)
    }

    fun register() {
        val state = _uiState.value
        _uiState.value = state.copy(isSubmitting = true, errorMessage = null)
        viewModelScope.launch {
            try {
                val response = api.register(RegisterRequest(state.username, state.email, state.password))
                if (!response.isSuccessful) {
                    _uiState.value = _uiState.value.copy(isSubmitting = false, errorMessage = "Registration failed: HTTP ${response.code()}")
                    return@launch
                }
                val tokens = response.body() ?: return@launch
                authPreferences.saveTokens(tokens.access, tokens.refresh)
                _uiState.value = _uiState.value.copy(isSubmitting = false, signedIn = true)
                refreshUsage()
                observeBackupWork()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isSubmitting = false, errorMessage = e.message ?: "Registration failed")
            }
        }
    }

    fun login() {
        val state = _uiState.value
        _uiState.value = state.copy(isSubmitting = true, errorMessage = null)
        viewModelScope.launch {
            try {
                val response = api.login(LoginRequest(state.username, state.password))
                if (!response.isSuccessful) {
                    _uiState.value = _uiState.value.copy(isSubmitting = false, errorMessage = "Sign-in failed: check your username and password")
                    return@launch
                }
                val tokens = response.body() ?: return@launch
                authPreferences.saveTokens(tokens.access, tokens.refresh)
                _uiState.value = _uiState.value.copy(isSubmitting = false, signedIn = true)
                refreshUsage()
                observeBackupWork()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isSubmitting = false, errorMessage = e.message ?: "Sign-in failed")
            }
        }
    }

    fun signOut() {
        authPreferences.clear()
        _uiState.value = CloudAuthUiState(signedIn = false)
    }

    private fun refreshUsage() = viewModelScope.launch {
        val response = runCatching { api.usage() }.getOrNull()
        if (response != null && response.isSuccessful) {
            val usage = response.body() ?: return@launch
            _uiState.value = _uiState.value.copy(usedBytes = usage.usedBytes, quotaBytes = usage.quotaBytes)
        }
    }

    /**
     * Watches the real WorkManager state for this destination's backups. Enqueued as actual
     * background work (see [BackupScheduler]) rather than run directly in this ViewModel's
     * coroutine scope, so it keeps going if this screen closes, the app backgrounds, or the
     * phone locks — none of which should silently cut off a multi-gigabyte upload.
     */
    private fun observeBackupWork() = viewModelScope.launch {
        backupScheduler.observe(BackupWorker.UNIQUE_ONE_TIME_PREFIX + BackupWorker.DESTINATION_CLOUD).collect { workInfo ->
            val state = workInfo.toBackupWorkState()
            _uiState.value = _uiState.value.copy(
                backupRunning = state.isRunning,
                backupProgress = state.progress ?: _uiState.value.backupProgress.takeIf { state.isRunning },
                backupSummary = state.summary ?: _uiState.value.backupSummary,
                backupError = state.error,
            )
            // A big backup can take a long time — refresh the usage readout periodically
            // so it doesn't look frozen/stuck while it runs, and once more when it finishes.
            val filesDone = state.progress?.filesDone
            if (filesDone != null && filesDone - lastUsageRefreshAt >= 20) {
                lastUsageRefreshAt = filesDone
                refreshUsage()
            } else if (state.summary != null) {
                refreshUsage()
            }
        }
    }

    /** Backs up the manually-picked source folder (shared with USB/NAS setup) plus whichever
     * of Photos/Videos are enabled on Home, all to Void Cloud. */
    fun runBackupNow() {
        viewModelScope.launch {
            val sourceUriString = backupSetupPreferences.sourceUri.first()
            val anySetEnabled = backupSetsPreferences.isEnabled(BackupSetKind.PHOTOS).first() ||
                backupSetsPreferences.isEnabled(BackupSetKind.VIDEOS).first()
            if (sourceUriString == null && !anySetEnabled) {
                _uiState.value = _uiState.value.copy(noSourceConfigured = true)
                return@launch
            }
            _uiState.value = _uiState.value.copy(backupRunning = true, backupSummary = null, backupError = null, noSourceConfigured = false)
            lastUsageRefreshAt = 0
            backupScheduler.runNowTo(BackupWorker.DESTINATION_CLOUD)
        }
    }
}
