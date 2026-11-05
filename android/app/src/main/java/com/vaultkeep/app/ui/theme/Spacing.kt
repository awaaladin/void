package com.vaultkeep.app.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** 4dp base spacing scale. Keep every layout gap/padding expressed in these tokens. */
data class VaultSpacing(
    val none: Dp = 0.dp,
    val xs: Dp = 4.dp,
    val sm: Dp = 8.dp,
    val md: Dp = 12.dp,
    val lg: Dp = 16.dp,
    val xl: Dp = 24.dp,
    val xxl: Dp = 32.dp,
    val xxxl: Dp = 48.dp,
)

data class VaultRadii(
    val sm: Dp = 8.dp,
    val md: Dp = 14.dp,
    val lg: Dp = 20.dp,
    val pill: Dp = 999.dp,
)

data class VaultElevation(
    val level0: Dp = 0.dp,
    val level1: Dp = 1.dp,
    val level2: Dp = 3.dp,
    val level3: Dp = 6.dp,
)

val LocalVaultSpacing = staticCompositionLocalOf { VaultSpacing() }
val LocalVaultRadii = staticCompositionLocalOf { VaultRadii() }
val LocalVaultElevation = staticCompositionLocalOf { VaultElevation() }
