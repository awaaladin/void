package com.vaultkeep.app.worker

import android.content.Context
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.vaultkeep.app.data.crypto.DestinationEncryptor
import com.vaultkeep.app.data.destination.UsbDestination
import com.vaultkeep.app.data.local.BackupSetsPreferences
import com.vaultkeep.app.data.local.BackupSetupPreferences
import com.vaultkeep.app.domain.model.BackupSetKind
import com.vaultkeep.app.domain.usecase.RunAllEnabledBackupsUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

/**
 * Runs an actual incremental USB backup in the background via WorkManager,
 * posting the single low-importance progress notification from
 * [BackupNotifications] as a foreground service for the duration of the
 * transfer (required on Android 12+ for any work running more than a few
 * seconds). Backs up the manually-picked source folder (if one is set) plus
 * whichever of Photos/Videos are enabled on Home — if neither applies, or no
 * destination has been picked, this is a real, intentional no-op (returns
 * success without doing anything), not a silent failure.
 */
@HiltWorker
class BackupWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val preferences: BackupSetupPreferences,
    private val backupSetsPreferences: BackupSetsPreferences,
    private val runAllEnabledBackupsUseCase: RunAllEnabledBackupsUseCase,
    private val destinationEncryptor: DestinationEncryptor,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        BackupNotifications.ensureChannel(applicationContext)
        setForeground(foregroundInfo(0))

        val destinationUriString = preferences.destinationUri.first() ?: return Result.success()
        val destinationUri = Uri.parse(destinationUriString)

        val sourceUriString = preferences.sourceUri.first()
        val anySetEnabled = backupSetsPreferences.isEnabled(BackupSetKind.PHOTOS).first() ||
            backupSetsPreferences.isEnabled(BackupSetKind.VIDEOS).first()
        if (sourceUriString == null && !anySetEnabled) return Result.success()

        val destination = destinationEncryptor.wrap(
            UsbDestination(
                context = applicationContext,
                id = "usb:$destinationUri",
                displayName = "USB/SSD destination",
                treeUri = destinationUri,
            ),
        )

        return try {
            val summary = runAllEnabledBackupsUseCase.execute(
                sourceTreeUri = sourceUriString?.let(Uri::parse),
                destination = destination,
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
