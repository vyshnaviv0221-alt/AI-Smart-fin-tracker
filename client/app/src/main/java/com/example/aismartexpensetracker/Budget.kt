package com.example.aismartexpensetracker

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A user-set monthly spending limit for one category.
 *
 * Budgets are never seeded with invented defaults -- a category only has a
 * limit once the user sets one. The budgets screen lists the categories the
 * user has actually spent in and shows "no limit set" until then.
 *
 * `userId` scopes the budget to its owner so two accounts on the same device
 * do not share limits. `category` alone was the primary key before v5; now
 * the composite (userId, category) is unique, which is enforced by Room.
 */
@Entity(tableName = "budgets", primaryKeys = ["userId", "category"])
data class Budget(
    val userId: String,
    val category: String,
    val monthlyLimit: Double
)
