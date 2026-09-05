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
     * Stable identity for cloud sync.
     *
     * The row id cannot be used: it restarts at 1 whenever the local database
     * is recreated, so after a wipe-and-resync a new row would upsert over a
     * different transaction already stored under the same (user_id, client_id)
     * key in Supabase.
     */
    val syncId: String = UUID.randomUUID().toString(),

    /**
     * Last local modification. Bumped on insert and on every update, so sync
     * can send only what changed instead of the whole table each time.
     */
    val updatedAt: Long = System.currentTimeMillis()
)
