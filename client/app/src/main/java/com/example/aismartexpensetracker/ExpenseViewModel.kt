package com.example.aismartexpensetracker

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
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

/** Outcome of a manual add, so the UI can confirm or explain. */
sealed interface AddResult {
    data class Added(val merchant: String, val category: String) : AddResult
    data class Duplicate(val merchant: String) : AddResult
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

    /** Everything below derives from these two Flows. Nothing is hardcoded. */
    val expenses: StateFlow<List<Expense>> = dao.getAllExpenses()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val budgets: StateFlow<List<Budget>> = budgetDao.getAllBudgets()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * Start of the current calendar month, re-emitted periodically.
     *
     * Without the ticker, a session left open across a month boundary keeps
     * filtering against the old month until some other change arrives. The
     * interval is coarse because the value only changes once a month.
     */
    private val monthStart = flow {
        while (true) {
            emit(startOfCurrentMonth())
            delay(MONTH_CHECK_INTERVAL_MS)
        }
    }

    /**
     * Budgets are monthly, so spend is compared against the current month only.
     * Derived from `expenses` rather than collecting getAllExpenses() a second
     * time -- two collectors meant Room ran and delivered the same query twice.
     */
    val expensesThisMonth: StateFlow<List<Expense>> =
        combine(expenses, monthStart) { all, since -> all.filter { it.date >= since } }
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
            InsightEngine.build(statuses, monthExpenses)
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

    private val _addResult = MutableStateFlow<AddResult?>(null)
    val addResult: StateFlow<AddResult?> = _addResult

    // ---------------- actions ----------------

    /**
     * Manual entry (+ button). Same enrichment pipeline as automatic capture,
     * but deduplication is OFF: two identical taps mean two real purchases,
     * and silently dropping the second is invisible data loss.
     */
    fun addExpense(merchant: String, amount: Double) {
        viewModelScope.launch {
            _isCategorizing.value = true
            val result = ExpenseRepository.captureExpense(
                dao = dao,
                merchant = merchant,
                amount = amount,
                deduplicate = false
            )
            _addResult.value = when (result) {
                is CaptureResult.Saved -> AddResult.Added(merchant, result.category)
                CaptureResult.DuplicateIgnored -> AddResult.Duplicate(merchant)
            }
            _isCategorizing.value = false
        }
    }

    fun clearAddResult() { _addResult.value = null }

    /**
     * Human-in-the-Loop correction. Every screen reads the same Room Flow, so
     * the correction propagates immediately.
     */
    fun correctCategory(expenseId: Int, newCategory: String) {
        viewModelScope.launch {
            val before = dao.findById(expenseId)
            dao.updateCategory(expenseId, newCategory)

            // Send the verified label upstream so it becomes training data.
            // Only when the category actually changed -- re-picking the same
            // one is not new information.
            if (before != null && before.category != newCategory) {
                ExpenseRepository.reportCorrection(
                    merchant = before.merchant,
                    category = newCategory,
                    amount = before.amount
                )
            }
        }
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

            // Concurrently, not in sequence. There are up to ten categories, and
            // OkHttp's call timeout is 75s to survive a hosted cold start -- so
            // a serial loop against an unreachable server made the user watch a
            // spinner for up to 12 minutes before any error appeared. In
            // parallel the worst case is one timeout, and the common case is
            // one cold start instead of ten round trips.
            val outcomes = spendByCategory.map { entry ->
                async {
                    runCatching {
                        CategoryForecast(
                            entry.category,
                            entry.amount,
                            ExpenseRepository.fetchForecast(nextMonth, entry.category)
                        )
                    }
                }
            }.awaitAll()

            val results = outcomes.mapNotNull { it.getOrNull() }
            // One unknown category must not blank the whole screen; only report
            // a failure when nothing at all came back.
            val lastError = outcomes.firstOrNull { it.isFailure }
                ?.exceptionOrNull()
                ?.let { it.message ?: it::class.java.simpleName }

            _forecastState.value = if (results.isNotEmpty()) {
                ForecastState.Ready(nextMonth, results)
            } else {
                ForecastState.Error(
                    "Could not reach the prediction server.\n" +
                        "Start it by running start-server.bat in the project folder.\n" +
                        "It sets up the phone connection too." +
                        (lastError?.let { "\n\n($it)" } ?: "")
                )
            }
        }
    }

    // ---------------- helpers ----------------

    private companion object {
        /** Coarse: the month boundary only moves once a month. */
        const val MONTH_CHECK_INTERVAL_MS = 15 * 60 * 1000L
    }

    private fun startOfCurrentMonth(): Long = Calendar.getInstance().apply {
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

}
