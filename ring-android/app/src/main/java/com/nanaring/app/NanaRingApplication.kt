package com.nanaring.app

import android.app.Application
import androidx.work.Constraints
import androidx.work.Configuration
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.nanaring.app.di.AppContainer
import com.nanaring.app.sync.SyncWorker
import java.util.concurrent.TimeUnit

class NanaRingApplication : Application(), Configuration.Provider {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(applicationContext)
        if (container.supabaseSyncAuth.restoreSession()) {
            scheduleSyncWorker()
        }
    }

    override val workManagerConfiguration: Configuration
        get() = container.workManagerConfiguration

    fun scheduleSyncWorker() {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "ring_sync",
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }
}
