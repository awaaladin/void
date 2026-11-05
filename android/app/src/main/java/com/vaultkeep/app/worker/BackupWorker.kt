package com.vaultkeep.app.worker

import android.content.Context
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.vaultkeep.app.data.destination.UsbDestination
import com.vaultkeep.app.data.local.BackupSetupPreferences
import com.vaultkeep.app.domain.model.BackupSetKind
import com.vaultkeep.app.domain.usecase.RunUsbBackupUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

/**
 * Runs an actual incremental USB backup in the background via WorkManager,
 * posting the single low-importance progress notification from
 * [BackupNotifications] as a foreground service for the duration of the
 * transfer (required on Android 12+ for any work running more than a few
 * seconds). Reads the SAF URIs the user already picked in Phase 2's setup
 * screen — if neither has been picked yet, this is a real, intentional
 * no-op (returns success without doing anything), not a silent failure.
 */
@HiltWorker
class BackupWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val preferences: BackupSetupPreferences,
    private val runUsbBackupUseCase: RunUsbBackupUseCase,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        BackupNotifications.ensureChannel(applicationContext)
        setForeground(foregroundInfo(0))

        val sourceUriString = preferences.sourceUri.first() ?: return Result.success()
        val destinationUriString = preferences.destinationUri.first() ?: return Result.success()

        val sourceUri = Uri.parse(sourceUriString)
        val destinationUri = Uri.parse(destinationUriString)

        val destination = UsbDestination(
            context = applicationContext,
            id = "usb:$destinationUri",
            displayName = "USB/SSD destination",
            treeUri = destinationUri,
        )

        return try {
            val summary = runUsbBackupUseCase.execute(
                sourceTreeUri = sourceUri,
                destination = destination,
                setType = BackupSetKind.CUSTOM,
                onProgress = { event ->
                    val percent = if (event.totalFiles > 0) (event.filesDone * 100 / event.totalFiles) else 0
                    setForeground(foregroundInfo(percent, event.currentFile))
                },
            )
            if (summary.filesFailed > 0) Result.retry() else Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    private fun foregroundInfo(percent: Int, currentFile: String? = null): ForegroundInfo {
        val notification = BackupNotifications.progressNotification(
            context = applicationContext,
            title = "Void is backing up",
            text = currentFile ?: "Starting…",
            progressPercent = percent,
        )
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                BackupNotifications.PROGRESS_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            ForegroundInfo(BackupNotifications.PROGRESS_NOTIFICATION_ID, notification)
        }
    }

    companion object {
        const val UNIQUE_PERIODIC_NAME = "vaultkeep_scheduled_backup"
        const val UNIQUE_ONE_TIME_NAME = "vaultkeep_triggered_backup"
    }
}
