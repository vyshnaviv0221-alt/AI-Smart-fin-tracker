package com.example.aismartexpensetracker

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.example.aismartexpensetracker.cloud.CloudResult
import com.example.aismartexpensetracker.cloud.SessionStore
import com.example.aismartexpensetracker.cloud.SupabaseClient
import java.util.Calendar

/** Spend in one category over the period being shown. */
data class CategoryTotal(val category: String, val amount: Double)

/** A category's spend against its user-set limit. `limit` is null until set. */
data class BudgetStatus(
    val category: String,
    val spent: Double,
    val limit: Double?
) {
    val ratio: Double? get() = limit?.takeIf { it > 0 }?.let { spent / it }
    val isOver: Boolean get() = (ratio ?: 0.0) > 1.0
    val isNear: Boolean get() = (ratio ?: 0.0) in 0.85..1.0
}

enum class InsightLevel { GOOD, WARNING, DANGER, NEUTRAL }

/** A generated recommendation. Derived from real data -- never a canned tip. */
data class Insight(
    val icon: String,
    val title: String,
    val message: String,
    val level: InsightLevel
)

data class ProfileStats(
    val transactionCount: Int,
    val categoriesUsed: Int,
    val totalSpentThisMonth: Double,
    val totalSpentAllTime: Double,
    val anomalyCount: Int,
    val budgetsSet: Int
)

/** One row on the predictions screen: spend so far vs what the model expects. */
data class CategoryForecast(
    val category: String,
    val spentSoFar: Double,
    val predicted: Double
)

/** Result of the most recent cloud action, for the UI to show. */
sealed interface CloudState {
    data object Idle : CloudState
    data object Busy : CloudState
    data class Message(val text: String, val isError: Boolean) : CloudState
}

sealed interface ForecastState {
    data object Idle : ForecastState
    data object Loading : ForecastState
    data class Ready(val month: Int, val forecasts: List<CategoryForecast>) : ForecastState
    data class Error(val message: String) : ForecastState
}

class ExpenseViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val dao = db.expenseDao()
    private val budgetDao = db.budgetDao()
    private val sessionStore = SessionStore(application)

    /** Everything below derives from these two Flows. Nothing is hardcoded. */
    val expenses: StateFlow<List<Expense>> = dao.getAllExpenses()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val budgets: StateFlow<List<Budget>> = budgetDao.getAllBudgets()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Budgets are monthly, so spend is compared against the current month only. */
    val expensesThisMonth: StateFlow<List<Expense>> = dao.getAllExpenses()
        .map { list -> list.filter { it.date >= startOfCurrentMonth() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val categoryTotals: StateFlow<List<CategoryTotal>> = expensesThisMonth
        .map { list ->
            list.groupBy { it.category }
                .map { (category, rows) -> CategoryTotal(category, rows.sumOf { it.amount }) }
                .filter { it.amount > 0.0 }
                .sortedByDescending { it.amount }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * One row per category the user has either spent in or set a limit for.
     * Categories with no activity and no limit are not invented.
     */
    val budgetStatuses: StateFlow<List<BudgetStatus>> =
        combine(categoryTotals, budgets) { totals, budgetList ->
            val limits = budgetList.associate { it.category to it.monthlyLimit }
            val spent = totals.associate { it.category to it.amount }
            (spent.keys + limits.keys)
                .map { category ->
                    BudgetStatus(category, spent[category] ?: 0.0, limits[category])
                }
                .sortedWith(
                    compareByDescending<BudgetStatus> { it.ratio ?: -1.0 }
                        .thenByDescending { it.spent }
                )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val insights: StateFlow<List<Insight>> =
        combine(budgetStatuses, expensesThisMonth) { statuses, monthExpenses ->
            buildInsights(statuses, monthExpenses)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val profileStats: StateFlow<ProfileStats> =
        combine(expenses, expensesThisMonth, budgets) { all, month, budgetList ->
            ProfileStats(
                transactionCount = all.size,
                categoriesUsed = all.map { it.category }.distinct().size,
                totalSpentThisMonth = month.sumOf { it.amount },
                totalSpentAllTime = all.sumOf { it.amount },
                anomalyCount = all.count { it.isAnomaly },
                budgetsSet = budgetList.size
            )
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            ProfileStats(0, 0, 0.0, 0.0, 0, 0)
        )

    private val _isCategorizing = MutableStateFlow(false)
    val isCategorizing: StateFlow<Boolean> = _isCategorizing

    private val _forecastState = MutableStateFlow<ForecastState>(ForecastState.Idle)
    val forecastState: StateFlow<ForecastState> = _forecastState

    private val _signedInEmail = MutableStateFlow(sessionStore.email)
    val signedInEmail: StateFlow<String?> = _signedInEmail

    private val _cloudState = MutableStateFlow<CloudState>(CloudState.Idle)
    val cloudState: StateFlow<CloudState> = _cloudState

    /** False when local.properties has no Supabase entries. */
    val cloudConfigured: Boolean get() = SupabaseClient.isConfigured

    // ---------------- actions ----------------

    /** Manual entry (+ button). Same pipeline as automatic notification capture. */
    fun addExpense(merchant: String, amount: Double) {
        viewModelScope.launch {
            _isCategorizing.value = true
            ExpenseRepository.captureExpense(dao, merchant, amount)
            _isCategorizing.value = false
        }
    }

    /**
     * Human-in-the-Loop correction. Every screen reads the same Room Flow, so
     * the correction propagates immediately.
     */
    fun correctCategory(expenseId: Int, newCategory: String) {
        viewModelScope.launch { dao.updateCategory(expenseId, newCategory) }
    }

    fun setBudget(category: String, monthlyLimit: Double) {
        viewModelScope.launch { budgetDao.setBudget(Budget(category, monthlyLimit)) }
    }

    fun clearBudget(category: String) {
        viewModelScope.launch { budgetDao.clearBudget(category) }
    }

    fun deleteExpense(expenseId: Int) {
        viewModelScope.launch { dao.deleteExpense(expenseId) }
    }

    /**
     * Asks the server to forecast next month for each category with real spend.
     * There is no on-device fallback for a trained regressor, so failure is
     * surfaced rather than hidden behind sample numbers.
     */
    fun loadForecasts() {
        viewModelScope.launch {
            _forecastState.value = ForecastState.Loading

            val spendByCategory = categoryTotals.value
                .filter { it.category != CategoryKeywords.UNCATEGORIZED }

            if (spendByCategory.isEmpty()) {
                _forecastState.value = ForecastState.Error(
                    "No categorized spending this month yet. Capture or add a few " +
                        "transactions and try again."
                )
                return@launch
            }

            // Calendar.MONTH is 0-based; the API expects 1-12.
            val nextMonth = (Calendar.getInstance().get(Calendar.MONTH) + 1) % 12 + 1

            val results = mutableListOf<CategoryForecast>()
            var lastError: String? = null

            for (entry in spendByCategory) {
                try {
                    val predicted = ExpenseRepository.fetchForecast(nextMonth, entry.category)
                    results += CategoryForecast(entry.category, entry.amount, predicted)
                } catch (e: Exception) {
                    // One unknown category must not blank the whole screen.
                    lastError = e.message ?: e::class.java.simpleName
                }
            }

            _forecastState.value = if (results.isNotEmpty()) {
                ForecastState.Ready(nextMonth, results)
            } else {
                ForecastState.Error(
                    "Could not reach the prediction server.\n" +
                        "Start it with: uvicorn app.main:app --port 8000\n" +
                        "On a phone, run: adb reverse tcp:8000 tcp:8000" +
                        (lastError?.let { "\n\n($it)" } ?: "")
                )
            }
        }
    }

    // ---------------- cloud (Supabase) ----------------

    fun signIn(email: String, password: String) = runAuth { 
        SupabaseClient.signIn(sessionStore, email, password)
    }

    fun signUp(email: String, password: String) = runAuth {
        SupabaseClient.signUp(sessionStore, email, password)
    }

    private fun runAuth(block: suspend () -> CloudResult<String>) {
        viewModelScope.launch {
            _cloudState.value = CloudState.Busy
            when (val result = block()) {
                is CloudResult.Ok -> {
                    _signedInEmail.value = sessionStore.email
                    _cloudState.value = CloudState.Message("Signed in", isError = false)
                    syncNow()
                }
                is CloudResult.Failed ->
                    _cloudState.value = CloudState.Message(result.message, isError = true)
                CloudResult.NotConfigured ->
                    _cloudState.value = CloudState.Message(
                        "Supabase is not configured. Add supabase.url and supabase.anonKey " +
                            "to client/local.properties, then rebuild.",
                        isError = true
                    )
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            SupabaseClient.signOut(sessionStore)
            _signedInEmail.value = null
            _cloudState.value = CloudState.Idle
        }
    }

    /** Pushes every local expense to Supabase. Upserts, so it is safe to repeat. */
    fun syncNow() {
        if (!sessionStore.isSignedIn) return
        viewModelScope.launch {
            _cloudState.value = CloudState.Busy
            _cloudState.value = when (val result = SupabaseClient.syncExpenses(sessionStore, expenses.value)) {
                is CloudResult.Ok -> CloudState.Message("Synced ${result.value} transactions", false)
                is CloudResult.Failed -> CloudState.Message(result.message, true)
                CloudResult.NotConfigured -> CloudState.Message("Supabase not configured", true)
            }
        }
    }

    fun clearCloudMessage() { _cloudState.value = CloudState.Idle }

    // ---------------- helpers ----------------

    private fun startOfCurrentMonth(): Long = Calendar.getInstance().apply {
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    /**
     * Rule-based recommendations computed from the user's own data.
     * Returns an empty list when there is nothing genuine to say -- the screen
     * shows an empty state rather than filler advice.
     */
    private fun buildInsights(
        statuses: List<BudgetStatus>,
        monthExpenses: List<Expense>
    ): List<Insight> {
        if (monthExpenses.isEmpty()) return emptyList()

        val insights = mutableListOf<Insight>()

        statuses.filter { it.isOver }.forEach { status ->
            val over = status.spent - (status.limit ?: 0.0)
            insights += Insight(
                icon = "⚠️",
                title = "${status.category} is over budget",
                message = "You've spent ₹${"%,.0f".format(status.spent)} against a " +
                    "₹${"%,.0f".format(status.limit)} limit — ₹${"%,.0f".format(over)} over.",
                level = InsightLevel.DANGER
            )
        }

        statuses.filter { it.isNear }.forEach { status ->
            val pct = ((status.ratio ?: 0.0) * 100).toInt()
            insights += Insight(
                icon = emojiForInsight(status.category),
                title = "${status.category} is nearing its limit",
                message = "At $pct% of your ₹${"%,.0f".format(status.limit)} limit " +
                    "with ₹${"%,.0f".format((status.limit ?: 0.0) - status.spent)} left.",
                level = InsightLevel.WARNING
            )
        }

        monthExpenses.filter { it.isAnomaly }.take(3).forEach { expense ->
            insights += Insight(
                icon = "🔍",
                title = "Unusual ${expense.category} transaction",
                message = "${expense.merchant} for ₹${"%,.0f".format(expense.amount)} " +
                    "is well outside your normal ${expense.category} spending. " +
                    "Tap it in Transactions if the category is wrong.",
                level = InsightLevel.WARNING
            )
        }

        val topCategory = statuses.maxByOrNull { it.spent }
        val monthTotal = monthExpenses.sumOf { it.amount }
        if (topCategory != null && monthTotal > 0) {
            val share = (topCategory.spent / monthTotal * 100).toInt()
            if (share >= 40) {
                insights += Insight(
                    icon = emojiForInsight(topCategory.category),
                    title = "${topCategory.category} dominates your spending",
                    message = "$share% of this month's ₹${"%,.0f".format(monthTotal)} " +
                        "went to ${topCategory.category}.",
                    level = InsightLevel.NEUTRAL
                )
            }
        }

        val withoutLimits = statuses.filter { it.limit == null && it.spent > 0 }
        if (withoutLimits.isNotEmpty()) {
            insights += Insight(
                icon = "🎯",
                title = "Set limits to get alerts",
                message = "No budget yet for " +
                    withoutLimits.take(3).joinToString(", ") { it.category } +
                    if (withoutLimits.size > 3) " and ${withoutLimits.size - 3} more." else ".",
                level = InsightLevel.NEUTRAL
            )
        }

        val onTrack = statuses.filter { it.limit != null && (it.ratio ?: 0.0) < 0.6 }
        if (onTrack.isNotEmpty() && insights.none { it.level == InsightLevel.DANGER }) {
            insights += Insight(
                icon = "✅",
                title = "On track",
                message = onTrack.take(3).joinToString(", ") { it.category } +
                    " ${if (onTrack.size == 1) "is" else "are"} comfortably within budget.",
                level = InsightLevel.GOOD
            )
        }

        return insights
    }

    private fun emojiForInsight(category: String): String = when (category) {
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
