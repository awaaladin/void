package com.vaultkeep.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Void design tokens — color.
 *
 * Palette intent: a deep "ink-vault" dark base (not black, not default Material
 * surface grey), one purple signal accent used sparingly for primary actions
 * and progress, and a muted verdigris for success/verified states — plus
 * white used deliberately in the light theme and on the launcher icon. No
 * stock Material default-blue, no default gradients.
 */
object VaultColorTokens {

    // Ink-vault base — the dark, cool backbone of the app.
    val Ink950 = Color(0xFF0B0E12)
    val Ink900 = Color(0xFF12161C)
    val Ink800 = Color(0xFF1B2028)
    val Ink700 = Color(0xFF262D37)
    val Ink600 = Color(0xFF394049)
    val Ink400 = Color(0xFF6B7280)
    val Ink200 = Color(0xFFAAB1BB)
    val Ink100 = Color(0xFFD8DCE1)
    val Ink50 = Color(0xFFF2F4F6)

    // Signal accent — purple, used for primary actions and progress.
    val Signal600 = Color(0xFF6D28D9)
    val Signal500 = Color(0xFF9333EA)
    val Signal400 = Color(0xFFC4B5FD)
    val SignalSurface = Color(0xFF241A3D)

    // Muted verdigris — success / verified.
    val Verdigris600 = Color(0xFF3F8E7C)
    val Verdigris500 = Color(0xFF53A692)
    val VerdigrisSurface = Color(0xFF16261F)

    // Errors — kept desaturated to match the rest of the palette, not stock red.
    val Ember600 = Color(0xFFB8503F)
    val Ember500 = Color(0xFFCB6B57)
    val EmberSurface = Color(0xFF2A1714)

    // Light theme surfaces — warm paper, not pure white, so both themes share a mood.
    val Paper50 = Color(0xFFFAF8F5)
    val Paper100 = Color(0xFFF1EDE6)
    val Paper200 = Color(0xFFE3DDD2)
}
