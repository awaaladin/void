package com.vaultkeep.app.ui.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.vaultkeep.app.ui.backupsetup.BackupSetupScreen
import com.vaultkeep.app.ui.batteryguide.BatteryGuideScreen
import com.vaultkeep.app.ui.cloudauth.CloudAuthScreen
import com.vaultkeep.app.ui.home.HomeScreen
import com.vaultkeep.app.ui.nassetup.NasSetupScreen
import com.vaultkeep.app.ui.scheduling.SchedulingScreen
import com.vaultkeep.app.ui.splash.SplashScreen
import com.vaultkeep.app.ui.theme.VaultTheme

/**
 * Single NavHost for the app. Phase 1 wires only Home; DestinationsList /
 * Restore / Settings are added as real screens in their respective phases.
 */
@Composable
fun VaultNavGraph(
    navController: NavHostController = rememberNavController(),
) {
    NavHost(
        navController = navController,
        startDestination = VaultDestination.Splash.route,
        enterTransition = { fadeIn(animationSpec = androidx.compose.animation.core.tween(180)) },
        exitTransition = { fadeOut(animationSpec = androidx.compose.animation.core.tween(180)) },
    ) {
        composable(VaultDestination.Splash.route) {
            SplashScreen(
                onFinished = {
                    navController.navigate(VaultDestination.Home.route) {
                        popUpTo(VaultDestination.Splash.route) { inclusive = true }
                    }
                },
            )
        }
        composable(VaultDestination.Home.route) {
            HomeScreen(
                onOpenBackupSetup = { navController.navigate(VaultDestination.BackupSetup.route) },
                onOpenNasSetup = { navController.navigate(VaultDestination.NasSetup.route) },
                onOpenCloudAuth = { navController.navigate(VaultDestination.CloudAuth.route) },
            )
        }
        composable(VaultDestination.BackupSetup.route) {
            BackupSetupScreen(onOpenScheduling = { navController.navigate(VaultDestination.Scheduling.route) })
        }
        composable(VaultDestination.Scheduling.route) {
            SchedulingScreen(onOpenBatteryGuide = { navController.navigate(VaultDestination.BatteryGuide.route) })
        }
        composable(VaultDestination.BatteryGuide.route) {
            BatteryGuideScreen()
        }
        composable(VaultDestination.NasSetup.route) {
            NasSetupScreen(onSaved = { navController.popBackStack() })
        }
        composable(VaultDestination.CloudAuth.route) {
            CloudAuthScreen()
        }
    }
}
