package com.nanaring.app

import android.app.Application
import androidx.work.Configuration
import com.nanaring.app.di.AppContainer

/**
 * Custom Application entry point.
 *
 * - Initialises [AppContainer] (manual DI).
 * - Implements [Configuration.Provider] so WorkManager uses [SyncWorkerFactory]
 *   without requiring Hilt.
 */
class NanaRingApplication : Application(), Configuration.Provider {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(applicationContext)
    }

    override val workManagerConfiguration: Configuration
        get() = container.workManagerConfiguration
}
