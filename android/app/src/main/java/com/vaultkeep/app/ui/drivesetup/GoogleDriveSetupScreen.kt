package com.vaultkeep.app.ui.drivesetup

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vaultkeep.app.ui.components.ScreenHeader
import com.vaultkeep.app.ui.components.StaggeredEntrance
import com.vaultkeep.app.ui.components.VoidButton
import com.vaultkeep.app.ui.theme.VaultColorTokens
import com.vaultkeep.app.ui.theme.VaultTheme

/**
 * Phase 5 (Drive): connect via Google's Identity Services Authorization API
 * (not the old Google Sign-In flow), scoped to `drive.file` only — Void
 * never sees anything else in the user's Drive. Backup itself reuses the
 * same source folder and the same incremental-backup use case as every
 * other destination.
 */
@Composable
fun GoogleDriveSetupScreen(viewModel: GoogleDriveSetupViewModel = hiltViewModel(), onBack: () -> Unit = {}) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val consentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            viewModel.onConsentResult(result.data)
        } else {
            viewModel.onConsentCancelled()
        }
    }

    LaunchedEffect(uiState.pendingConsent) {
        uiState.pendingConsent?.let { intentSender ->
            consentLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
        }
    }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(VaultTheme.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(VaultTheme.spacing.lg),
        ) {
            StaggeredEntrance(index = 0) {
                ScreenHeader(
                    icon = Icons.Outlined.Folder,
                    tint = VaultColorTokens.Signal500,
                    title = "Google Drive",
                    subtitle = "Backs up into a \"Void Backups\" folder in your own Drive. Void only ever sees files and folders it creates there — nothing else in your Drive.",
                    onBack = onBack,
                )
            }

            if (!uiState.isAuthorized) {
                VoidButton(
                    text = "Connect Google Drive",
                    onClick = viewModel::connect,
                    loading = uiState.isAuthorizing,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Text("Connected", color = VaultColorTokens.Verdigris500, style = MaterialTheme.typography.titleMedium)

                VoidButton(
                    text = "Back up now to Google Drive",
                    onClick = viewModel::runBackupNow,
                    enabled = !uiState.backupRunning,
                    loading = uiState.backupRunning,
                    modifier = Modifier.fillMaxWidth(),
                )

                if (uiState.noSourceConfigured) {
                    Text(
                        "Pick a source folder in \"Set up USB/SSD backup\" first — Drive backup reuses that same source.",
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                if (uiState.backupRunning) {
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

                uiState.backupSummary?.let { summary ->
                    Text(
                        "Copied ${summary.filesCopied}, skipped ${summary.filesSkipped}, failed ${summary.filesFailed} — ${summary.bytesCopied} bytes",
                        color = VaultColorTokens.Verdigris500,
                    )
                }
            }

            uiState.errorMessage?.let { message ->
                Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
