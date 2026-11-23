package com.vaultkeep.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.vaultkeep.app.ui.theme.VaultTheme

/**
 * The same icon-in-a-tinted-circle identity [DestinationCard] uses on Home,
 * reused at the top of every setup/detail screen so they read as the same
 * app instead of falling back to a bare Material3 headline — this is the
 * fix for screens looking "blank and generic."
 *
 * [onBack], when provided, draws a back arrow above the title row. Every
 * screen except Home passes one (wired to `navController.popBackStack()`)
 * since system back alone wasn't a visible enough way out of a screen.
 */
@Composable
fun ScreenHeader(
    icon: ImageVector,
    tint: Color,
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        if (onBack != null) {
            IconButton(onClick = onBack, modifier = Modifier.padding(bottom = VaultTheme.spacing.xs)) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onBackground,
                )
            }
        }
        Row(verticalAlignment = Alignment.Top) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(tint.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(26.dp))
            }
            Column(modifier = Modifier.padding(start = VaultTheme.spacing.md).weight(1f)) {
                Text(title, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground)
                if (subtitle != null) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }
    }
}
