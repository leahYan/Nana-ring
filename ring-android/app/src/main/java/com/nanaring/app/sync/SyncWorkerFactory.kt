package com.nanaring.app.sync

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import com.nanaring.app.di.AppContainer

/**
 * Custom [WorkerFactory] that injects [AppContainer] into [SyncWorker].
 * Registered via [NanaRingApplication] implementing [Configuration.Provider]
 * so WorkManager uses this factory without Hilt.
 */
class SyncWorkerFactory(private val container: AppContainer) : WorkerFactory() {

    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters,
    ): ListenableWorker? =
        when (workerClassName) {
            SyncWorker::class.java.name -> SyncWorker(appContext, workerParameters)
            else                        -> null   // Fall through to default factory
        }
}
