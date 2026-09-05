package com.example.aismartexpensetracker

import android.util.Log
import com.example.aismartexpensetracker.network.ApiService
import com.example.aismartexpensetracker.network.PredictionRequest
import com.example.aismartexpensetracker.network.RetrofitClient
import com.example.aismartexpensetracker.network.TransactionRequest

/**
 * Single place that knows how to save an expense AND enrich it with the
 * server's models. Both manual entry (ExpenseViewModel) and automatic
 * notification capture (ExpenseNotificationListener) go through here, so a
 * transaction is treated identically however it arrived.
 *
 * Enrichment order is deliberate:
 *   1. insert locally with an on-device keyword category  -> always works
 *   2. ask the server for a better category                -> overwrites if confident
 *   3. ask the server whether the amount is unusual        -> best effort
 *
 * Step 1 means the app is fully usable with the server down; steps 2-3 are
 * enhancements, not dependencies.
 */
object ExpenseRepository {

    private const val TAG = "ExpenseRepository"

    /**
     * Below this we keep the on-device keyword result: a low-confidence model
     * guess is not worth overriding an exact brand match like "swiggy".
     */
    private const val MIN_SERVER_CONFIDENCE = 0.50

    /**
     * Banks and UPI apps routinely post the same alert more than once (and
     * Android re-delivers notifications on update). Identical merchant+amount
     * inside this window is treated as the same transaction.
     */
    private const val DUPLICATE_WINDOW_MS = 60_000L

    private val api: ApiService = RetrofitClient.apiService

    /**
     * @return the new row id, or null if it was suppressed as a duplicate.
     */
    suspend fun captureExpense(
        dao: ExpenseDao,
        merchant: String,
        amount: Double,
        deduplicate: Boolean = true
    ): Int? {
        if (deduplicate) {
            val since = System.currentTimeMillis() - DUPLICATE_WINDOW_MS
            if (dao.countRecentDuplicates(merchant, amount, since) > 0) {
                Log.d(TAG, "Duplicate suppressed: $merchant / $amount")
                return null
            }
        }

        val localCategory = CategoryKeywords.categorize(merchant)
        val newId = dao.insertExpense(
            Expense(amount = amount, merchant = merchant, category = localCategory)
        ).toInt()
        Log.d(TAG, "Saved '$merchant' locally as $localCategory")

        try {
            val request = TransactionRequest(merchant_text = merchant, amount = amount)

            val categoryResult = api.categorize(request)
            if (categoryResult.confidence >= MIN_SERVER_CONFIDENCE) {
                dao.updateCategory(newId, categoryResult.category)
                Log.d(TAG, "Server refined -> ${categoryResult.category} (${categoryResult.confidence})")
            } else {
                Log.d(
                    TAG,
                    "Server confidence ${categoryResult.confidence} below threshold; keeping $localCategory"
                )
            }

            val anomalyResult = api.checkAnomaly(request)
            val isAnomaly = anomalyResult.status == "UNUSUAL"
            dao.updateAnomalyFlag(newId, isAnomaly)
            if (isAnomaly) Log.w(TAG, "Flagged as UNUSUAL: $merchant, $amount")
        } catch (e: Exception) {
            // Expected whenever the server isn't running. The expense is already
            // saved and categorized on-device, so there is nothing to recover.
            Log.i(TAG, "Server unreachable; keeping on-device category $localCategory (${e.message})")
        }

        return newId
    }

    /**
     * Asks the forecaster what [category] is expected to cost in [month].
     * Throws if the server is unreachable or rejects the category -- unlike
     * categorization there is no sensible on-device fallback for a trained
     * regressor, so the caller surfaces the failure.
     */
    suspend fun fetchForecast(month: Int, category: String): Double =
        api.predictExpense(PredictionRequest(month = month, category = category)).predicted_amount
}
