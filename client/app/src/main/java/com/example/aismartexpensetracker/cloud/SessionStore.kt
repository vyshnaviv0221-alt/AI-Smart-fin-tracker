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
@Suppress("DEPRECATION")  // androidx.security-crypto is deprecated wholesale
                          // with no drop-in replacement. Still the best
                          // available option for keeping the token off disk in
                          // plaintext; revisit if AndroidX ships a successor.
class SessionStore(context: Context) {

    private companion object {
        const val TAG = "SessionStore"
        const val FILE_ENCRYPTED = "supabase_session"
        const val FILE_PLAIN = "supabase_session_plain"
        const val KEY_ACCESS_TOKEN = "access_token"
        const val KEY_REFRESH_TOKEN = "refresh_token"
        const val KEY_EMAIL = "email"
        const val KEY_USER_ID = "user_id"
        const val KEY_LAST_SYNCED_AT = "last_synced_at"
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

    /**
     * `updatedAt` of the newest row confirmed uploaded. Sync sends only rows
     * changed after this, instead of the whole table every time.
     */
    var lastSyncedAt: Long
        get() = prefs.getLong(KEY_LAST_SYNCED_AT, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_SYNCED_AT, value).apply()

    fun save(response: AuthResponse) {
        prefs.edit()
            .putString(KEY_ACCESS_TOKEN, response.access_token)
            .putString(KEY_REFRESH_TOKEN, response.refresh_token)
            .putString(KEY_EMAIL, response.user?.email)
            .putString(KEY_USER_ID, response.user?.id)
            .apply()
    }

    /** Signing out drops the watermark too, so the next account syncs in full. */
    fun clear() = prefs.edit().clear().apply()
}
