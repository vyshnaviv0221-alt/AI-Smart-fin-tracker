package com.example.aismartexpensetracker.network

// These mirror server/app/schemas.py exactly -- keep both in sync if the
// server's request/response shape changes.

data class TransactionRequest(
    val merchant_text: String,
    val amount: Double
)

data class CategoryResponse(
    val category: String,
    val confidence: Double
)

data class AnomalyResponse(
    val amount: Double,
    val status: String // "normal" | "UNUSUAL"
)

data class PredictionRequest(
    val month: Int,
    val category: String
)

data class PredictionResponse(
    val category: String,
    val predicted_amount: Double
)

/**
 * A category the user corrected by hand.
 *
 * Sent to the server so it becomes training data. This is the project's only
 * source of real in-domain labels: a merchant string that actually arrived on
 * this phone, categorised by the person who made the purchase.
 */
data class CorrectionRequest(
    val merchant_text: String,
    val category: String,
    val amount: Double
)

data class CorrectionResponse(
    val recorded: Int,
    val for_this_category: Int,
    val message: String
)
