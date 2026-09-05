package com.example.aismartexpensetracker

/**
 * Turns a month of spending and the user's budgets into recommendations.
 *
 * Extracted from ExpenseViewModel so it can be unit-tested on the JVM: it is a
 * pure function of its inputs with no Android, Room or coroutine dependency.
 * Every card the Recommendations screen shows comes from here, and nothing is
 * canned -- when the data supports nothing, it returns an empty list and the
 * screen says so.
 */
object InsightEngine {

    /** Budget usage at or above this is "nearing the limit". */
    private const val NEAR_LIMIT = 0.85

    /** A category taking at least this share of the month is worth naming. */
    private const val DOMINANT_SHARE = 0.40

    /** Below this fraction of the limit a category counts as comfortably on track. */
    private const val ON_TRACK = 0.60

    private const val MAX_ANOMALIES = 3
    private const val MAX_NAMED_CATEGORIES = 3

    fun build(statuses: List<BudgetStatus>, monthExpenses: List<Expense>): List<Insight> {
        if (monthExpenses.isEmpty()) return emptyList()

        val insights = mutableListOf<Insight>()

        statuses.filter { it.isOver }.forEach { status ->
            val limit = status.limit ?: return@forEach
            insights += Insight(
                icon = "⚠️",
                title = "${status.category} is over budget",
                message = "Spent ${money(status.spent)} against a ${money(limit)} limit — " +
                    "${money(status.spent - limit)} over.",
                level = InsightLevel.DANGER
            )
        }

        statuses.filter { it.isNear }.forEach { status ->
            val limit = status.limit ?: return@forEach
            val pct = ((status.ratio ?: 0.0) * 100).toInt()
            insights += Insight(
                icon = emojiFor(status.category),
                title = "${status.category} is nearing its limit",
                message = "At $pct% of your ${money(limit)} limit with " +
                    "${money(limit - status.spent)} left.",
                level = InsightLevel.WARNING
            )
        }

        monthExpenses.filter { it.isAnomaly }.take(MAX_ANOMALIES).forEach { expense ->
            insights += Insight(
                icon = "🔍",
                title = "Unusual ${expense.category} transaction",
                message = "${expense.merchant} for ${money(expense.amount)} is well above " +
                    "your normal ${expense.category} spending. Tap it in Transactions " +
                    "if the category is wrong.",
                level = InsightLevel.WARNING
            )
        }

        val monthTotal = monthExpenses.sumOf { it.amount }
        val top = statuses.maxByOrNull { it.spent }
        if (top != null && monthTotal > 0 && top.spent / monthTotal >= DOMINANT_SHARE) {
            insights += Insight(
                icon = emojiFor(top.category),
                title = "${top.category} dominates your spending",
                message = "${(top.spent / monthTotal * 100).toInt()}% of this month's " +
                    "${money(monthTotal)} went to ${top.category}.",
                level = InsightLevel.NEUTRAL
            )
        }

        val unbudgeted = statuses.filter { it.limit == null && it.spent > 0 }
        if (unbudgeted.isNotEmpty()) {
            val named = unbudgeted.take(MAX_NAMED_CATEGORIES).joinToString(", ") { it.category }
            val rest = unbudgeted.size - MAX_NAMED_CATEGORIES
            insights += Insight(
                icon = "🎯",
                title = "Set limits to get alerts",
                message = "No budget yet for $named" +
                    if (rest > 0) " and $rest more." else ".",
                level = InsightLevel.NEUTRAL
            )
        }

        val onTrack = statuses.filter { it.limit != null && (it.ratio ?: 0.0) < ON_TRACK }
        if (onTrack.isNotEmpty() && insights.none { it.level == InsightLevel.DANGER }) {
            insights += Insight(
                icon = "✅",
                title = "On track",
                message = onTrack.take(MAX_NAMED_CATEGORIES).joinToString(", ") { it.category } +
                    " ${if (onTrack.size == 1) "is" else "are"} comfortably within budget.",
                level = InsightLevel.GOOD
            )
        }

        return insights
    }

    private fun money(amount: Double): String = "₹${"%,.0f".format(amount)}"

    private fun emojiFor(category: String): String = when (category) {
        "Food" -> "🍔"
        "Groceries" -> "🛒"
        "Travel" -> "🚕"
        "Shopping" -> "🛍️"
        "Bills" -> "🧾"
        "Healthcare" -> "🏥"
        "Entertainment" -> "🎬"
        "Investment" -> "📈"
        "Rent" -> "🏠"
        "Transfer" -> "💸"
        else -> "💰"
    }
}
