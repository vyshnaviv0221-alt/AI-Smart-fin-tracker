package com.example.aismartexpensetracker

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface BudgetDao {

    @Query("SELECT * FROM budgets")
    fun getAllBudgets(): Flow<List<Budget>>

    /** Insert or replace -- setting a limit twice for a category just updates it. */
    @Upsert
    suspend fun setBudget(budget: Budget)

    @Query("DELETE FROM budgets WHERE category = :category")
    suspend fun clearBudget(category: String)
}
