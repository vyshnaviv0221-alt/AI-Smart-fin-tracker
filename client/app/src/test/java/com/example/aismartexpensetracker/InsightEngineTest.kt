package com.example.aismartexpensetracker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Recommendation generation. Pure function, so it runs on the JVM.
 *
 * These exist because the Recommendations screen is one of the project's
 * headline claims and previously had no coverage at all -- the logic was a
 * private method on an AndroidViewModel and therefore untestable.
 */
class InsightEngineTest {

    private fun expense(
        merchant: String = "Swiggy",
        amount: Double = 100.0,
        category: String = "Food",
        anomaly: Boolean = false
    ) = Expense(
        id = 0,
        amount = amount,
        merchant = merchant,
        category = category,
        isAnomaly = anomaly
    )

    @Test
    fun `no expenses produces no advice rather than filler`() {
        val result = InsightEngine.build(
            statuses = listOf(BudgetStatus("Food", 0.0, 5000.0)),
            monthExpenses = emptyList()
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun `over budget is reported as danger with the overspend amount`() {
        val result = InsightEngine.build(
            statuses = listOf(BudgetStatus("Shopping", spent = 3540.0, limit = 3000.0)),
            monthExpenses = listOf(expense(category = "Shopping", amount = 3540.0))
        )
        val danger = result.single { it.level == InsightLevel.DANGER }
        assertTrue(danger.title.contains("Shopping"))
        assertTrue("should name the overspend", danger.message.contains("540"))
    }

    @Test
    fun `nearing the limit is a warning, not a danger`() {
        val result = InsightEngine.build(
            statuses = listOf(BudgetStatus("Bills", spent = 5880.0, limit = 6000.0)),
            monthExpenses = listOf(expense(category = "Bills", amount = 5880.0))
        )
        assertTrue(result.any { it.level == InsightLevel.WARNING })
        assertTrue(result.none { it.level == InsightLevel.DANGER })
    }

    @Test
    fun `a category with no limit prompts the user to set one`() {
        val result = InsightEngine.build(
            statuses = listOf(BudgetStatus("Travel", spent = 260.0, limit = null)),
            monthExpenses = listOf(expense(category = "Travel", amount = 260.0))
        )
        assertTrue(result.any { it.title.contains("Set limits") && it.message.contains("Travel") })
    }

    @Test
    fun `a dominant category is named with its share`() {
        val result = InsightEngine.build(
            statuses = listOf(
                BudgetStatus("Rent", spent = 800.0, limit = null),
                BudgetStatus("Food", spent = 200.0, limit = null)
            ),
            monthExpenses = listOf(
                expense(category = "Rent", amount = 800.0),
                expense(category = "Food", amount = 200.0)
            )
        )
        val dominant = result.single { it.title.contains("dominates") }
        assertTrue("80% of 1000", dominant.message.contains("80%"))
    }

    @Test
    fun `a category below the dominance threshold is not called out`() {
        val result = InsightEngine.build(
            statuses = listOf(
                BudgetStatus("Rent", spent = 300.0, limit = null),
                BudgetStatus("Food", spent = 700.0, limit = null)
            ),
            monthExpenses = listOf(
                expense(category = "Rent", amount = 300.0),
                expense(category = "Food", amount = 700.0)
            )
        )
        // Food is 70% so it IS dominant; Rent at 30% must not be.
        assertTrue(result.none { it.title.startsWith("Rent dominates") })
    }

    @Test
    fun `anomalies are surfaced but capped at three`() {
        val flagged = (1..5).map { expense(merchant = "Odd $it", amount = 9000.0, anomaly = true) }
        val result = InsightEngine.build(
            statuses = listOf(BudgetStatus("Food", 45000.0, null)),
            monthExpenses = flagged
        )
        assertEquals(3, result.count { it.title.startsWith("Unusual") })
    }

    @Test
    fun `on-track praise is suppressed while something is over budget`() {
        val result = InsightEngine.build(
            statuses = listOf(
                BudgetStatus("Shopping", spent = 4000.0, limit = 3000.0),  // over
                BudgetStatus("Food", spent = 100.0, limit = 5000.0)        // comfortable
            ),
            monthExpenses = listOf(
                expense(category = "Shopping", amount = 4000.0),
                expense(category = "Food", amount = 100.0)
            )
        )
        assertTrue(result.any { it.level == InsightLevel.DANGER })
        assertTrue(
            "don't congratulate while a budget is blown",
            result.none { it.level == InsightLevel.GOOD }
        )
    }

    @Test
    fun `on-track praise appears when nothing is over budget`() {
        val result = InsightEngine.build(
            statuses = listOf(BudgetStatus("Food", spent = 100.0, limit = 5000.0)),
            monthExpenses = listOf(expense(amount = 100.0))
        )
        assertTrue(result.any { it.level == InsightLevel.GOOD })
    }
}
