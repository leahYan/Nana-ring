package com.nanaring.app.util

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

private const val PREFS_FILE         = "nana_ring_auth"
private const val KEY_ACCESS_TOKEN   = "access_token"
private const val KEY_REFRESH_TOKEN  = "refresh_token"
private const val KEY_USER_ID        = "user_id"
private const val KEY_TOKEN_EXPIRY   = "token_expiry"   // epoch seconds (Long)
private const val KEY_USER_ROLE      = "user_role"
private const val KEY_USER_FULL_NAME = "user_full_name"
private const val KEY_SERVER_URL     = "server_url"
private const val DEFAULT_URL        = "http://10.0.2.2/"

class AuthManager(context: Context) {

    private val prefs = EncryptedSharedPreferences.create(
        context,
        PREFS_FILE,
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    var accessToken: String
        get()      = prefs.getString(KEY_ACCESS_TOKEN, "") ?: ""
        set(value) { prefs.edit().putString(KEY_ACCESS_TOKEN, value).apply() }

    var refreshToken: String
        get()      = prefs.getString(KEY_REFRESH_TOKEN, "") ?: ""
        set(value) { prefs.edit().putString(KEY_REFRESH_TOKEN, value).apply() }

    var userId: String
        get()      = prefs.getString(KEY_USER_ID, "") ?: ""
        set(value) { prefs.edit().putString(KEY_USER_ID, value).apply() }

    /** Epoch seconds — Supabase returns `expires_at` as a Unix timestamp. */
    var tokenExpiry: Long
        get()      = prefs.getLong(KEY_TOKEN_EXPIRY, 0L)
        set(value) { prefs.edit().putLong(KEY_TOKEN_EXPIRY, value).apply() }

    var userRole: String
        get()      = prefs.getString(KEY_USER_ROLE, "") ?: ""
        set(value) { prefs.edit().putString(KEY_USER_ROLE, value).apply() }

    var userFullName: String
        get()      = prefs.getString(KEY_USER_FULL_NAME, "") ?: ""
        set(value) { prefs.edit().putString(KEY_USER_FULL_NAME, value).apply() }

    /** Kept for DeveloperSettingsActivity backward compat. */
    var serverUrl: String
        get()      = prefs.getString(KEY_SERVER_URL, DEFAULT_URL) ?: DEFAULT_URL
        set(value) { prefs.edit().putString(KEY_SERVER_URL, value).apply() }

    /** Alias so existing code referencing authManager.jwt still compiles. */
    var jwt: String
        get()      = accessToken
        set(value) { accessToken = value }

    /** True if we have a stored session (even if the access token expired — refresh will fix it). */
    fun isSignedIn(): Boolean = userId.isNotBlank() && refreshToken.isNotBlank()

    fun isConfigured(): Boolean = isSignedIn()

    fun clearSession() {
        prefs.edit()
            .remove(KEY_ACCESS_TOKEN)
            .remove(KEY_REFRESH_TOKEN)
            .remove(KEY_USER_ID)
            .remove(KEY_USER_ROLE)
            .remove(KEY_USER_FULL_NAME)
            .putLong(KEY_TOKEN_EXPIRY, 0L)
            .apply()
    }
}
