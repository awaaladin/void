package com.vaultkeep.app.ui.nassetup

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vaultkeep.app.domain.model.NasProtocol
import com.vaultkeep.app.ui.theme.VaultColorTokens
import com.vaultkeep.app.ui.theme.VaultTheme

@Composable
fun NasSetupScreen(
    viewModel: NasSetupViewModel = hiltViewModel(),
    onSaved: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.startDiscovery() }
    LaunchedEffect(uiState.saved) { if (uiState.saved) onSaved() }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(VaultTheme.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(VaultTheme.spacing.lg),
        ) {
            item {
                Text("Home NAS", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(VaultTheme.spacing.sm)) {
                    ProtocolChip("WebDAV", NasProtocol.WEBDAV, uiState.protocol, viewModel::onProtocolChanged)
                    ProtocolChip("SMB", NasProtocol.SMB, uiState.protocol, viewModel::onProtocolChanged)
                }
            }

            if (uiState.discovered.isNotEmpty()) {
                item {
                    Text("Found on your network", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
                }
                items(uiState.discovered) { host ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(VaultTheme.radii.sm))
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(VaultTheme.spacing.md),
                    ) {
                        Text("${host.name} — ${host.host}:${host.port}", color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }

            item {
                OutlinedTextField(
                    value = uiState.host,
                    onValueChange = viewModel::onHostChanged,
                    label = { Text("Host or IP") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                OutlinedTextField(
                    value = uiState.port,
                    onValueChange = viewModel::onPortChanged,
                    label = { Text("Port") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                OutlinedTextField(
                    value = uiState.shareOrPath,
                    onValueChange = viewModel::onShareOrPathChanged,
                    label = { Text(if (uiState.protocol == NasProtocol.SMB) "Share name" else "WebDAV path") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                OutlinedTextField(
                    value = uiState.username,
                    onValueChange = viewModel::onUsernameChanged,
                    label = { Text("Username") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                OutlinedTextField(
                    value = uiState.password,
                    onValueChange = viewModel::onPasswordChanged,
                    label = { Text("Password") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (uiState.protocol == NasProtocol.WEBDAV) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("Use HTTPS", color = MaterialTheme.colorScheme.onBackground)
                        Switch(checked = uiState.useHttps, onCheckedChange = viewModel::onUseHttpsChanged)
                    }
                }
            }

            item {
                Button(onClick = viewModel::testConnection, enabled = uiState.canSave, modifier = Modifier.fillMaxWidth()) {
                    Text("Test connection")
                }
            }

            item {
                when (uiState.testResult) {
                    TestConnectionResult.TESTING -> CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    TestConnectionResult.SUCCESS -> Text("Connected", color = VaultColorTokens.Verdigris500)
                    TestConnectionResult.FAILURE -> Text(
                        uiState.testError ?: "Connection failed",
                        color = MaterialTheme.colorScheme.error,
                    )
                    TestConnectionResult.IDLE -> {}
                }
            }

            item {
                Button(onClick = viewModel::save, enabled = uiState.canSave, modifier = Modifier.fillMaxWidth()) {
                    Text("Save")
                }
            }

            item {
                Button(
                    onClick = viewModel::runBackupNow,
                    enabled = uiState.canSave && !uiState.backupRunning,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (uiState.backupRunning) "Backing up…" else "Back up now to this NAS")
                }
            }

            if (uiState.noSourceConfigured) {
                item {
                    Text(
                        "Pick a source folder in \"Set up USB/SSD backup\" first — NAS backup reuses that same source.",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            if (uiState.backupRunning) {
                item {
                    val progress = uiState.backupProgress
                    Column(verticalArrangement = Arrangement.spacedBy(VaultTheme.spacing.sm)) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        if (progress != null) {
                            Text(
                                "${progress.filesDone}/${progress.totalFiles} — ${progress.currentFile}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            uiState.backupSummary?.let { summary ->
                item {
                    Text(
                        "Copied ${summary.filesCopied}, skipped ${summary.filesSkipped}, failed ${summary.filesFailed} — ${summary.bytesCopied} bytes",
                        color = VaultColorTokens.Verdigris500,
                    )
                }
            }

            uiState.backupError?.let { message ->
                item {
                    Text(message, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun ProtocolChip(label: String, value: NasProtocol, current: NasProtocol, onSelect: (NasProtocol) -> Unit) {
    val selected = value == current
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(VaultTheme.radii.pill))
            .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
            .clickable { onSelect(value) }
            .padding(horizontal = VaultTheme.spacing.lg, vertical = VaultTheme.spacing.sm),
    ) {
        Text(
            label,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        )
    }
}
