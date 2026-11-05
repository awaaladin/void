package com.vaultkeep.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Usb
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vaultkeep.app.ui.theme.VaultColorTokens
import com.vaultkeep.app.ui.theme.VaultKeepTheme
import com.vaultkeep.app.ui.theme.VaultTheme

enum class DestinationKind { USB, NAS, CLOUD, DRIVE }

sealed class DestinationStatus {
    data object Connected : DestinationStatus()
    data object Disconnected : DestinationStatus()
    data object Syncing : DestinationStatus()
    data class Error(val message: String) : DestinationStatus()
}

data class DestinationUiModel(
    val kind: DestinationKind,
    val displayName: String,
    val detail: String,
    val status: DestinationStatus,
)

@Composable
fun DestinationCard(
    model: DestinationUiModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(VaultTheme.spacing.md),
        modifier = modifier
            .fillMaxWidth()
            .pressScale(interactionSource)
            .clip(RoundedCornerShape(VaultTheme.radii.md))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(VaultTheme.spacing.lg),
    ) {
        DestinationIcon(model.kind, model.status)
        Column(modifier = Modifier.weight(1f)) {
            Text(model.displayName, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(statusLine(model), style = MaterialTheme.typography.bodyMedium, color = statusColor(model.status))
        }
    }
}

@Composable
private fun DestinationIcon(kind: DestinationKind, status: DestinationStatus) {
    val icon: ImageVector = when (kind) {
        DestinationKind.USB -> Icons.Outlined.Usb
        DestinationKind.NAS -> Icons.Outlined.Dns
        DestinationKind.CLOUD -> Icons.Outlined.Cloud
        DestinationKind.DRIVE -> Icons.Outlined.Folder
    }
    val tint = statusColor(status)
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(tint.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun statusColor(status: DestinationStatus) = when (status) {
    is DestinationStatus.Connected -> VaultColorTokens.Verdigris500
    is DestinationStatus.Syncing -> MaterialTheme.colorScheme.primary
    is DestinationStatus.Disconnected -> MaterialTheme.colorScheme.onSurfaceVariant
    is DestinationStatus.Error -> MaterialTheme.colorScheme.error
}

private fun statusLine(model: DestinationUiModel): String = when (val s = model.status) {
    is DestinationStatus.Connected -> model.detail
    is DestinationStatus.Syncing -> "Syncing — ${model.detail}"
    is DestinationStatus.Disconnected -> "Not connected"
    is DestinationStatus.Error -> s.message
}

@Preview(showBackground = true)
@Composable
private fun DestinationCardPreview() {
    VaultKeepTheme {
        Column(verticalArrangement = Arrangement.spacedBy(VaultTheme.spacing.sm)) {
            DestinationCard(
                DestinationUiModel(DestinationKind.USB, "SanDisk 256GB", "182 GB free", DestinationStatus.Connected),
                onClick = {},
            )
            DestinationCard(
                DestinationUiModel(DestinationKind.NAS, "Home NAS", "192.168.1.42", DestinationStatus.Syncing),
                onClick = {},
            )
            DestinationCard(
                DestinationUiModel(DestinationKind.CLOUD, "VaultKeep Cloud", "Free plan — 4.2 GB used", DestinationStatus.Disconnected),
                onClick = {},
            )
            DestinationCard(
                DestinationUiModel(DestinationKind.DRIVE, "Google Drive", "Token expired", DestinationStatus.Error("Sign in again")),
                onClick = {},
            )
        }
    }
}
