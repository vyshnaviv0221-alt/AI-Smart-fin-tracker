package com.example.aismartexpensetracker.cloud

import android.content.Context
import android.util.Log
import com.example.aismartexpensetracker.BuildConfig
import com.example.aismartexpensetracker.Expense
import com.google.gson.Gson
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/** Result of a cloud call, so callers can show a real message instead of guessing. */
sealed interface CloudResult<out T> {
    data class Ok<T>(val value: T) : CloudResult<T>
    data class Failed(val message: String) : CloudResult<Nothing>
    data object NotConfigured : CloudResult<Nothing>

    /**
     * The stored session could not be renewed and has been cleared.
     *
     * Distinct from [Failed] because it is the one failure the user can
     * actually act on, and because callers must stop showing themselves as
     * signed in -- a "Synced" badge over a dead session is worse than an error.
     */
    data object SessionExpired : CloudResult<Nothing>
}

/**
 * Supabase over its plain REST APIs (GoTrue for auth, PostgREST for data).
 *
 * Deliberately not the Supabase Kotlin SDK: this reuses the Retrofit/OkHttp
 * stack the app already has for the ML server, adds no dependencies, and has
 * no version-coupling to the SDK's module renames.
 *
 * Cloud sync is entirely optional. Every method degrades to NotConfigured or
 * Failed, and callers must keep working without it -- the app is fully usable
 * offline with Room as the source of truth.
 */
object SupabaseClient {

    private const val TAG = "SupabaseClient"

    private val url = BuildConfig.SUPABASE_URL.trimEnd('/')
    private val anonKey = BuildConfig.SUPABASE_ANON_KEY

    /** False when local.properties has no Supabase entries -- the app still runs. */
    val isConfigured: Boolean
        get() = url.isNotBlank() && anonKey.isNotBlank()

    private val gson = Gson()

