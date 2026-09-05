package com.example.aismartexpensetracker

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * v3 adds the `budgets` table and changes `expenses.amount` from TEXT to REAL.
 *
 * Versions 1 and 2 only ever existed on development machines -- the app has
 * never been released -- so those are dropped rather than migrated. From v3
 * onward every schema change needs a real Migration, because by then the
 * database holds automatically captured transactions the user cannot recreate.
 */
@Database(entities = [Expense::class, Budget::class], version = 3, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun expenseDao(): ExpenseDao
    abstract fun budgetDao(): BudgetDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "expense_database"
                )
                    // Only the pre-release dev schemas are discarded.
                    .fallbackToDestructiveMigrationFrom(1, 2)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
