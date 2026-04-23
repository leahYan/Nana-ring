package com.nanaring.app.di

import android.content.Context
import androidx.work.Configuration
import com.nanaring.app.BuildConfig
import com.nanaring.app.data.local.RingDatabase
import com.nanaring.app.data.remote.RingApiService
import com.nanaring.app.data.repository.PhysicalRingDataSource
import com.nanaring.app.data.repository.RingRepository
import com.nanaring.app.sync.SupabaseSyncAuth
import com.nanaring.app.sync.SyncWorkerFactory
import com.nanaring.app.util.AuthManager
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * Manual DI container. Created once in [com.nanaring.app.NanaRingApplication].
 *
 * [supabaseSyncAuth] is the primary data path — Android posts directly to Supabase REST.
 * [apiService] is retained for admin/debug use; it is no longer in the ingestion path.
 */
class AppContainer(context: Context) {

    val authManager = AuthManager(context)

    val database: RingDatabase = RingDatabase.getInstance(context)

    // Shared OkHttpClient (logging interceptor for debugging).
    val httpClient: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(
            HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY }
        )
        .build()

    // Primary sync path — direct Supabase REST with Supabase Auth.
    val supabaseSyncAuth: SupabaseSyncAuth = SupabaseSyncAuth(
        supabaseUrl = BuildConfig.SUPABASE_URL,
        anonKey     = BuildConfig.SUPABASE_ANON_KEY,
        authStore   = authManager,
        client      = httpClient,
    )

    // Retrofit instance pointed at the FastAPI backend (admin/CSV export only).
    // apiService() rebuilds if serverUrl changes in DeveloperSettings.
    private var cachedBaseUrl: String? = null
    private var cachedApiService: RingApiService? = null

    fun apiService(): RingApiService {
        val url = authManager.serverUrl
        if (url == cachedBaseUrl && cachedApiService != null) return cachedApiService!!
        val service = Retrofit.Builder()
            .baseUrl(url)
            .client(httpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(RingApiService::class.java)
        cachedBaseUrl    = url
        cachedApiService = service
        return service
    }

    val ringRepository: RingRepository = PhysicalRingDataSource(
        context      = context,
        application  = context.applicationContext as android.app.Application,
        heartRateDao = database.heartRateDao(),
    )

    val workerFactory: SyncWorkerFactory
        get() = SyncWorkerFactory(this)

    val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
