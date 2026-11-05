package com.vaultkeep.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.vaultkeep.app.ui.navigation.VaultNavGraph
import com.vaultkeep.app.ui.theme.VaultKeepTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Single activity. Normal, visible launcher entry — the owner opens Void
 * like any other app; there is nothing here to hide it from them.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VaultKeepTheme {
                VaultNavGraph()
            }
        }
    }
}
