package com.vaultkeep.app.ui.nassetup

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaultkeep.app.data.destination.SmbDestination
import com.vaultkeep.app.data.destination.WebDavDestination
import com.vaultkeep.app.data.local.BackupSetupPreferences
import com.vaultkeep.app.data.local.NasCredentialsStore
import com.vaultkeep.app.data.local.NasDiscovery
import com.vaultkeep.app.domain.BackupDestination
import com.vaultkeep.app.domain.model.BackupSetKind
import com.vaultkeep.app.domain.model.DiscoveredNasHost
import com.vaultkeep.app.domain.model.NasConfig
import com.vaultkeep.app.domain.model.NasProtocol
import com.vaultkeep.app.domain.usecase.BackupProgressEvent
import com.vaultkeep.app.domain.usecase.BackupRunSummary
import com.vaultkeep.app.domain.usecase.RunUsbBackupUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class TestConnectionResult { IDLE, TESTING, SUCCESS, FAILURE }

data class NasSetupUiState(
    val protocol: NasProtocol = NasProtocol.WEBDAV,
    val host: String = "",
    val port: String = "",
    val shareOrPath: String = "",
    val username: String = "",
    val password: String = "",
    val useHttps: Boolean = false,
    val discovered: List<DiscoveredNasHost> = emptyList(),
    val testResult: TestConnectionResult = TestConnectionResult.IDLE,
    val testError: String? = null,
    val saved: Boolean = false,
    val backupRunning: Boolean = false,
    val backupProgress: BackupProgressEvent? = null,
    val backupSummary: BackupRunSummary? = null,
    val backupError: String? = null,
    val noSourceConfigured: Boolean = false,
) {
    val canSave: Boolean get() = host.isNotBlank() && port.toIntOrNull() != null
}

@HiltViewModel
class NasSetupViewModel @Inject constructor(
    private val credentialsStore: NasCredentialsStore,
    private val discovery: NasDiscovery,
    private val backupSetupPreferences: BackupSetupPreferences,
    private val runBackupUseCase: RunUsbBackupUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(loadInitialState())
    val uiState: StateFlow<NasSetupUiState> = _uiState.asStateFlow()

    private fun loadInitialState(): NasSetupUiState {
        val existing = credentialsStore.load() ?: return NasSetupUiState()
        return NasSetupUiState(
            protocol = existing.protocol,
            host = existing.host,
            port = existing.port.toString(),
            shareOrPath = existing.shareOrPath,
            username = existing.username,
            password = existing.password,
            useHttps = existing.useHttps,
        )
    }

    fun startDiscovery() {
        viewModelScope.launch {
            discovery.discover().collect { host ->
                val current = _uiState.value.discovered
                if (current.none { it.host == host.host && it.port == host.port }) {
                    _uiState.value = _uiState.value.copy(discovered = current + host)
                }
            }
        }
    }

    fun onDiscoveredHostSelected(host: DiscoveredNasHost) {
        _uiState.value = _uiState.value.copy(
            protocol = host.protocol,
            host = host.host,
            port = host.port.toString(),
        )
    }

    fun onProtocolChanged(protocol: NasProtocol) {
        _uiState.value = _uiState.value.copy(
            protocol = protocol,
            port = if (_uiState.value.port.isBlank()) defaultPortFor(protocol).toString() else _uiState.value.port,
        )
    }

    fun onHostChanged(value: String) = update { it.copy(host = value) }
    fun onPortChanged(value: String) = update { it.copy(port = value) }
    fun onShareOrPathChanged(value: String) = update { it.copy(shareOrPath = value) }
    fun onUsernameChanged(value: String) = update { it.copy(username = value) }
    fun onPasswordChanged(value: String) = update { it.copy(password = value) }
    fun onUseHttpsChanged(value: Boolean) = update { it.copy(useHttps = value) }

    private fun update(block: (NasSetupUiState) -> NasSetupUiState) {
        _uiState.value = block(_uiState.value)
    }

    private fun buildConfig(): NasConfig? {
        val state = _uiState.value
        val port = state.port.toIntOrNull() ?: return null
        return NasConfig(
            protocol = state.protocol,
            host = state.host,
            port = port,
            shareOrPath = state.shareOrPath,
            username = state.username,
            password = state.password,
            useHttps = state.useHttps,
        )
    }

    private fun destinationFor(config: NasConfig): BackupDestination = when (config.protocol) {
        NasProtocol.SMB -> SmbDestination(config, id = "smb:${config.host}", displayName = config.displayName())
        NasProtocol.WEBDAV -> WebDavDestination(config, id = "webdav:${config.host}", displayName = config.displayName())
    }

    fun testConnection() {
        val config = buildConfig() ?: return
        _uiState.value = _uiState.value.copy(testResult = TestConnectionResult.TESTING, testError = null)
        viewModelScope.launch {
            val destination = destinationFor(config)
            try {
                destination.testConnection()
                _uiState.value = _uiState.value.copy(testResult = TestConnectionResult.SUCCESS)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    testResult = TestConnectionResult.FAILURE,
                    testError = e.message ?: "Connection failed",
                )
            }
        }
    }

    fun save() {
        val config = buildConfig() ?: return
        credentialsStore.save(config)
        _uiState.value = _uiState.value.copy(saved = true)
    }

    /**
     * Reuses the source folder already picked in the USB setup flow
     * (Phase 2) and runs the exact same incremental-backup use case against
     * this NAS destination instead. This is a manual trigger only —
     * scheduled/background NAS runs aren't wired into BackupWorker yet.
     */
    fun runBackupNow() {
        val config = buildConfig() ?: return
        viewModelScope.launch {
            val sourceUriString = backupSetupPreferences.sourceUri.first()
            if (sourceUriString == null) {
                _uiState.value = _uiState.value.copy(noSourceConfigured = true)
                return@launch
            }
            _uiState.value = _uiState.value.copy(
                backupRunning = true,
                backupSummary = null,
                backupError = null,
                noSourceConfigured = false,
            )
            val destination = destinationFor(config)
            try {
                val summary = runBackupUseCase.execute(
                    sourceTreeUri = Uri.parse(sourceUriString),
                    destination = destination,
                    setType = BackupSetKind.CUSTOM,
                    onProgress = { event -> _uiState.value = _uiState.value.copy(backupProgress = event) },
                )
                _uiState.value = _uiState.value.copy(backupRunning = false, backupSummary = summary)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(backupRunning = false, backupError = e.message ?: "Backup failed")
            }
        }
    }

    private fun defaultPortFor(protocol: NasProtocol) = when (protocol) {
        NasProtocol.SMB -> 445
        NasProtocol.WEBDAV -> 443
    }
}
