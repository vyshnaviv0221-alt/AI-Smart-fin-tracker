package com.example.aismartexpensetracker

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface BudgetDao {

    @Query("SELECT * FROM budgets WHERE userId = :userId")
    fun getAllBudgets(userId: String): Flow<List<Budget>>

    /** Insert or replace -- setting a limit twice for a category just updates it. */
    @Upsert
    suspend fun setBudget(budget: Budget)

    @Query("DELETE FROM budgets WHERE category = :category AND userId = :userId")
    suspend fun clearBudget(category: String, userId: String)

    /** Delete all budgets belonging to a user -- called on sign-out to clear data. */
    @Query("DELETE FROM budgets WHERE userId = :userId")
    suspend fun deleteAllForUser(userId: String)
}
