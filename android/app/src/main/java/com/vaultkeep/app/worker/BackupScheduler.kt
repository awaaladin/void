package com.vaultkeep.app.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.vaultkeep.app.data.local.ScheduleMode
import com.vaultkeep.app.data.local.SchedulingSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Translates [SchedulingSettings] into real WorkManager state. Called once on
 * app start (to restore whatever was last configured) and again every time
 * the user changes a setting — this is the only place that calls
 * enqueueUniquePeriodicWork/cancelUniqueWork for scheduled backups, so the
 * two can never drift apart.
 */
@Singleton
class BackupScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun apply(settings: SchedulingSettings) {
        val workManager = WorkManager.getInstance(context)

        if (settings.mode == ScheduleMode.MANUAL) {
            workManager.cancelUniqueWork(BackupWorker.UNIQUE_PERIODIC_NAME)
            return
        }

        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .setRequiresCharging(settings.mode == ScheduleMode.ON_CHARGE || settings.requireCharging)
            .setRequiredNetworkType(
                if (settings.mode == ScheduleMode.ON_WIFI || settings.requireWifi) {
                    NetworkType.UNMETERED
                } else {
                    NetworkType.NOT_REQUIRED
                },
            )
            .build()

        val request = PeriodicWorkRequestBuilder<BackupWorker>(1, TimeUnit.DAYS)
            .setConstraints(constraints)
            .build()

        workManager.enqueueUniquePeriodicWork(
            BackupWorker.UNIQUE_PERIODIC_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    fun runNow() {
        val request = androidx.work.OneTimeWorkRequestBuilder<BackupWorker>().build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            BackupWorker.UNIQUE_ONE_TIME_NAME,
            androidx.work.ExistingWorkPolicy.REPLACE,
            request,
        )
    }
}
