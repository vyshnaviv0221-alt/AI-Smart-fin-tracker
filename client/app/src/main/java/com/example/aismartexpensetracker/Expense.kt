package com.example.aismartexpensetracker

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * A captured expense.
 *
 * `amount` is a Double, not a String. It was previously stored as text, which
 * forced every screen to do `amount.toDoubleOrNull() ?: 0.0` and silently
 * turned any malformed row into zero rupees.
 *
 * Indexed on `date` because every read is `ORDER BY date DESC`.
 */
@Entity(
    tableName = "expenses",
    indices = [Index("date"), Index(value = ["syncId"], unique = true)]
)
data class Expense(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val amount: Double,
    val merchant: String,
    val date: Long = System.currentTimeMillis(),
    val category: String = CategoryKeywords.UNCATEGORIZED,
    val isAnomaly: Boolean = false,

    /**
     * Stable identity for a transaction, independent of the row id.
     *
     * Originally added for cloud sync, which has since been removed. It is
     * kept because dropping a uniquely-indexed column costs a Room migration
     * for no user-visible gain, and because a row id is not a safe identity:
     * ids restart at 1 whenever the database is recreated, so anything that
     * has to refer to a transaction across a wipe (an export, a backup file,
     * a future sync) needs this instead.
     */
    val syncId: String = UUID.randomUUID().toString(),

    /**
     * Last local modification. Bumped on insert and on every update.
     */
    val updatedAt: Long = System.currentTimeMillis()
)
