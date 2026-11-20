package com.vaultkeep.app.ui.insights

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vaultkeep.app.domain.model.destinationLabel
import com.vaultkeep.app.ui.components.VoidButton
import com.vaultkeep.app.ui.theme.VaultColorTokens
import com.vaultkeep.app.ui.theme.VaultTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Phase 7: what's actually backed up, where, and how much of it — plus a plain-text report export. */
@Composable
fun StorageInsightsScreen(viewModel: StorageInsightsViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy 'at' h:mm a", Locale.getDefault()) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) viewModel.exportReport(uri)
    }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(VaultTheme.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(VaultTheme.spacing.lg),
        ) {
            Text("Storage insights", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)

            SummaryCard(
                title = "Everything you've backed up",
                lines = listOf(
                    "${uiState.overall.fileCount} file(s), ${formatBytes(uiState.overall.totalBytes)}",
                    "Last backup: " + (uiState.overall.lastBackupAt?.let { dateFormat.format(Date(it)) } ?: "never"),
                ),
            )

            Text("By destination", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            if (uiState.byDestination.isEmpty()) {
                Text("Nothing backed up yet.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            uiState.byDestination.forEach { row ->
                SummaryCard(
                    title = destinationLabel(row.destinationId),
                    lines = listOf(
                        "${row.fileCount} file(s), ${formatBytes(row.totalBytes)}",
                        "Last backup: ${dateFormat.format(Date(row.lastBackupAt))}",
                    ),
                )
            }

            Text("By backup set", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            if (uiState.bySetKind.isEmpty()) {
                Text("Nothing backed up yet.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            uiState.bySetKind.forEach { row ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(VaultTheme.radii.sm))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(VaultTheme.spacing.sm),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(row.setType, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                    Text(
                        "${row.fileCount} · ${formatBytes(row.totalBytes)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            VoidButton(
                text = "Export report (.txt)",
                onClick = { exportLauncher.launch("void-backup-report.txt") },
                modifier = Modifier.fillMaxWidth(),
            )

            uiState.exportMessage?.let { message ->
                Text(message, style = MaterialTheme.typography.bodyMedium, color = VaultColorTokens.Verdigris500)
            }
        }
    }
}

@Composable
private fun SummaryCard(title: String, lines: List<String>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(VaultTheme.radii.md))
            .background(MaterialTheme.colorScheme.surface)
            .padding(VaultTheme.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(VaultTheme.spacing.xs),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        lines.forEach { line ->
            Text(line, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
