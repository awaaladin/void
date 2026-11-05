package com.vaultkeep.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vaultkeep.app.ui.theme.VaultColorTokens
import com.vaultkeep.app.ui.theme.VaultKeepTheme
import com.vaultkeep.app.ui.theme.VaultTheme

/** States the hero card must render — every one, not just the happy path. */
sealed class BackupHeroState {
    data object Empty : BackupHeroState()
    data object Offline : BackupHeroState()
    data class Running(
        val progress: Float, // 0f..1f
        val bytesPerSecond: Long,
        val etaSeconds: Long,
    ) : BackupHeroState()
    data class Verified(val lastRunLabel: String) : BackupHeroState()
    data class Error(val message: String) : BackupHeroState()
}

/**
 * The home screen's centerpiece: a live progress ring plus a short status
 * line. Progress animates with a spring (never jumps), and the ring eases
 * toward its target even as new progress values arrive mid-animation.
 */
@Composable
fun BackupHeroCard(
    state: BackupHeroState,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(VaultTheme.radii.lg))
            .background(MaterialTheme.colorScheme.surface)
            .padding(VaultTheme.spacing.xl),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(VaultTheme.spacing.lg),
        ) {
            ProgressRing(state = state)
            Column(verticalArrangement = Arrangement.spacedBy(VaultTheme.spacing.xs)) {
                Text(
                    text = titleFor(state),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = subtitleFor(state),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ProgressRing(state: BackupHeroState) {
    val target = when (state) {
        is BackupHeroState.Running -> state.progress
        is BackupHeroState.Verified -> 1f
        else -> 0f
    }
    val animatedProgress by animateFloatAsState(
        targetValue = target,
        animationSpec = VaultTheme.motion.progressSpec,
        label = "heroProgress",
    )

    val ringColor = when (state) {
        is BackupHeroState.Error -> MaterialTheme.colorScheme.error
        is BackupHeroState.Verified -> VaultColorTokens.Verdigris500
        is BackupHeroState.Offline -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.primary
    }

    Box(
        modifier = Modifier.size(64.dp),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.foundation.Canvas(modifier = Modifier.size(64.dp)) {
            val stroke = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round)
            drawArc(
                color = ringColor.copy(alpha = 0.15f),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = stroke,
            )
            drawArc(
                color = ringColor,
                startAngle = -90f,
                sweepAngle = 360f * animatedProgress,
                useCenter = false,
                style = stroke,
            )
        }
        val icon = when (state) {
            is BackupHeroState.Offline -> Icons.Outlined.CloudOff
            is BackupHeroState.Error -> Icons.Outlined.ErrorOutline
            is BackupHeroState.Verified -> Icons.Outlined.CloudDone
            else -> Icons.Outlined.CloudSync
        }
        Icon(imageVector = icon, contentDescription = null, tint = ringColor, modifier = Modifier.size(22.dp))
    }
}

private fun titleFor(state: BackupHeroState): String = when (state) {
    is BackupHeroState.Empty -> "No backups yet"
    is BackupHeroState.Offline -> "Waiting for Wi-Fi"
    is BackupHeroState.Running -> "Backing up…"
    is BackupHeroState.Verified -> "All verified"
    is BackupHeroState.Error -> "Backup needs attention"
}

private fun subtitleFor(state: BackupHeroState): String = when (state) {
    is BackupHeroState.Empty -> "Pick a destination to get started."
    is BackupHeroState.Offline -> "Backups resume automatically when a network is available."
    is BackupHeroState.Running -> {
        val rate = formatRate(state.bytesPerSecond)
        val eta = formatEta(state.etaSeconds)
        "$rate · $eta left"
    }
    is BackupHeroState.Verified -> "Last run ${state.lastRunLabel}"
    is BackupHeroState.Error -> state.message
}

private fun formatRate(bytesPerSecond: Long): String {
    val mb = bytesPerSecond / 1_000_000.0
    return if (mb >= 1.0) "%.1f MB/s".format(mb) else "%.0f KB/s".format(bytesPerSecond / 1000.0)
}

private fun formatEta(seconds: Long): String {
    val m = seconds / 60
    val s = seconds % 60
    return if (m > 0) "${m}m ${s}s" else "${s}s"
}

@Preview(showBackground = true)
@Composable
private fun BackupHeroCardRunningPreview() {
    VaultKeepTheme {
        BackupHeroCard(
            state = BackupHeroState.Running(progress = 0.42f, bytesPerSecond = 3_200_000, etaSeconds = 95),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun BackupHeroCardVerifiedPreview() {
    VaultKeepTheme {
        BackupHeroCard(state = BackupHeroState.Verified(lastRunLabel = "today at 7:02 AM"))
    }
}

@Preview(showBackground = true)
@Composable
private fun BackupHeroCardErrorPreview() {
    VaultKeepTheme {
        BackupHeroCard(state = BackupHeroState.Error(message = "3 files failed — tap to retry"))
    }
}
