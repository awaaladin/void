package com.vaultkeep.app.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.vaultkeep.app.R

/**
 * One quiet, low-importance notification channel for backup progress — no
 * sound, no per-file spam. This is the "visible but not loud" behavior the
 * owner-controlled design calls for: a single updating notification while a
 * transfer runs, nothing left behind once it finishes successfully.
 */
object BackupNotifications {
    const val CHANNEL_ID = "backup_progress"
    const val PROGRESS_NOTIFICATION_ID = 1001

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        val existing = manager.getNotificationChannel(CHANNEL_ID)
        if (existing == null) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Backup progress",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Shows while VaultKeep is actively copying files. No sound, no per-file alerts."
                setShowBadge(false)
            }
            manager.createNotificationChannel(channel)
        }
    }

    fun progressNotification(
        context: Context,
        title: String,
        text: String,
        progressPercent: Int?,
    ): android.app.Notification {
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        if (progressPercent != null) {
            builder.setProgress(100, progressPercent, false)
        } else {
            builder.setProgress(0, 0, true)
        }
        return builder.build()
    }
}
