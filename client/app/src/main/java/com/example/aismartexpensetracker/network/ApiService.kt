package com.example.aismartexpensetracker.network

import retrofit2.http.Body
import retrofit2.http.POST

interface ApiService {
    @POST("categorize")
    suspend fun categorize(@Body request: TransactionRequest): CategoryResponse

    @POST("anomaly")
    suspend fun checkAnomaly(@Body request: TransactionRequest): AnomalyResponse

    @POST("predict")
    suspend fun predictExpense(@Body request: PredictionRequest): PredictionResponse

    /** Sends a human-verified category back so the model can learn from it. */
    @POST("feedback/correction")
    suspend fun sendCorrection(@Body request: CorrectionRequest): CorrectionResponse
}
