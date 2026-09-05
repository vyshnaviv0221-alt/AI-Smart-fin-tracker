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

    @Query("UPDATE expenses SET category = :category WHERE id = :id")
    suspend fun updateCategory(id: Int, category: String)

    @Query("UPDATE expenses SET isAnomaly = :isAnomaly WHERE id = :id")
    suspend fun updateAnomalyFlag(id: Int, isAnomaly: Boolean)

    @Query("DELETE FROM expenses WHERE id = :id")
    suspend fun deleteExpense(id: Int)

    @Query("SELECT * FROM expenses WHERE id = :id")
    suspend fun findById(id: Int): Expense?

    /** Used to suppress duplicate captures when a bank posts the same alert twice. */
    @Query(
        "SELECT COUNT(*) FROM expenses WHERE merchant = :merchant AND amount = :amount AND date >= :since"
    )
    suspend fun countRecentDuplicates(merchant: String, amount: Double, since: Long): Int
}
