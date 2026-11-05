package com.vaultkeep.app.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.schedulingDataStore by preferencesDataStore(name = "scheduling")

enum class ScheduleMode { MANUAL, DAILY, ON_CHARGE, ON_WIFI }

data class SchedulingSettings(
    val mode: ScheduleMode = ScheduleMode.MANUAL,
    val requireCharging: Boolean = false,
    val requireWifi: Boolean = false,
)

/**
 * Real, persisted scheduling preferences — read by [com.vaultkeep.app.worker.BackupScheduler]
 * to actually enqueue/cancel WorkManager requests, not just UI state.
 */
@Singleton
class SchedulingPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val modeKey = stringPreferencesKey("schedule_mode")
    private val requireChargingKey = booleanPreferencesKey("require_charging")
    private val requireWifiKey = booleanPreferencesKey("require_wifi")

    val settings: Flow<SchedulingSettings> = context.schedulingDataStore.data.map { prefs ->
        SchedulingSettings(
            mode = prefs[modeKey]?.let { runCatching { ScheduleMode.valueOf(it) }.getOrNull() } ?: ScheduleMode.MANUAL,
            requireCharging = prefs[requireChargingKey] ?: false,
            requireWifi = prefs[requireWifiKey] ?: false,
        )
    }

    suspend fun setMode(mode: ScheduleMode) {
        context.schedulingDataStore.edit { it[modeKey] = mode.name }
    }

    suspend fun setRequireCharging(value: Boolean) {
        context.schedulingDataStore.edit { it[requireChargingKey] = value }
    }

    suspend fun setRequireWifi(value: Boolean) {
        context.schedulingDataStore.edit { it[requireWifiKey] = value }
    }
}
