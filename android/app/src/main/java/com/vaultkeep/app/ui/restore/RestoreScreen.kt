package com.vaultkeep.app.ui.restore

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vaultkeep.app.ui.components.ScreenHeader
import com.vaultkeep.app.ui.components.StaggeredEntrance
import com.vaultkeep.app.ui.components.VoidButton
import com.vaultkeep.app.ui.components.VoidErrorDialog
import com.vaultkeep.app.ui.components.VoidSecondaryButton
import com.vaultkeep.app.ui.theme.VaultColorTokens
import com.vaultkeep.app.ui.theme.VaultTheme

private fun formatBytes(bytes: Long): String {
    val units = listOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unitIndex = 0
    while (value >= 1024 && unitIndex < units.lastIndex) {
        value /= 1024
        unitIndex++
    }
    return "%.1f %s".format(value, units[unitIndex])
}

/**
 * Phase 7: pick a destination Void has already verified files on, pick which
 * files to bring back, pick where on this phone they should land, and run
 * the restore — the mirror of Phase 2-5's backup setup screens.
 */
@Composable
fun RestoreScreen(viewModel: RestoreViewModel = hiltViewModel(), onBack: () -> Unit = {}) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val targetPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
            viewModel.onTargetPicked(uri)
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
                ScreenHeader(icon = Icons.Outlined.History, tint = VaultColorTokens.Signal500, title = "Restore", onBack = onBack)
            }

            if (uiState.destinations.isEmpty()) {
                Text(
                    "No verified backups found yet on this device's index. Back something up first — once a backup shows VERIFIED, it shows up here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@Column
            }

            Text(
                "1. Where to restore from",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            uiState.destinations.forEachIndexed { index, destination ->
                StaggeredEntrance(index = index + 1) {
                    DestinationRow(
                        label = destination.label,
                        fileCount = destination.fileCount,
                        totalBytes = destination.totalBytes,
                        selected = destination.id == uiState.selectedDestinationId,
                        onClick = { viewModel.selectDestination(destination.id) },
                    )
                }
            }

            if (uiState.selectedDestinationId != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "2. Which files (${uiState.selectedFileIds.size}/${uiState.files.size} selected)",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    VoidSecondaryButton(text = "Toggle all", onClick = viewModel::toggleSelectAll)
                }

                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(VaultTheme.spacing.xs),
                ) {
                    items(uiState.files, key = { it.fileId }) { file ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(VaultTheme.radii.sm))
                                .background(MaterialTheme.colorScheme.surface)
                                .padding(horizontal = VaultTheme.spacing.sm),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = file.fileId in uiState.selectedFileIds,
                                onCheckedChange = { viewModel.toggleFile(file.fileId) },
                            )
                            Column(modifier = Modifier.padding(vertical = VaultTheme.spacing.xs)) {
                                Text(file.relativePath, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                                Text(
                                    formatBytes(file.sizeBytes) + if (file.encrypted) " · encrypted" else "",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }

                Text(
                    "3. Where it goes on this phone",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                VoidSecondaryButton(
                    text = if (uiState.targetUri == null) "Choose folder" else "Change folder (✓ selected)",
                    onClick = { targetPicker.launch(null) },
                    modifier = Modifier.fillMaxWidth(),
                )

                VoidButton(
                    text = "Restore selected files",
                    onClick = viewModel::startRestore,
                    enabled = uiState.canStart,
                    loading = uiState.isRunning,
                    modifier = Modifier.fillMaxWidth(),
                )

                if (uiState.isRunning) {
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

                uiState.summary?.let { summary ->
                    Text(
                        "Restored ${summary.filesRestored}, failed ${summary.filesFailed} — ${formatBytes(summary.bytesRestored)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = VaultColorTokens.Verdigris500,
                    )
                }

            }
        }
        VoidErrorDialog(uiState.errorMessage ?: uiState.summary?.takeIf { it.filesFailed > 0 }?.firstError)
    }
}

@Composable
private fun DestinationRow(label: String, fileCount: Int, totalBytes: Long, selected: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(VaultTheme.radii.md))
            .background(if (selected) VaultColorTokens.SignalSurface else MaterialTheme.colorScheme.surface)
            .padding(VaultTheme.spacing.md),
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        Text(
            "$fileCount file${if (fileCount == 1) "" else "s"} · ${formatBytes(totalBytes)}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        VoidSecondaryButton(text = if (selected) "Selected" else "Select", onClick = onClick, modifier = Modifier.fillMaxWidth())
    }
}
