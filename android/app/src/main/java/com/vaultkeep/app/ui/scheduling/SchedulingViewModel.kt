package com.vaultkeep.app.ui.scheduling

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaultkeep.app.data.local.ScheduleMode
import com.vaultkeep.app.data.local.SchedulingPreferences
import com.vaultkeep.app.data.local.SchedulingSettings
import com.vaultkeep.app.worker.BackupScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SchedulingViewModel @Inject constructor(
    private val preferences: SchedulingPreferences,
    private val scheduler: BackupScheduler,
) : ViewModel() {

    private val _settings = MutableStateFlow(SchedulingSettings())
    val settings: StateFlow<SchedulingSettings> = _settings.asStateFlow()

    init {
        viewModelScope.launch {
            preferences.settings.collect { value ->
                _settings.value = value
            }
        }
    }

    /** Every change here is applied to real WorkManager state immediately, not just saved. */
    fun setMode(mode: ScheduleMode) = viewModelScope.launch {
        preferences.setMode(mode)
        scheduler.apply(preferences.settings.first())
    }

    fun setRequireCharging(value: Boolean) = viewModelScope.launch {
        preferences.setRequireCharging(value)
        scheduler.apply(preferences.settings.first())
    }

    fun setRequireWifi(value: Boolean) = viewModelScope.launch {
        preferences.setRequireWifi(value)
        scheduler.apply(preferences.settings.first())
    }

    fun runNow() = scheduler.runNow()
}
