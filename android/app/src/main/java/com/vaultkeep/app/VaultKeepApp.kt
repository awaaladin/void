package com.vaultkeep.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.vaultkeep.app.data.local.SchedulingPreferences
import com.vaultkeep.app.receiver.StorageAttachReceiver
import com.vaultkeep.app.worker.BackupNotifications
import com.vaultkeep.app.worker.BackupScheduler
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Application entry point. Hilt component graph root. Also the WorkManager
 * [Configuration.Provider] so [com.vaultkeep.app.worker.BackupWorker] gets
 * its dependencies (Room DAOs, the use case) injected like everything else,
 * instead of WorkManager's default no-arg worker construction.
 */
@HiltAndroidApp
class VaultKeepApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var schedulingPreferences: SchedulingPreferences

    @Inject
    lateinit var backupScheduler: BackupScheduler

    override fun onCreate() {
        super.onCreate()
        BackupNotifications.ensureChannel(this)
        StorageAttachReceiver.register(this)

        // Re-apply whatever schedule was last configured — WorkManager's own
        // unique-work persistence survives app restarts, but this keeps the
        // DataStore setting and the actual enqueued request from drifting
        // apart after an app update changes work definitions.
        CoroutineScope(Dispatchers.Default).launch {
            backupScheduler.apply(schedulingPreferences.settings.first())
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
