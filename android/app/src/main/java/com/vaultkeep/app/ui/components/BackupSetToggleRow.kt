package com.vaultkeep.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.vaultkeep.app.ui.theme.VaultColorTokens
import com.vaultkeep.app.ui.theme.VaultKeepTheme
import com.vaultkeep.app.ui.theme.VaultTheme

enum class BackupSetType { PHOTOS, VIDEOS, DOCUMENTS, DOWNLOADS, WHATSAPP, CUSTOM }

data class BackupSetUiModel(
    val type: BackupSetType,
    val label: String,
    val sizeLabel: String,
    val enabled: Boolean,
    /** Disabled rows (e.g. no destination assigned yet) still render, just non-interactive. */
    val interactive: Boolean = true,
)

@Composable
fun BackupSetToggleRow(
    model: BackupSetUiModel,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = VaultTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text(
                model.label,
                style = MaterialTheme.typography.titleMedium,
                color = if (model.interactive) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(model.sizeLabel, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(
            checked = model.enabled,
            onCheckedChange = onToggle,
            enabled = model.interactive,
            colors = SwitchDefaults.colors(
                checkedThumbColor = VaultColorTokens.Signal500,
                checkedTrackColor = VaultColorTokens.Signal600.copy(alpha = 0.4f),
            ),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun BackupSetToggleRowPreview() {
    VaultKeepTheme {
        Column {
            BackupSetToggleRow(
                BackupSetUiModel(BackupSetType.PHOTOS, "Photos", "12,402 files · 38.1 GB", enabled = true),
                onToggle = {},
            )
            BackupSetToggleRow(
                BackupSetUiModel(BackupSetType.WHATSAPP, "WhatsApp media", "No destination assigned", enabled = false, interactive = false),
                onToggle = {},
            )
        }
    }
}
