package com.example.aismartexpensetracker.cloud

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Query

// ---------- auth (GoTrue: /auth/v1) ----------

data class CredentialsRequest(val email: String, val password: String)

data class AuthUser(val id: String, val email: String?)

data class AuthResponse(
    val access_token: String?,
    val refresh_token: String?,
    val expires_in: Long?,
    val user: AuthUser?
)

/** GoTrue returns errors as JSON; these are the fields worth surfacing. */
data class AuthError(
    val error: String?,
    val error_description: String?,
    val msg: String?,
    val message: String?
) {
    fun readable(): String =
        error_description ?: msg ?: message ?: error ?: "Authentication failed"
}

// ---------- data (PostgREST: /rest/v1) ----------

/**
 * One row of the `expenses` table. `user_id` is filled in by a Postgres
 * default (auth.uid()), so the client never sends it -- that keeps row-level
 * security authoritative rather than trusting the client.
 */
data class RemoteExpense(
    val merchant: String,
    val amount: Double,
    val category: String,
    val occurred_at: String,
    val is_anomaly: Boolean,
    val client_id: Int
)

interface SupabaseApi {

    @Headers("Content-Type: application/json")
    @POST("auth/v1/signup")
    suspend fun signUp(
        @Header("apikey") apiKey: String,
        @Body body: CredentialsRequest
    ): Response<AuthResponse>

    @Headers("Content-Type: application/json")
    @POST("auth/v1/token")
    suspend fun signIn(
        @Header("apikey") apiKey: String,
        @Query("grant_type") grantType: String = "password",
        @Body body: CredentialsRequest
    ): Response<AuthResponse>

    @POST("auth/v1/logout")
    suspend fun signOut(
        @Header("apikey") apiKey: String,
        @Header("Authorization") bearer: String
    ): Response<Unit>

    @GET("auth/v1/user")
    suspend fun currentUser(
        @Header("apikey") apiKey: String,
        @Header("Authorization") bearer: String
    ): Response<AuthUser>

    /**
     * Upsert on the (user_id, client_id) unique constraint, so re-syncing the
     * same local row updates it instead of creating duplicates.
     */
    @Headers("Content-Type: application/json", "Prefer: resolution=merge-duplicates")
    @POST("rest/v1/expenses")
    suspend fun upsertExpenses(
        @Header("apikey") apiKey: String,
        @Header("Authorization") bearer: String,
        @Body rows: List<RemoteExpense>
    ): Response<Unit>
}
