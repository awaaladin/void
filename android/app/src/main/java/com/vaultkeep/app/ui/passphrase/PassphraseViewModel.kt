package com.vaultkeep.app.ui.passphrase

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaultkeep.app.data.crypto.CryptoKeyManager
import com.vaultkeep.app.data.local.EncryptionPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PassphraseUiState(
    val isPassphraseSet: Boolean = false,
    val isUnlocked: Boolean = false,
    val encryptionEnabled: Boolean = true,
    val passphrase: String = "",
    val confirmPassphrase: String = "",
    val errorMessage: String? = null,
    val showResetConfirm: Boolean = false,
) {
    val canSubmitSetup: Boolean get() = passphrase.length >= 8 && passphrase == confirmPassphrase
}

@HiltViewModel
class PassphraseViewModel @Inject constructor(
    private val keyManager: CryptoKeyManager,
    private val encryptionPreferences: EncryptionPreferences,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        PassphraseUiState(
            isPassphraseSet = keyManager.isPassphraseSet(),
            isUnlocked = keyManager.isUnlocked(),
        ),
    )
    val uiState: StateFlow<PassphraseUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            encryptionPreferences.isEnabled.collect { enabled ->
                _uiState.value = _uiState.value.copy(encryptionEnabled = enabled)
            }
        }
    }

    fun onPassphraseChanged(value: String) = update { it.copy(passphrase = value, errorMessage = null) }
    fun onConfirmPassphraseChanged(value: String) = update { it.copy(confirmPassphrase = value, errorMessage = null) }

    private fun update(block: (PassphraseUiState) -> PassphraseUiState) {
        _uiState.value = block(_uiState.value)
    }

    /** First-time setup, or a deliberate passphrase change (re-wraps the same DEK — already-encrypted files stay readable). */
    fun setupOrChangePassphrase() {
        val state = _uiState.value
        if (!state.canSubmitSetup) {
            _uiState.value = state.copy(errorMessage = "Passphrases must match and be at least 8 characters.")
            return
        }
        keyManager.setPassphrase(state.passphrase.toCharArray())
        _uiState.value = state.copy(
            isPassphraseSet = true,
            isUnlocked = true,
            passphrase = "",
            confirmPassphrase = "",
            errorMessage = null,
        )
    }

    fun unlock() {
        val state = _uiState.value
        val ok = keyManager.unlock(state.passphrase.toCharArray())
        _uiState.value = if (ok) {
            state.copy(isUnlocked = true, passphrase = "", errorMessage = null)
        } else {
            state.copy(errorMessage = "That passphrase doesn't match. Try again.")
        }
    }

    fun lock() {
        keyManager.lock()
        _uiState.value = _uiState.value.copy(isUnlocked = false)
    }

    fun setEncryptionEnabled(enabled: Boolean) = viewModelScope.launch {
        encryptionPreferences.setEnabled(enabled)
    }

    fun requestReset() = update { it.copy(showResetConfirm = true) }
    fun cancelReset() = update { it.copy(showResetConfirm = false) }

    /** Irreversible: any file already encrypted under the old DEK becomes permanently unreadable. */
    fun confirmReset() {
        keyManager.resetAll()
        _uiState.value = PassphraseUiState(encryptionEnabled = _uiState.value.encryptionEnabled)
    }
}
