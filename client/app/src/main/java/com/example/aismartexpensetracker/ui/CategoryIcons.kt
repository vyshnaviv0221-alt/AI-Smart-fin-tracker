package com.example.aismartexpensetracker.ui

import androidx.compose.ui.graphics.Color

/**
 * Emoji and colour shown for a category. Shared by the dashboard, transactions
 * list, analytics and budgets so a category looks the same everywhere.
 */
val CATEGORY_EMOJI: Map<String, String> = mapOf(
    "Food" to "🍔",
    "Groceries" to "🛒",
    "Travel" to "🚕",
    "Shopping" to "🛍️",
    "Bills" to "🧾",
    "Healthcare" to "🏥",
    "Entertainment" to "🎬",
    "Investment" to "📈",
    "Rent" to "🏠",
    "Transfer" to "💸",
    "Uncategorized" to "❓"
)

private val CATEGORY_COLOR: Map<String, Color> = mapOf(
    "Food" to Color(0xFF534AB7),
    "Groceries" to Color(0xFF1D9E75),
    "Travel" to Color(0xFF378ADD),
    "Shopping" to Color(0xFFD4537E),
    "Bills" to Color(0xFFD85A30),
    "Healthcare" to Color(0xFF00897B),
    "Entertainment" to Color(0xFF8E44AD),
    "Investment" to Color(0xFF2E7D32),
    "Rent" to Color(0xFF6D4C41),
    "Transfer" to Color(0xFF546E7A),
    "Uncategorized" to Color(0xFF9E9E9E)
)

fun emojiFor(category: String): String = CATEGORY_EMOJI[category] ?: "❓"

fun colorFor(category: String): Color = CATEGORY_COLOR[category] ?: Color(0xFF9E9E9E)
