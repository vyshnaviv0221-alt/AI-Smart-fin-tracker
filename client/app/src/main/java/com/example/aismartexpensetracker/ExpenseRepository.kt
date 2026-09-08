package com.example.aismartexpensetracker

import android.util.Log
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.example.aismartexpensetracker.network.ApiService
import com.example.aismartexpensetracker.network.CorrectionRequest
import com.example.aismartexpensetracker.network.PredictionRequest
import com.example.aismartexpensetracker.network.RetrofitClient
import com.example.aismartexpensetracker.network.TransactionRequest

/** Outcome of a capture, so callers can tell the user what actually happened. */
sealed interface CaptureResult {
    data class Saved(val id: Int, val category: String) : CaptureResult
    /** Suppressed as a repeat of a transaction captured moments ago. */
    data object DuplicateIgnored : CaptureResult
}

/**
 * Single place that knows how to save an expense AND enrich it with the
 * server's models. Both manual entry (ExpenseViewModel) and automatic
 * notification capture (ExpenseNotificationListener) go through here, so a
 * transaction is treated identically however it arrived.
 *
 * Enrichment order is deliberate:
 *   1. insert locally with an on-device keyword category  -> always works
 *   2. ask the server for a better category                -> overwrites if useful
 *   3. ask the server whether the amount is unusual        -> best effort
 *
 * Step 1 means the app is fully usable with the server down; steps 2-3 are
 * enhancements, not dependencies.
 */
object ExpenseRepository {

    private const val TAG = "ExpenseRepository"

    /**
     * Minimum server confidence to override the on-device keyword result.
     *
     * This is 0.30, not 0.50. With ten categories, chance is 0.10, and a
     * linear model spreads probability across classes: measured over 20 real
     * merchants, a 0.50 bar discarded 4 predictions (IRCTC 0.37, Blinkit 0.37,
     * Starbucks 0.45, BigBasket 0.46) that were all *correct*. A threshold
     * that throws away 20% of correct answers is too strict.
     */
    private const val MIN_SERVER_CONFIDENCE = 0.30

    /**
     * Banks and UPI apps routinely post the same alert more than once (and
     * Android re-delivers notifications on update). Identical merchant+amount
     * inside this window is treated as the same transaction.
     */
    private const val DUPLICATE_WINDOW_MS = 60_000L

    private val api: ApiService = RetrofitClient.apiService

    /**
     * Serialises the duplicate check and the insert that follows it.
     *
     * Without this the two are a check-then-act race. A bank posts a
     * notification and then updates it, so onNotificationPosted fires twice and
     * launches two independent coroutines on Dispatchers.IO. Both ran
     * countRecentDuplicates before either had inserted, both saw zero, and both
     * inserted -- observed on a real device with the twins landing 0ms, 9ms and
     * 74ms apart, all far inside the 60-second window that was supposed to
     * catch them. The visible damage is a doubled spending total.
     *
     * A mutex rather than a unique index because the rows are not identical:
     * they differ by timestamp and syncId, so there is no column set to
     * constrain on. The critical section is two fast local queries.
     */
    private val captureLock = Mutex()

    /**
     * @param deduplicate suppress a repeat of an identical recent capture.
     *   True for notifications, which genuinely arrive twice. **False for
     *   manual entry** -- if someone taps Add twice they mean two coffees, and
     *   silently dropping the second is data loss the user cannot see.
     */
    suspend fun captureExpense(
        dao: ExpenseDao,
        merchant: String,
        amount: Double,
        deduplicate: Boolean
    ): CaptureResult {
        val localCategory = CategoryKeywords.categorize(merchant)

        // The check and the insert must be atomic with respect to each other,
        // or a duplicate slips through between them. Enrichment below stays
        // outside the lock: it is network work and must not block capture.
        val newId = captureLock.withLock {
            if (deduplicate) {
                val since = System.currentTimeMillis() - DUPLICATE_WINDOW_MS
                if (dao.countRecentDuplicates(merchant, amount, since) > 0) {
                    Log.d(TAG, "Duplicate suppressed: $merchant / $amount")
                    return CaptureResult.DuplicateIgnored
                }
            }
            dao.insertExpense(
                Expense(amount = amount, merchant = merchant, category = localCategory)
            ).toInt()
        }
        Log.d(TAG, "Saved '$merchant' locally as $localCategory")

        var finalCategory = localCategory

        try {
            val request = TransactionRequest(merchant_text = merchant, amount = amount)

            val result = api.categorize(request)
            // Take the server's answer when it is reasonably confident, or
            // whenever the keyword list had no opinion at all -- but only if it
            // is a category this app actually knows. A retrained model can emit
            // a label the client has never heard of, and an unknown category
            // silently disappears from every chart, filter and budget that
            // enumerates ALL_CATEGORIES: the money would be logged and then
            // invisible. Falling back to the on-device guess is worse only in
            // accuracy, not in correctness.
            val keywordAbstained = localCategory == CategoryKeywords.UNCATEGORIZED
            val known = result.category in CategoryKeywords.ALL_CATEGORIES
            if (!known) {
                Log.w(TAG, "Server returned unknown category '${result.category}'; keeping $localCategory")
            }
            if (known && (result.confidence >= MIN_SERVER_CONFIDENCE || keywordAbstained)) {
                dao.updateCategory(newId, result.category)
                finalCategory = result.category
                Log.d(TAG, "Server refined -> ${result.category} (${result.confidence})")
            } else if (known) {
                Log.d(
                    TAG,
                    "Server confidence ${result.confidence} below threshold; keeping $localCategory"
                )
            }

            val anomaly = api.checkAnomaly(request)
            val isAnomaly = anomaly.status == "UNUSUAL"
            dao.updateAnomalyFlag(newId, isAnomaly)
            if (isAnomaly) Log.w(TAG, "Flagged as UNUSUAL: $merchant, $amount")
        } catch (e: Exception) {
            // Expected whenever the server isn't running. The expense is already
            // saved and categorized on-device, so there is nothing to recover.
            Log.i(TAG, "Server unreachable; keeping on-device category $localCategory (${e.message})")
        }

        return CaptureResult.Saved(newId, finalCategory)
    }

    /**
     * Reports a user's category correction to the server as training data.
     *
     * Best effort and deliberately silent: the correction is already saved in
     * Room, so the user's view is correct whether or not the server is
     * reachable. Failing to improve the model is not a reason to bother them.
     */
    suspend fun reportCorrection(merchant: String, category: String, amount: Double) {
        try {
            val response = api.sendCorrection(
                CorrectionRequest(merchant_text = merchant, category = category, amount = amount)
            )
            Log.d(TAG, "Correction sent: $merchant -> $category (${response.recorded} held)")
        } catch (e: Exception) {
            Log.i(TAG, "Could not send correction; it is still saved locally (${e.message})")
        }
    }

    /**
     * Asks the forecaster what [category] is expected to cost. `month` is part
     * of the API contract but the model does not consume it -- see the server's
     * predict_monthly_amount. Throws if the server is unreachable or rejects
     * the category; unlike categorization there is no on-device fallback for a
     * trained regressor, so the caller surfaces the failure.
     */
    suspend fun fetchForecast(month: Int, category: String): Double =
        api.predictExpense(PredictionRequest(month = month, category = category)).predicted_amount
}
