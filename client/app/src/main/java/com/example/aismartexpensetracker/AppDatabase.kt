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
 * v5 adds `userId` to both `expenses` and `budgets` for per-user data isolation.
 *
 * Versions 1 and 2 only ever existed on development machines -- the app had
 * never been installed anywhere -- so those are dropped rather than migrated.
 * From v3 onward every schema change gets a real Migration, because by then
 * the database holds automatically captured transactions the user cannot
 * recreate. MIGRATION_3_4 and MIGRATION_4_5 preserve every existing row.
 */
@Database(entities = [Expense::class, Budget::class], version = 5, exportSchema = false)
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

        /**
         * Adds `userId` to both `expenses` and `budgets`.
         *
         * Old rows get an empty userId. Because sign-in clears local data
         * (user's choice), these rows will be removed on first login anyway.
         * The budgets table is recreated because SQLite does not support adding
         * a column to a composite primary key in place.
         */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE expenses ADD COLUMN userId TEXT NOT NULL DEFAULT ''")
                // Recreate budgets with the new composite PK (userId, category)
                db.execSQL(
                    """CREATE TABLE budgets_new (
                        userId TEXT NOT NULL DEFAULT '',
                        category TEXT NOT NULL,
                        monthlyLimit REAL NOT NULL,
                        PRIMARY KEY(userId, category)
                    )"""
                )
                db.execSQL(
                    "INSERT INTO budgets_new (userId, category, monthlyLimit) " +
                        "SELECT '', category, monthlyLimit FROM budgets"
                )
                db.execSQL("DROP TABLE budgets")
                db.execSQL("ALTER TABLE budgets_new RENAME TO budgets")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "expense_database"
                )
                    .addMigrations(MIGRATION_3_4, MIGRATION_4_5)
                    // Only the pre-release dev schemas are discarded.
                    .fallbackToDestructiveMigrationFrom(dropAllTables = true, 1, 2)
                    .build()
                INSTANCE = instance
                instance
            }
        }

    }
}
