package com.example.aismartexpensetracker.cloud

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Stores the Supabase session.
 *
 * Uses EncryptedSharedPreferences so the access token is not sitting in
 * plaintext on the device. Keystore initialisation genuinely fails on some
 * devices and on emulators with a broken keystore, so there is a plain-prefs
 * fallback -- losing encryption is better than the app refusing to start, and
 * the token is short-lived either way.
 */
class SessionStore(context: Context) {

    private companion object {
        const val TAG = "SessionStore"
        const val FILE_ENCRYPTED = "supabase_session"
        const val FILE_PLAIN = "supabase_session_plain"
        const val KEY_ACCESS_TOKEN = "access_token"
        const val KEY_REFRESH_TOKEN = "refresh_token"
        const val KEY_EMAIL = "email"
        const val KEY_USER_ID = "user_id"
    }

    private val prefs: SharedPreferences = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            FILE_ENCRYPTED,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        Log.w(TAG, "Encrypted storage unavailable, falling back to plain prefs", e)
        context.getSharedPreferences(FILE_PLAIN, Context.MODE_PRIVATE)
    }

    val accessToken: String? get() = prefs.getString(KEY_ACCESS_TOKEN, null)
    val email: String? get() = prefs.getString(KEY_EMAIL, null)
    val userId: String? get() = prefs.getString(KEY_USER_ID, null)
    val isSignedIn: Boolean get() = !accessToken.isNullOrBlank()

    fun save(response: AuthResponse) {
        prefs.edit()
            .putString(KEY_ACCESS_TOKEN, response.access_token)
            .putString(KEY_REFRESH_TOKEN, response.refresh_token)
            .putString(KEY_EMAIL, response.user?.email)
            .putString(KEY_USER_ID, response.user?.id)
            .apply()
    }

    fun clear() = prefs.edit().clear().apply()
}
