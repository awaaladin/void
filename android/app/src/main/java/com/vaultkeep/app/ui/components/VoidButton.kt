package com.vaultkeep.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Button as Material3Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.vaultkeep.app.ui.theme.VaultColorTokens
import com.vaultkeep.app.ui.theme.VaultTheme

/**
 * The app's one primary button, used everywhere instead of stock
 * Material3 `Button` — press-scale feedback (via [pressScale]), a subtle
 * haptic tick on every tap, and the signal-purple fill with the pill
 * radius from the design tokens, so every "Start backup" / "Save" /
 * "Test connection" moment in the app feels like the same product.
 */
@Composable
fun VoidButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val haptics = LocalHapticFeedback.current

    Material3Button(
        onClick = {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        },
        enabled = enabled && !loading,
        interactionSource = interactionSource,
        shape = RoundedCornerShape(VaultTheme.radii.pill),
        colors = ButtonDefaults.buttonColors(
            containerColor = VaultColorTokens.Signal500,
            contentColor = VaultColorTokens.Ink50,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = VaultTheme.elevation.level2, pressedElevation = VaultTheme.elevation.level1),
        modifier = modifier
            .height(52.dp)
            .pressScale(interactionSource),
    ) {
        if (loading) {
            CircularProgressIndicator(
                color = VaultColorTokens.Ink50,
                strokeWidth = 2.dp,
                modifier = Modifier.height(20.dp),
            )
        } else {
            Text(text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** The secondary/outlined counterpart — used for "go deeper" actions that aren't the screen's main CTA. */
@Composable
fun VoidSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val haptics = LocalHapticFeedback.current

    OutlinedButton(
        onClick = {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        },
        enabled = enabled,
        interactionSource = interactionSource,
        shape = RoundedCornerShape(VaultTheme.radii.pill),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
        modifier = modifier
            .height(52.dp)
            .pressScale(interactionSource),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}
