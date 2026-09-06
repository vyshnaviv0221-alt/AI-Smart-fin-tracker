package com.example.aismartexpensetracker

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A user-set monthly spending limit for one category.
 *
 * Budgets are never seeded with invented defaults -- a category only has a
 * limit once the user sets one. The budgets screen lists the categories the
 * user has actually spent in and shows "no limit set" until then.
 */
@Entity(tableName = "budgets")
data class Budget(
    @PrimaryKey val category: String,
    val monthlyLimit: Double
)
