package com.vaultkeep.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import com.vaultkeep.app.ui.theme.VaultTheme

/**
 * Applies the standard VaultKeep press-scale feedback to any tappable surface.
 * Uses graphicsLayer (not a layout-affecting scale) so scaling never triggers
 * a relayout/recomposition of siblings — just a cheap transform on this node.
 *
 * Usage: Modifier.pressScale(interactionSource).clickable(interactionSource, ...) { }
 */
@Composable
fun Modifier.pressScale(interactionSource: MutableInteractionSource): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val targetScale = if (pressed) VaultTheme.motion.pressedScale else 1f
    val scale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = VaultTheme.motion.pressScaleSpec,
        label = "pressScale",
    )
    return this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}
