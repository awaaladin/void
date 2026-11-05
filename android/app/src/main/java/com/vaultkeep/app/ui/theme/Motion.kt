package com.vaultkeep.app.ui.theme

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Motion tokens. Everything physical — springs, not fixed-duration tweens — so
 * interactions stay interruptible. Durations below are reference values for the
 * rare tween (e.g. simple alpha fades); prefer the spring specs for anything a
 * user can touch mid-animation (press, drag, progress).
 */
data class VaultMotion(
    val pressScaleSpec: FiniteAnimationSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessHigh,
    ),
    val listEntranceSpec: FiniteAnimationSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessMediumLow,
    ),
    val progressSpec: FiniteAnimationSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessLow,
    ),
    val navTransitionDurationMs: Int = 260,
    val fadeDurationMs: Int = 180,
    // Scale applied on press for any tappable surface (button, card, row).
    val pressedScale: Float = 0.97f,
)

val LocalVaultMotion = staticCompositionLocalOf { VaultMotion() }
