package com.example.aismartexpensetracker

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A captured expense.
 *
 * `amount` is a Double, not a String. It was previously stored as text, which
 * forced every screen to do `amount.toDoubleOrNull() ?: 0.0` and silently
 * turned any malformed row into zero rupees.
 */
@Entity(tableName = "expenses")
data class Expense(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val amount: Double,
    val merchant: String,
    val date: Long = System.currentTimeMillis(),
    val category: String = CategoryKeywords.UNCATEGORIZED,
    val isAnomaly: Boolean = false
)
