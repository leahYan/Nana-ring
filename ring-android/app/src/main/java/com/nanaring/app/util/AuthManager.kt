package com.nanaring.app.util

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

private const val PREFS_FILE   = "nana_ring_auth"
private const val KEY_JWT       = "jwt_token"
private const val KEY_SERVER_URL = "server_url"
private const val DEFAULT_URL   = "http://10.0.2.2/"   // Android emulator → host localhost

/**
 * Stores JWT and server URL in [EncryptedSharedPreferences].
 * Accessed via [com.nanaring.app.di.AppContainer].
 */
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

    var jwt: String
        get()      = prefs.getString(KEY_JWT, "") ?: ""
        set(value) = prefs.edit().putString(KEY_JWT, value).apply()

    var serverUrl: String
        get()      = prefs.getString(KEY_SERVER_URL, DEFAULT_URL) ?: DEFAULT_URL
        set(value) = prefs.edit().putString(KEY_SERVER_URL, value).apply()

    fun isConfigured(): Boolean = jwt.isNotBlank() && serverUrl.isNotBlank()
}
