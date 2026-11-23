package com.vaultkeep.app.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.vaultkeep.app.data.local.ScheduleMode
import com.vaultkeep.app.data.local.SchedulingPreferences
import com.vaultkeep.app.data.local.SchedulingSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

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
    private val schedulingPreferences: SchedulingPreferences,
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

    suspend fun runNow() = runNowTo(BackupWorker.DESTINATION_USB)

    /**
     * Enqueues a one-time backup to the given destination kind
     * (see [BackupWorker]'s DESTINATION_* constants) as real WorkManager work,
     * so it keeps running if the screen that started it closes, the app is
     * backgrounded, or the phone locks — unlike calling the use case directly
     * from a ViewModel's own coroutine scope. Returns the unique work name so
     * the caller can observe its [androidx.work.WorkInfo].
     *
     * Cloud and Drive actually go over the internet, so — same as a scheduled
     * run — they honor the "require Wi-Fi" setting instead of silently using
     * mobile data for what can be a multi-gigabyte upload. USB and NAS don't
     * need this: USB is local storage, and NAS is already reached over
     * whatever network it's configured on.
     */
    suspend fun runNowTo(destinationKind: String): String {
        val uniqueName = BackupWorker.UNIQUE_ONE_TIME_PREFIX + destinationKind
        val needsNetwork = destinationKind == BackupWorker.DESTINATION_CLOUD || destinationKind == BackupWorker.DESTINATION_DRIVE
        val requireWifi = needsNetwork && schedulingPreferences.settings.first().requireWifi
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(if (requireWifi) NetworkType.UNMETERED else NetworkType.NOT_REQUIRED)
            .build()
        val request = androidx.work.OneTimeWorkRequestBuilder<BackupWorker>()
            .setConstraints(constraints)
            .setInputData(
                androidx.work.Data.Builder()
                    .putString(BackupWorker.KEY_DESTINATION_KIND, destinationKind)
                    .build(),
            )
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            uniqueName,
            androidx.work.ExistingWorkPolicy.KEEP,
            request,
        )
        return uniqueName
    }

    /** The latest [WorkInfo] for a unique one-time backup name, or null if it's never been run. */
    fun observe(uniqueName: String): Flow<WorkInfo?> =
        WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow(uniqueName).map { it.firstOrNull() }
}
