package com.vaultkeep.app.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.appearanceDataStore by preferencesDataStore(name = "appearance_settings")

enum class ThemeMode { DARK, LIGHT, SYSTEM }

/** Dark by default — Void's identity (eclipse splash, ink/signal-purple palette) is designed
 * dark-first — but user-changeable, never hardcoded. */
@Singleton
class AppearancePreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val themeModeKey = stringPreferencesKey("theme_mode")

    val themeMode: Flow<ThemeMode> = context.appearanceDataStore.data.map { prefs ->
        prefs[themeModeKey]?.let { stored -> runCatching { ThemeMode.valueOf(stored) }.getOrNull() } ?: ThemeMode.DARK
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.appearanceDataStore.edit { it[themeModeKey] = mode.name }
    }
}
