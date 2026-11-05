package com.vaultkeep.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

private val VaultDarkColorScheme = darkColorScheme(
    primary = VaultColorTokens.Signal500,
    onPrimary = VaultColorTokens.Ink950,
    primaryContainer = VaultColorTokens.SignalSurface,
    onPrimaryContainer = VaultColorTokens.Signal400,

    secondary = VaultColorTokens.Verdigris500,
    onSecondary = VaultColorTokens.Ink950,
    secondaryContainer = VaultColorTokens.VerdigrisSurface,
    onSecondaryContainer = VaultColorTokens.Verdigris500,

    error = VaultColorTokens.Ember500,
    onError = VaultColorTokens.Ink950,
    errorContainer = VaultColorTokens.EmberSurface,
    onErrorContainer = VaultColorTokens.Ember500,

    background = VaultColorTokens.Ink950,
    onBackground = VaultColorTokens.Ink50,
    surface = VaultColorTokens.Ink900,
    onSurface = VaultColorTokens.Ink50,
    surfaceVariant = VaultColorTokens.Ink800,
    onSurfaceVariant = VaultColorTokens.Ink200,
    outline = VaultColorTokens.Ink600,
)

private val VaultLightColorScheme = lightColorScheme(
    primary = VaultColorTokens.Signal600,
    onPrimary = VaultColorTokens.Paper50,
    primaryContainer = Color(0xFFE9D8FD),
    onPrimaryContainer = VaultColorTokens.Signal600,

    secondary = VaultColorTokens.Verdigris600,
    onSecondary = VaultColorTokens.Paper50,
    secondaryContainer = Color(0xFFD6E8E2),
    onSecondaryContainer = VaultColorTokens.Verdigris600,

    error = VaultColorTokens.Ember600,
    onError = VaultColorTokens.Paper50,
    errorContainer = Color(0xFFF1D8D3),
    onErrorContainer = VaultColorTokens.Ember600,

    background = VaultColorTokens.Paper50,
    onBackground = VaultColorTokens.Ink900,
    surface = VaultColorTokens.Paper100,
    onSurface = VaultColorTokens.Ink900,
    surfaceVariant = VaultColorTokens.Paper200,
    onSurfaceVariant = VaultColorTokens.Ink600,
    outline = VaultColorTokens.Ink400,
)

object VaultTheme {
    val spacing: VaultSpacing
        @Composable @ReadOnlyComposable get() = LocalVaultSpacing.current
    val radii: VaultRadii
        @Composable @ReadOnlyComposable get() = LocalVaultRadii.current
    val elevation: VaultElevation
        @Composable @ReadOnlyComposable get() = LocalVaultElevation.current
    val motion: VaultMotion
        @Composable @ReadOnlyComposable get() = LocalVaultMotion.current
}

/**
 * Root theme wrapper. Wraps MaterialTheme (used only as a base for built-in
 * components) and layers VaultKeep's own spacing/radii/motion tokens on top
 * via CompositionLocals, exposed through [VaultTheme].
 */
@Composable
fun VaultKeepTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) VaultDarkColorScheme else VaultLightColorScheme

    CompositionLocalProvider(
        LocalVaultSpacing provides VaultSpacing(),
        LocalVaultRadii provides VaultRadii(),
        LocalVaultElevation provides VaultElevation(),
        LocalVaultMotion provides VaultMotion(),
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = VaultTypography,
            content = content,
        )
    }
}
