package com.vaultkeep.app.worker

import android.content.Context
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.vaultkeep.app.data.auth.DriveAuthManager
import com.vaultkeep.app.data.crypto.DestinationEncryptor
import com.vaultkeep.app.data.destination.CloudDestination
import com.vaultkeep.app.data.destination.GoogleDriveDestination
import com.vaultkeep.app.data.destination.SmbDestination
import com.vaultkeep.app.data.destination.UsbDestination
import com.vaultkeep.app.data.destination.WebDavDestination
import com.vaultkeep.app.data.local.BackupSetsPreferences
import com.vaultkeep.app.data.local.BackupSetupPreferences
import com.vaultkeep.app.data.local.NasCredentialsStore
import com.vaultkeep.app.data.remote.DriveApi
import com.vaultkeep.app.data.remote.VoidApi
import com.vaultkeep.app.di.DriveHttpClient
import com.vaultkeep.app.domain.BackupDestination
import com.vaultkeep.app.domain.model.BackupSetKind
import com.vaultkeep.app.domain.model.NasProtocol
import com.vaultkeep.app.domain.usecase.RunAllEnabledBackupsUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import okhttp3.OkHttpClient

/**
 * Runs an actual incremental backup in the background via WorkManager,
 * posting the single low-importance progress notification from
 * [BackupNotifications] as a foreground service for the duration of the
 * transfer. This is what EVERY "Back up now" button (USB, NAS, Cloud, Drive)
 * enqueues via [BackupScheduler.runNowTo] — running the transfer here instead
 * of in a screen's ViewModel means it keeps going if the screen is closed,
 * the app is backgrounded, or the phone screen locks, same as the daily
 * scheduled run. [KEY_DESTINATION_KIND] says which destination to build;
 * everything else needed to build it is already persisted (SAF tree URI,
 * NAS credentials, or an auth token this worker re-derives itself), so no
 * secrets travel through WorkManager's input Data.
 */
@HiltWorker
class BackupWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val preferences: BackupSetupPreferences,
    private val backupSetsPreferences: BackupSetsPreferences,
    private val runAllEnabledBackupsUseCase: RunAllEnabledBackupsUseCase,
    private val destinationEncryptor: DestinationEncryptor,
    private val nasCredentialsStore: NasCredentialsStore,
    private val voidApi: VoidApi,
    private val driveAuthManager: DriveAuthManager,
    private val driveApi: DriveApi,
    @DriveHttpClient private val driveHttpClient: OkHttpClient,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        BackupNotifications.ensureChannel(applicationContext)
        setForeground(foregroundInfo(0))

        val kind = inputData.getString(KEY_DESTINATION_KIND) ?: DESTINATION_USB
        val destination = buildDestination(kind) ?: return Result.success()

        val sourceUriString = preferences.sourceUri.first()
        val anySetEnabled = backupSetsPreferences.isEnabled(BackupSetKind.PHOTOS).first() ||
            backupSetsPreferences.isEnabled(BackupSetKind.VIDEOS).first()
        if (sourceUriString == null && !anySetEnabled) return Result.success()

        return try {
            val summary = runAllEnabledBackupsUseCase.execute(
                sourceTreeUri = sourceUriString?.let(Uri::parse),
                destination = destination,
                onProgress = { event ->
                    val percent = if (event.totalFiles > 0) (event.filesDone * 100 / event.totalFiles) else 0
                    setForeground(foregroundInfo(percent, event.currentFile))
                    setProgress(
                        Data.Builder()
                            .putInt(KEY_FILES_DONE, event.filesDone)
                            .putInt(KEY_TOTAL_FILES, event.totalFiles)
                            .putString(KEY_CURRENT_FILE, event.currentFile)
                            .build(),
                    )
                },
            )
            Result.success(
                Data.Builder()
                    .putInt(KEY_FILES_COPIED, summary.filesCopied)
                    .putInt(KEY_FILES_SKIPPED, summary.filesSkipped)
                    .putInt(KEY_FILES_FAILED, summary.filesFailed)
                    .putLong(KEY_BYTES_COPIED, summary.bytesCopied)
                    .putString(KEY_FIRST_ERROR, summary.firstError)
                    .build(),
            )
        } catch (e: Exception) {
            Result.failure(Data.Builder().putString(KEY_FIRST_ERROR, e.message ?: "Backup failed").build())
        }
    }

    private suspend fun buildDestination(kind: String): BackupDestination? {
        return when (kind) {
            DESTINATION_USB -> {
                val uriString = preferences.destinationUri.first() ?: return null
                destinationEncryptor.wrap(
                    UsbDestination(
                        context = applicationContext,
                        id = "usb:$uriString",
                        displayName = "USB/SSD destination",
                        treeUri = Uri.parse(uriString),
                    ),
                )
            }
            DESTINATION_NAS -> {
                val config = nasCredentialsStore.load() ?: return null
                val nasDestination = when (config.protocol) {
                    NasProtocol.SMB -> SmbDestination(config, id = "smb:${config.host}", displayName = config.displayName())
                    NasProtocol.WEBDAV -> WebDavDestination(config, id = "webdav:${config.host}", displayName = config.displayName())
                }
                destinationEncryptor.wrap(nasDestination)
            }
            DESTINATION_CLOUD -> destinationEncryptor.wrap(CloudDestination(api = voidApi))
            DESTINATION_DRIVE -> {
                // Non-interactive: Play Services caches the earlier consent and hands back
                // a fresh token here without a PendingIntent, unless the user revoked access.
                val outcome = driveAuthManager.authorize()
                val token = (outcome as? DriveAuthManager.AuthorizeOutcome.Authorized)?.accessToken ?: return null
                destinationEncryptor.wrap(GoogleDriveDestination(driveApi, driveHttpClient, token))
            }
            else -> null
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
        const val UNIQUE_ONE_TIME_PREFIX = "vaultkeep_triggered_backup_"

        const val KEY_DESTINATION_KIND = "destination_kind"
        const val DESTINATION_USB = "usb"
        const val DESTINATION_NAS = "nas"
        const val DESTINATION_CLOUD = "cloud"
        const val DESTINATION_DRIVE = "drive"

        const val KEY_FILES_DONE = "files_done"
        const val KEY_TOTAL_FILES = "total_files"
        const val KEY_CURRENT_FILE = "current_file"
        const val KEY_FILES_COPIED = "files_copied"
        const val KEY_FILES_SKIPPED = "files_skipped"
        const val KEY_FILES_FAILED = "files_failed"
        const val KEY_BYTES_COPIED = "bytes_copied"
        const val KEY_FIRST_ERROR = "first_error"
    }
}
