package com.nanaring.app.di

import android.content.Context
import androidx.work.Configuration
import com.nanaring.app.data.local.RingDatabase
import com.nanaring.app.data.remote.RingApiService
import com.nanaring.app.data.repository.PhysicalRingDataSource
import com.nanaring.app.data.repository.RingRepository
// MockRingDataSource has been removed — no fake/mock data sources permitted (PROJECT_OVERVIEW §2)
import com.nanaring.app.sync.SyncWorkerFactory
import com.nanaring.app.util.AuthManager
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * Manual DI container. Created once in [com.nanaring.app.NanaRingApplication] and
 * accessed via the Application instance throughout the app.
 *
 * apiService() is a method (not lazy val) so that a URL change in DeveloperSettings
 * causes Retrofit to be rebuilt on the next call — no stale endpoint references.
 */
class AppContainer(context: Context) {

    val authManager = AuthManager(context)

    val database: RingDatabase = RingDatabase.getInstance(context)

    // Tracks the URL used when the current Retrofit instance was created.
    private var cachedBaseUrl: String? = null
    private var cachedApiService: RingApiService? = null

    fun apiService(): RingApiService {
        val url = authManager.serverUrl
        if (url == cachedBaseUrl && cachedApiService != null) {
            return cachedApiService!!
        }
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            .build()

        val service = Retrofit.Builder()
            .baseUrl(url)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(RingApiService::class.java)

        cachedBaseUrl = url
        cachedApiService = service
        return service
    }

    // Repository — PhysicalRingDataSource is the only permitted implementation.
    // All health data must originate from the physical ring via the SDK.
    val ringRepository: RingRepository = PhysicalRingDataSource(
        context     = context,
        application = context.applicationContext as android.app.Application,
        heartRateDao = database.heartRateDao(),
    )

    val workerFactory: SyncWorkerFactory
        get() = SyncWorkerFactory(this)

    val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
