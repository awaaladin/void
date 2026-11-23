package com.vaultkeep.app.ui.cloudauth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vaultkeep.app.ui.components.ScreenHeader
import com.vaultkeep.app.ui.components.StaggeredEntrance
import com.vaultkeep.app.ui.components.VoidButton
import com.vaultkeep.app.ui.components.VoidSecondaryButton
import com.vaultkeep.app.ui.theme.VaultColorTokens
import com.vaultkeep.app.ui.theme.VaultTheme

/**
 * Void Cloud sign-in/register + usage + manual "back up now." Username/
 * password for now — Google sign-in lands in Phase 8 alongside the rest of
 * the public-account system.
 */
@Composable
fun CloudAuthScreen(viewModel: CloudAuthViewModel = hiltViewModel(), onBack: () -> Unit = {}) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        if (uiState.signedIn) {
            SignedInContent(uiState, viewModel, padding, onBack)
        } else {
            SignedOutContent(uiState, viewModel, padding, onBack)
        }
    }
}

@Composable
private fun SignedOutContent(uiState: CloudAuthUiState, viewModel: CloudAuthViewModel, padding: androidx.compose.foundation.layout.PaddingValues, onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(VaultTheme.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(VaultTheme.spacing.lg),
    ) {
        ScreenHeader(
            icon = Icons.Outlined.Cloud,
            tint = VaultColorTokens.Signal500,
            title = "Void Cloud",
            subtitle = "Our own backend, backed by S3-compatible object storage — no practical cap on how much you can keep here. Sign in or create an account to use it as a backup destination.",
            onBack = onBack,
        )

        OutlinedTextField(
            value = uiState.username,
            onValueChange = viewModel::onUsernameChanged,
            label = { Text("Username") },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = uiState.email,
            onValueChange = viewModel::onEmailChanged,
            label = { Text("Email (for new accounts)") },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = uiState.password,
            onValueChange = viewModel::onPasswordChanged,
            label = { Text("Password") },
            modifier = Modifier.fillMaxWidth(),
        )

        VoidButton(text = "Sign in", onClick = viewModel::login, loading = uiState.isSubmitting, modifier = Modifier.fillMaxWidth())
        VoidSecondaryButton(text = "Create account", onClick = viewModel::register, enabled = !uiState.isSubmitting, modifier = Modifier.fillMaxWidth())

        uiState.errorMessage?.let { message ->
            Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun SignedInContent(uiState: CloudAuthUiState, viewModel: CloudAuthViewModel, padding: androidx.compose.foundation.layout.PaddingValues, onBack: () -> Unit) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(VaultTheme.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(VaultTheme.spacing.lg),
    ) {
        item {
            StaggeredEntrance(index = 0) {
                ScreenHeader(icon = Icons.Outlined.Cloud, tint = VaultColorTokens.Signal500, title = "Void Cloud", onBack = onBack)
            }
        }

        item {
            val used = uiState.usedBytes
            val quota = uiState.quotaBytes
            if (used != null && quota != null && quota > 0) {
                val fraction = (used.toFloat() / quota.toFloat()).coerceIn(0f, 1f)
                val barColor = when {
                    fraction >= 1f -> MaterialTheme.colorScheme.error
                    fraction >= 0.9f -> VaultColorTokens.Ember500
                    else -> VaultColorTokens.Signal500
                }
                Column(verticalArrangement = Arrangement.spacedBy(VaultTheme.spacing.xs)) {
                    Text(
                        "${formatBytes(used)} of ${formatBytes(quota)} used",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    LinearProgressIndicator(
                        progress = { fraction },
                        color = barColor,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (fraction >= 0.9f) {
                        Text(
                            if (fraction >= 1f) "You're over quota — new uploads will be rejected until you free up space."
                            else "Getting close to your quota.",
                            style = MaterialTheme.typography.bodySmall,
                            color = barColor,
                        )
                    }
                }
            }
        }

        item {
            VoidButton(
                text = "Back up now to Void Cloud",
                onClick = viewModel::runBackupNow,
                enabled = !uiState.backupRunning,
                loading = uiState.backupRunning,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (uiState.noSourceConfigured) {
            item {
                Text(
                    "Pick a source folder in \"Set up USB/SSD backup\" first — cloud backup reuses that same source.",
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
            item { Text(message, color = MaterialTheme.colorScheme.error) }
        }

        item {
            VoidSecondaryButton(text = "Sign out", onClick = viewModel::signOut, modifier = Modifier.fillMaxWidth())
        }
    }
}

private fun formatBytes(bytes: Long): String {
    val gb = bytes / 1_000_000_000.0
    if (gb >= 1.0) return "%.2f GB".format(gb)
    val mb = bytes / 1_000_000.0
    return "%.1f MB".format(mb)
}