    private val api: SupabaseApi? by lazy {
        if (!isConfigured) {
            Log.i(TAG, "Supabase not configured; cloud sync disabled.")
            return@lazy null
        }
        val client = OkHttpClient.Builder()
            .addInterceptor(
                HttpLoggingInterceptor().apply {
                    // BASIC, not BODY: BODY would print access tokens and
                    // transaction data into logcat.
                    level = HttpLoggingInterceptor.Level.BASIC
                }
            )
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()

        Retrofit.Builder()
            .baseUrl("$url/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(SupabaseApi::class.java)
    }

    private val timestampFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    // ---------------- auth ----------------

    suspend fun signUp(store: SessionStore, email: String, password: String): CloudResult<String> =
        authenticate(store) { it.signUp(anonKey, CredentialsRequest(email, password)) }

    suspend fun signIn(store: SessionStore, email: String, password: String): CloudResult<String> =
        authenticate(store) { it.signIn(anonKey, body = CredentialsRequest(email, password)) }

    private suspend fun authenticate(
        store: SessionStore,
        call: suspend (SupabaseApi) -> Response<AuthResponse>
    ): CloudResult<String> {
        val client = api ?: return CloudResult.NotConfigured
        return try {
            val response = call(client)
            val body = response.body()
            when {
                response.isSuccessful && body?.access_token != null -> {
                    store.save(body)
                    CloudResult.Ok(body.user?.email ?: "")
                }
                // Sign-up with email confirmation on returns 200 and no token.
                response.isSuccessful ->
                    CloudResult.Failed(
                        "Account created. Confirm your email address, then sign in."
                    )
                else -> CloudResult.Failed(readError(response.errorBody()?.string()))
            }
        } catch (e: Exception) {
            Log.w(TAG, "Auth call failed", e)
            CloudResult.Failed(e.message ?: "Could not reach Supabase")
        }
    }

    suspend fun signOut(store: SessionStore) {
        val client = api
        val token = store.accessToken
        // Clear locally first: the user is signed out on this device either way.
        store.clear()
        if (client != null && !token.isNullOrBlank()) {
            try {
                client.signOut(anonKey, "Bearer $token")
            } catch (e: Exception) {
                Log.i(TAG, "Remote sign-out failed; local session already cleared", e)
            }
        }
    }

    // ---------------- data ----------------

    /**
     * Pushes expenses to the `expenses` table. Upserts on (user_id, client_id)
     * where client_id is the row's stable syncId, so retries and re-syncs
     * update in place instead of duplicating.
     */
    suspend fun syncExpenses(store: SessionStore, expenses: List<Expense>): CloudResult<Int> {
        val client = api ?: return CloudResult.NotConfigured
        if (expenses.isEmpty()) return CloudResult.Ok(0)
        if (!store.isSignedIn) return CloudResult.Failed("Not signed in")

        val rows = expenses.map { expense ->
            RemoteExpense(
                merchant = expense.merchant,
                amount = expense.amount,
                category = expense.category,
                occurred_at = timestampFormat.format(java.util.Date(expense.date)),
                is_anomaly = expense.isAnomaly,
                client_id = expense.syncId
            )
        }

        // Refresh proactively when the token is about to expire...
        var token = usableToken(client, store) ?: return CloudResult.SessionExpired

        return try {
            var response = client.upsertExpenses(anonKey, "Bearer $token", rows)

            // ...and reactively if the server rejects it anyway. Clock skew and
            // a session revoked server-side both produce a 401 on a token this
            // device still believes is valid, so the expiry check alone is not
            // enough.
            if (response.code() == 401) {
                Log.i(TAG, "Access token rejected; refreshing and retrying once")
                token = refreshToken(client, store) ?: return CloudResult.SessionExpired
                response = client.upsertExpenses(anonKey, "Bearer $token", rows)
            }

            when {
                response.isSuccessful -> CloudResult.Ok(rows.size)
                response.code() == 401 -> {
                    store.clear()
                    CloudResult.SessionExpired
                }
                else -> CloudResult.Failed(readError(response.errorBody()?.string()))
            }
        } catch (e: Exception) {
            Log.w(TAG, "Sync failed", e)
            CloudResult.Failed(e.message ?: "Could not reach Supabase")
        }
    }

    // ---------------- token lifetime ----------------
    //
    // Supabase access tokens last one hour. Before this, the app stored the
    // refresh token and never used it: roughly an hour after sign-in every
    // write started failing with 401 while the UI still reported success.

    /** A token believed good, refreshing first if it is at or near expiry. */
    private suspend fun usableToken(client: SupabaseApi, store: SessionStore): String? =
        if (store.needsRefresh()) refreshToken(client, store) else store.accessToken

    /**
     * Exchanges the refresh token for a new access token.
     *
     * Returns null when the session is unrecoverable, and clears it in that
     * case so the app stops presenting itself as signed in. A network error is
     * NOT unrecoverable -- the session is kept so a later sync can succeed.
     */
    private suspend fun refreshToken(client: SupabaseApi, store: SessionStore): String? {
        val refresh = store.refreshToken
        if (refresh.isNullOrBlank()) {
            store.clear()
            return null
        }
        return try {
            val response = client.refresh(anonKey, body = RefreshRequest(refresh))
            val body = response.body()
            if (response.isSuccessful && body?.access_token != null) {
                store.save(body)
                body.access_token
            } else {
                Log.w(TAG, "Refresh rejected (${response.code()}); session cleared")
                store.clear()
                null
            }
        } catch (e: Exception) {
            // Offline. Keep the session: the token may still be inside its hour,
            // and discarding a valid refresh token would sign the user out for
            // nothing more than a dropped connection.
            Log.i(TAG, "Could not refresh (offline?); keeping session", e)
            store.accessToken
        }
    }

    private fun readError(raw: String?): String {
        if (raw.isNullOrBlank()) return "Request failed"
        return try {
            gson.fromJson(raw, AuthError::class.java)?.readable() ?: raw.take(200)
        } catch (e: Exception) {
            raw.take(200)
        }
    }
}
