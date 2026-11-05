package com.vaultkeep.app.ui.navigation

/**
 * Top-level navigation routes. Kept as a sealed type (not raw strings scattered
 * around the app) so adding a screen later is a compile-time-checked change.
 */
sealed class VaultDestination(val route: String) {
    data object Splash : VaultDestination("splash")
    data object Home : VaultDestination("home")

    // Added in later phases — present here as placeholders so NavGraph wiring
    // doesn't need to change shape when they land.
    data object DestinationsList : VaultDestination("destinations")
    data object BackupSetup : VaultDestination("backup_setup")
    data object Scheduling : VaultDestination("scheduling")
    data object BatteryGuide : VaultDestination("battery_guide")
    data object NasSetup : VaultDestination("nas_setup")
    data object Restore : VaultDestination("restore")
    data object Settings : VaultDestination("settings")
}
