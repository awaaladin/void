package com.vaultkeep.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.vaultkeep.app.worker.BackupWorker

/**
 * Listens for removable storage being mounted and kicks off a one-time
 * backup run when one is.
 *
 * Important, real limitation — not glossed over: `ACTION_MEDIA_MOUNTED` can
 * only be received by a *dynamically registered* receiver (Android's own
 * docs forbid declaring it in the manifest), which means this only fires
 * while VaultKeep's process is already alive. It is NOT a guaranteed
 * "plug in the SSD and it backs up even if the app was fully killed"
 * trigger — Android gives no public, policy-compliant API for that on
 * modern versions without the app (or a persistent foreground service)
 * already running. What this *does* give you: if VaultKeep is open, or
 * was recently in the background, plugging in the destination drive
 * starts a backup without you tapping anything.
 */
class StorageAttachReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MEDIA_MOUNTED) return

        val request = OneTimeWorkRequestBuilder<BackupWorker>().build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            BackupWorker.UNIQUE_ONE_TIME_NAME,
            androidx.work.ExistingWorkPolicy.KEEP,
            request,
        )
    }

    companion object {
        fun register(context: Context) {
            val filter = android.content.IntentFilter(Intent.ACTION_MEDIA_MOUNTED).apply {
                addDataScheme("file")
            }
            // targetSdk 34 requires an explicit exported flag for dynamically
            // registered receivers; NOT_EXPORTED since only the system needs
            // to deliver this broadcast to us, never another app.
            ContextCompat.registerReceiver(
                context,
                StorageAttachReceiver(),
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
        }
    }
}
