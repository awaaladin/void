package com.vaultkeep.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import kotlinx.coroutines.delay

/**
 * Each item fades + slides up with a short delay proportional to its index —
 * the same staggered-list entrance Home uses, shared so every screen gets
 * the same lively feel instead of content just appearing all at once.
 * One-shot: only plays on first composition of this node, never replays on recomposition.
 */
@Composable
fun StaggeredEntrance(index: Int, content: @Composable () -> Unit) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(30L * index)
        visible = true
    }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(220)) + slideInVertically(
            animationSpec = tween(220),
            initialOffsetY = { it / 4 },
        ),
    ) {
        content()
    }
}
