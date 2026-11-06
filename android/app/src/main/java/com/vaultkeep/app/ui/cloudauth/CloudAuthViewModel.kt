package com.vaultkeep.app.ui.cloudauth

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaultkeep.app.data.destination.CloudDestination
import com.vaultkeep.app.data.local.BackupSetupPreferences
import com.vaultkeep.app.data.local.CloudAuthPreferences
import com.vaultkeep.app.data.remote.LoginRequest
import com.vaultkeep.app.data.remote.RegisterRequest
import com.vaultkeep.app.data.remote.VoidApi
import com.vaultkeep.app.di.UploadHttpClient
import com.vaultkeep.app.domain.model.BackupSetKind
import com.vaultkeep.app.domain.usecase.BackupProgressEvent
import com.vaultkeep.app.domain.usecase.BackupRunSummary
import com.vaultkeep.app.domain.usecase.RunUsbBackupUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
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
    private val runBackupUseCase: RunUsbBackupUseCase,
    @UploadHttpClient private val uploadHttpClient: OkHttpClient,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CloudAuthUiState(signedIn = authPreferences.isSignedIn()))
    val uiState: StateFlow<CloudAuthUiState> = _uiState.asStateFlow()

    init {
        if (authPreferences.isSignedIn()) refreshUsage()
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

    /** Reuses the same source folder from the USB setup flow, same pattern as NAS. */
    fun runBackupNow() {
        viewModelScope.launch {
            val sourceUriString = backupSetupPreferences.sourceUri.first()
            if (sourceUriString == null) {
                _uiState.value = _uiState.value.copy(noSourceConfigured = true)
                return@launch
            }
            _uiState.value = _uiState.value.copy(backupRunning = true, backupSummary = null, backupError = null, noSourceConfigured = false)
            val destination = CloudDestination(api = api, uploadClient = uploadHttpClient)
            try {
                val summary = runBackupUseCase.execute(
                    sourceTreeUri = Uri.parse(sourceUriString),
                    destination = destination,
                    setType = BackupSetKind.CUSTOM,
                    onProgress = { event -> _uiState.value = _uiState.value.copy(backupProgress = event) },
                )
                _uiState.value = _uiState.value.copy(backupRunning = false, backupSummary = summary)
                refreshUsage()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(backupRunning = false, backupError = e.message ?: "Cloud backup failed")
            }
        }
    }
}
