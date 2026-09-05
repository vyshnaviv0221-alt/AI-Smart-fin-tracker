package com.example.aismartexpensetracker

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseDao {

    @Insert
    suspend fun insertExpense(expense: Expense): Long

    @Query("SELECT * FROM expenses ORDER BY date DESC")
    fun getAllExpenses(): Flow<List<Expense>>

    @Query("SELECT * FROM expenses WHERE id = :id")
    suspend fun findById(id: Int): Expense?

    /**
     * Category and anomaly updates bump `updatedAt` so a corrected transaction
     * is picked up by the next incremental sync even though its date is old.
     */
    @Query("UPDATE expenses SET category = :category, updatedAt = :now WHERE id = :id")
    suspend fun updateCategory(id: Int, category: String, now: Long = System.currentTimeMillis())

    @Query("UPDATE expenses SET isAnomaly = :isAnomaly, updatedAt = :now WHERE id = :id")
    suspend fun updateAnomalyFlag(
        id: Int,
        isAnomaly: Boolean,
        now: Long = System.currentTimeMillis()
    )

    @Query("DELETE FROM expenses WHERE id = :id")
    suspend fun deleteExpense(id: Int)

    /** Rows changed since the last successful sync. */
    @Query("SELECT * FROM expenses WHERE updatedAt > :since ORDER BY updatedAt ASC LIMIT :limit")
    suspend fun getChangedSince(since: Long, limit: Int = 500): List<Expense>

    /**
     * Duplicate check for automatically captured notifications.
     *
     * The amount is compared with a half-paisa tolerance rather than `=`:
     * exact equality on a floating-point column is fragile, since two values
     * that print identically need not share the same bits.
     */
    @Query(
        """
        SELECT COUNT(*) FROM expenses
        WHERE merchant = :merchant
          AND ABS(amount - :amount) < 0.005
          AND date >= :since
        """
    )
    suspend fun countRecentDuplicates(merchant: String, amount: Double, since: Long): Int
}
