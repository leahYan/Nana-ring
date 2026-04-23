package com.nanaring.app.sync

import android.util.Log
import com.google.gson.Gson
import com.nanaring.app.data.local.entity.HeartRateEntity
import com.nanaring.app.util.AuthManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.UUID

private const val TAG = "SupabaseSyncAuth"
private val JSON_MEDIA = "application/json".toMediaType()

/**
 * Handles Supabase Auth (sign-up, sign-in, token refresh) and direct REST inserts
 * into the ring_* tables. All data paths bypass the FastAPI backend entirely.
 *
 * Token refresh is mutex-protected so concurrent WorkManager retries don't issue
 * multiple parallel refresh calls.
 */
class SupabaseSyncAuth(
    private val supabaseUrl: String,
    private val anonKey: String,
    private val authStore: AuthManager,
    private val client: OkHttpClient,
) {
    private val gson = Gson()
    private val refreshMutex = Mutex()

    // ── Auth ─────────────────────────────────────────────────────────────────

    /**
     * Returns true if a session exists in SharedPreferences.
     * Expiry is intentionally ignored here — refreshIfNeeded() handles that.
     */
    fun restoreSession(): Boolean =
        authStore.userId.isNotBlank() && authStore.refreshToken.isNotBlank()

    suspend fun signIn(email: String, password: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            val body = gson.toJson(mapOf("email" to email, "password" to password))
            val resp = postAuth("/auth/v1/token?grant_type=password", body)
                ?: return@withContext Result.failure(Exception("Sign-in failed"))
            storeSession(resp)
            Result.success(Unit)
        }

    suspend fun signUp(email: String, password: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            val body = gson.toJson(mapOf("email" to email, "password" to password))
            val resp = postAuth("/auth/v1/signup", body)
                ?: return@withContext Result.failure(Exception("Sign-up failed"))
            storeSession(resp)
            Result.success(Unit)
        }

    /**
     * Refreshes the access token if it expires within 60 seconds.
     * Returns false only when the refresh token itself is invalid/expired.
     */
    suspend fun refreshIfNeeded(): Boolean {
        val nowSecs = System.currentTimeMillis() / 1000
        if (authStore.tokenExpiry - nowSecs > 60) return true
        return refreshMutex.withLock {
            // Re-check after acquiring lock — another coroutine may have already refreshed.
            if (authStore.tokenExpiry - System.currentTimeMillis() / 1000 > 60) return@withLock true
            withContext(Dispatchers.IO) {
                val rt = authStore.refreshToken
                if (rt.isBlank()) return@withContext false
                val body = gson.toJson(mapOf("refresh_token" to rt))
                val resp = postAuth("/auth/v1/token?grant_type=refresh_token", body)
                    ?: return@withContext false
                storeSession(resp)
                true
            }
        }
    }

    // ── Per-table inserts ────────────────────────────────────────────────────

    suspend fun insertHeartRate(entities: List<HeartRateEntity>): Boolean {
        val userId = authStore.userId
        val rows = entities.map { e ->
            buildMap<String, Any> {
                put("id", UUID.randomUUID().toString())
                put("user_id", userId)
                put("device_timestamp", e.deviceTimestamp)
                put("bpm", e.bpm)
                e.rri?.let { put("rri", it) }
                put("source", e.source)
                e.syncId.let { put("sync_id", it) }
                e.firmwareVersion?.let { put("firmware_version", it) }
                e.hardwareVersion?.let { put("hardware_version", it) }
            }
        }
        return postRows("ring_heart_rate", rows)
    }

    suspend fun insertSpO2(entities: List<HeartRateEntity>): Boolean {
        val userId = authStore.userId
        val rows = entities.mapNotNull { e ->
            val pct = e.spO2 ?: return@mapNotNull null
            buildMap<String, Any> {
                put("id", UUID.randomUUID().toString())
                put("user_id", userId)
                put("device_timestamp", e.deviceTimestamp)
                put("percent", pct)
                put("reading_type", "manual")
                put("sync_id", "${e.syncId}-spo2")
                e.firmwareVersion?.let { put("firmware_version", it) }
                e.hardwareVersion?.let { put("hardware_version", it) }
            }
        }
        if (rows.isEmpty()) return true
        return postRows("ring_spo2", rows)
    }

    suspend fun insertHRV(entities: List<HeartRateEntity>): Boolean {
        val userId = authStore.userId
        val rows = entities.mapNotNull { e ->
            val ms = e.hrv ?: return@mapNotNull null
            buildMap<String, Any> {
                put("id", UUID.randomUUID().toString())
                put("user_id", userId)
                put("device_timestamp", e.deviceTimestamp)
                put("ms", ms)
                put("source", e.source)
                put("sync_id", "${e.syncId}-hrv")
                e.firmwareVersion?.let { put("firmware_version", it) }
                e.hardwareVersion?.let { put("hardware_version", it) }
            }
        }
        if (rows.isEmpty()) return true
        return postRows("ring_hrv", rows)
    }

    suspend fun insertStress(entities: List<HeartRateEntity>): Boolean {
        val userId = authStore.userId
        val rows = entities.mapNotNull { e ->
            val lvl = e.stress ?: return@mapNotNull null
            buildMap<String, Any> {
                put("id", UUID.randomUUID().toString())
                put("user_id", userId)
                put("device_timestamp", e.deviceTimestamp)
                put("level", lvl)
                put("source", e.source)
                put("sync_id", "${e.syncId}-stress")
                e.firmwareVersion?.let { put("firmware_version", it) }
                e.hardwareVersion?.let { put("hardware_version", it) }
            }
        }
        if (rows.isEmpty()) return true
        return postRows("ring_stress", rows)
    }

    suspend fun insertTemperature(entities: List<HeartRateEntity>): Boolean {
        val userId = authStore.userId
        val rows = entities.mapNotNull { e ->
            val c = e.temperature ?: return@mapNotNull null
            buildMap<String, Any> {
                put("id", UUID.randomUUID().toString())
                put("user_id", userId)
                put("device_timestamp", e.deviceTimestamp)
                put("celsius_primary", c)
                put("measurement_mode", "manual_once")
                put("sync_id", "${e.syncId}-temp")
                e.firmwareVersion?.let { put("firmware_version", it) }
                e.hardwareVersion?.let { put("hardware_version", it) }
            }
        }
        if (rows.isEmpty()) return true
        return postRows("ring_temperature", rows)
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private suspend fun postRows(table: String, rows: List<Map<String, Any>>): Boolean =
        withContext(Dispatchers.IO) {
            if (!refreshIfNeeded()) {
                Log.w(TAG, "Token refresh failed — cannot insert into $table")
                return@withContext false
            }
            val request = Request.Builder()
                .url("$supabaseUrl/rest/v1/$table")
                .post(gson.toJson(rows).toRequestBody(JSON_MEDIA))
                .addHeader("apikey", anonKey)
                .addHeader("Authorization", "Bearer ${authStore.accessToken}")
                .addHeader("Content-Type", "application/json")
                // UNIQUE(sync_id, user_id) constraint absorbs retries silently.
                .addHeader("Prefer", "resolution=ignore-duplicates")
                .build()
            val resp = client.newCall(request).execute()
            val ok = resp.code in 200..201
            if (!ok) Log.w(TAG, "Insert $table → HTTP ${resp.code}: ${resp.body?.string()}")
            resp.close()
            ok
        }

    /**
     * POST to a Supabase Auth endpoint and return the parsed JSON body,
     * or null on failure.
     */
    private suspend fun postAuth(path: String, jsonBody: String): Map<*, *>? =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("$supabaseUrl$path")
                .post(jsonBody.toRequestBody(JSON_MEDIA))
                .addHeader("apikey", anonKey)
                .addHeader("Content-Type", "application/json")
                .build()
            val resp = client.newCall(request).execute()
            val bodyStr = resp.body?.string() ?: ""
            if (!resp.isSuccessful) {
                Log.w(TAG, "Auth $path → HTTP ${resp.code}: $bodyStr")
                return@withContext null
            }
            gson.fromJson(bodyStr, Map::class.java)
        }

    private fun storeSession(resp: Map<*, *>) {
        authStore.accessToken  = resp["access_token"]  as? String ?: return
        authStore.refreshToken = resp["refresh_token"] as? String ?: ""
        // Supabase returns expires_at as a numeric epoch-seconds value.
        authStore.tokenExpiry  = when (val exp = resp["expires_at"]) {
            is Double -> exp.toLong()
            is Long   -> exp
            is Int    -> exp.toLong()
            else      -> 0L
        }
        val user = resp["user"] as? Map<*, *>
        if (user != null) {
            authStore.userId = user["id"] as? String ?: ""
        }
    }
}
