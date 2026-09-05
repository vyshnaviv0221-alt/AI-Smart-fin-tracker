package com.example.aismartexpensetracker

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v3 added the `budgets` table and changed `expenses.amount` from TEXT to REAL.
 * v4 adds `syncId` + `updatedAt` and indexes `date`.
 *
 * Versions 1 and 2 only ever existed on development machines -- the app had
 * never been installed anywhere -- so those are dropped rather than migrated.
 * From v3 onward every schema change gets a real Migration, because by then
 * the database holds automatically captured transactions the user cannot
 * recreate. MIGRATION_3_4 is that: it preserves every existing row.
 */
@Database(entities = [Expense::class, Budget::class], version = 4, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun expenseDao(): ExpenseDao
    abstract fun budgetDao(): BudgetDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * Adds sync identity and change tracking without losing data.
         *
         * `syncId` is backfilled with a per-row value derived from the row id
         * (SQLite has no uuid()); it only has to be unique and stable, and
         * rows created after this point get a real UUID from the entity
         * default. `updatedAt` is seeded from `date` so the first incremental
         * sync sees existing rows as already-known rather than all-changed.
         */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE expenses ADD COLUMN syncId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE expenses ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE expenses SET syncId = 'local-' || id WHERE syncId = ''")
                db.execSQL("UPDATE expenses SET updatedAt = date WHERE updatedAt = 0")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_expenses_date ON expenses(date)")
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_expenses_syncId ON expenses(syncId)"
                )
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "expense_database"
                )
                    .addMigrations(MIGRATION_3_4)
                    // Only the pre-release dev schemas are discarded.
                    .fallbackToDestructiveMigrationFrom(dropAllTables = true, 1, 2)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
