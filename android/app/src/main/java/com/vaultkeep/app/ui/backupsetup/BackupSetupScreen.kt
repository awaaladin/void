package com.vaultkeep.app.ui.backupsetup

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Usb
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vaultkeep.app.ui.components.ScreenHeader
import com.vaultkeep.app.ui.components.StaggeredEntrance
import com.vaultkeep.app.ui.components.VoidButton
import com.vaultkeep.app.ui.components.VoidSecondaryButton
import com.vaultkeep.app.ui.theme.VaultColorTokens
import com.vaultkeep.app.ui.theme.VaultTheme

/**
 * Phase 2 screen: pick a source folder and a USB/SSD destination folder via
 * SAF, then run an incremental backup between them. Replaces nothing from
 * Phase 1 — reachable from Home as a new destination's "set up" action.
 */
@Composable
fun BackupSetupScreen(
    viewModel: BackupSetupViewModel = hiltViewModel(),
    onOpenScheduling: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val sourcePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
            viewModel.onSourcePicked(uri)
        }
    }

    val destinationPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
            viewModel.onDestinationPicked(uri)
        }
    }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(VaultTheme.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(VaultTheme.spacing.lg),
        ) {
            item {
                StaggeredEntrance(index = 0) {
                    ScreenHeader(
                        icon = Icons.Outlined.Usb,
                        tint = VaultColorTokens.Signal500,
                        title = "Back up to USB/SSD",
                        subtitle = "Two taps: pick what to back up, then pick where to put it. Each \"Choose folder\" button opens Android's own folder picker — a full-screen system screen, not part of Void — so don't worry if it looks different from the rest of the app.",
                    )
                }
            }

            item {
                StaggeredEntrance(index = 1) {
                    PickerRow(
                        stepIcon = Icons.Outlined.CreateNewFolder,
                        label = "1. Source folder — what to back up",
                        description = "The folder on your phone you want copied, e.g. DCIM/Camera for photos, or Download. In the picker that opens, tap through to that folder, then tap \"Use this folder\" at the bottom.",
                        value = uiState.sourceUri,
                        onPick = { sourcePicker.launch(null) },
                    )
                }
            }
            item {
                StaggeredEntrance(index = 2) {
                    PickerRow(
                        stepIcon = Icons.Outlined.Usb,
                        label = "2. Destination — where it goes",
                        description = "Make sure your USB/SSD drive is plugged in first. In the picker, it should appear as a storage option on the left (often named after the drive) — tap it, pick or create a folder inside it, then tap \"Use this folder.\"",
                        value = uiState.destinationUri,
                        onPick = { destinationPicker.launch(null) },
                    )
                }
            }

            item {
                StaggeredEntrance(index = 3) {
                    VoidButton(
                        text = "Start backup",
                        onClick = viewModel::startBackup,
                        enabled = uiState.canStart,
                        loading = uiState.isRunning,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            item {
                VoidSecondaryButton(text = "Scheduling & battery guide", onClick = onOpenScheduling, modifier = Modifier.fillMaxWidth())
            }

            if (uiState.isRunning) {
                item {
                    val progress = uiState.progress
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

            uiState.summary?.let { summary ->
                item {
                    Text(
                        "Copied ${summary.filesCopied}, skipped ${summary.filesSkipped}, failed ${summary.filesFailed} — ${summary.bytesCopied} bytes",
                        style = MaterialTheme.typography.bodyMedium,
                        color = VaultColorTokens.Verdigris500,
                    )
                }
            }

            uiState.errorMessage?.let { message ->
                item {
                    Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun PickerRow(
    stepIcon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    description: String,
    value: String?,
    onPick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(VaultTheme.radii.md))
            .background(MaterialTheme.colorScheme.surface)
            .padding(VaultTheme.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(VaultTheme.spacing.xs),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(VaultTheme.spacing.sm)) {
            val accent = if (value != null) VaultColorTokens.Verdigris500 else VaultColorTokens.Signal500
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(imageVector = stepIcon, contentDescription = null, tint = accent, modifier = Modifier.size(16.dp))
            }
            Text(label, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        }
        Text(
            description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value?.let { "✓ Selected" } ?: "Not selected yet",
            style = MaterialTheme.typography.labelLarge,
            color = if (value != null) VaultColorTokens.Verdigris500 else MaterialTheme.colorScheme.error,
        )
        VoidSecondaryButton(text = if (value == null) "Choose folder" else "Change folder", onClick = onPick)
    }
}
