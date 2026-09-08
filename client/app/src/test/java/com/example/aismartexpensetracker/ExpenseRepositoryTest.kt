package com.example.aismartexpensetracker

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Deduplication behaviour.
 *
 * This is the path that silently dropped a manual re-entry: the second of two
 * identical taps was suppressed and the UI showed nothing. The rule is that
 * automatic notification capture deduplicates (banks genuinely re-post the
 * same alert) and manual entry does not (two taps mean two purchases).
 *
 * The fake DAO keeps this on the JVM; the network call inside captureExpense
 * fails fast without a server and is caught, which is the offline path.
 */
class ExpenseRepositoryTest {

    private class FakeDao : ExpenseDao {
        val rows = mutableListOf<Expense>()
        var duplicateQueries = 0

        override suspend fun insertExpense(expense: Expense): Long {
            rows += expense.copy(id = rows.size + 1)
            return (rows.size).toLong()
        }

        override fun getAllExpenses(): Flow<List<Expense>> = flowOf(rows.toList())

        override suspend fun findById(id: Int): Expense? = rows.find { it.id == id }

        override suspend fun updateCategory(id: Int, category: String, now: Long) {
            rows.replaceAll { if (it.id == id) it.copy(category = category, updatedAt = now) else it }
        }

        override suspend fun updateAnomalyFlag(id: Int, isAnomaly: Boolean, now: Long) {
            rows.replaceAll { if (it.id == id) it.copy(isAnomaly = isAnomaly, updatedAt = now) else it }
        }

        override suspend fun deleteExpense(id: Int) {
            rows.removeAll { it.id == id }
        }

        override suspend fun countRecentDuplicates(
            merchant: String,
            amount: Double,
            since: Long
        ): Int {
            duplicateQueries++
            return rows.count {
                it.merchant == merchant &&
                    kotlin.math.abs(it.amount - amount) < 0.005 &&
                    it.date >= since
            }
        }
    }

    @Test
    fun `manual entry does not deduplicate - two coffees are two expenses`() = runBlocking {
        val dao = FakeDao()
        val first = ExpenseRepository.captureExpense(dao, "Coffee", 50.0, deduplicate = false)
        val second = ExpenseRepository.captureExpense(dao, "Coffee", 50.0, deduplicate = false)

        assertTrue(first is CaptureResult.Saved)
        assertTrue("the second manual add must not vanish", second is CaptureResult.Saved)
        assertEquals(2, dao.rows.size)
        assertEquals("dedup must not even be queried", 0, dao.duplicateQueries)
    }

    @Test
    fun `notification capture deduplicates a re-posted alert`() = runBlocking {
        val dao = FakeDao()
        val first = ExpenseRepository.captureExpense(dao, "Swiggy", 450.0, deduplicate = true)
        val second = ExpenseRepository.captureExpense(dao, "Swiggy", 450.0, deduplicate = true)

        assertTrue(first is CaptureResult.Saved)
        assertEquals(CaptureResult.DuplicateIgnored, second)
        assertEquals(1, dao.rows.size)
    }

    @Test
    fun `different amounts from the same merchant are both kept`() = runBlocking {
        val dao = FakeDao()
        ExpenseRepository.captureExpense(dao, "Swiggy", 450.0, deduplicate = true)
        ExpenseRepository.captureExpense(dao, "Swiggy", 300.0, deduplicate = true)
        assertEquals(2, dao.rows.size)
    }

    @Test
    fun `capture works offline and applies the on-device category`() = runBlocking {
        val dao = FakeDao()
        // No server is running in a unit test, so this exercises the offline path.
        val result = ExpenseRepository.captureExpense(dao, "Swiggy Bangalore", 450.0, deduplicate = false)

        assertTrue(result is CaptureResult.Saved)
        assertEquals("Food", (result as CaptureResult.Saved).category)
        assertEquals("Food", dao.rows.single().category)
    }

    @Test
    fun `every saved expense gets a distinct sync id`() = runBlocking {
        val dao = FakeDao()
        ExpenseRepository.captureExpense(dao, "Coffee", 50.0, deduplicate = false)
        ExpenseRepository.captureExpense(dao, "Coffee", 50.0, deduplicate = false)

        val ids = dao.rows.map { it.syncId }
        assertEquals("sync ids must be unique per row", ids.size, ids.toSet().size)
        assertTrue(ids.none { it.isBlank() })
    }

    /**
     * A DAO whose duplicate check suspends part-way through.
     *
     * This is what the real race looked like: a bank posts a notification and
     * then updates it, onNotificationPosted fires twice, and the two coroutines
     * both finished counting before either had inserted. The delay makes that
     * interleaving deterministic instead of relying on timing.
     */
    private class SlowCheckDao : ExpenseDao by FakeDao() {
        val inner = FakeDao()
        override suspend fun insertExpense(expense: Expense): Long = inner.insertExpense(expense)
        override suspend fun countRecentDuplicates(
            merchant: String, amount: Double, since: Long
        ): Int {
            val count = inner.rows.count { it.merchant == merchant && it.amount == amount }
            delay(20)   // the window the second coroutine used to slip through
            return count
        }
        override suspend fun updateCategory(id: Int, category: String, now: Long) =
            inner.updateCategory(id, category, now)
        override suspend fun updateAnomalyFlag(id: Int, isAnomaly: Boolean, now: Long) =
            inner.updateAnomalyFlag(id, isAnomaly, now)
    }

    @Test
    fun `concurrent identical captures insert only one row`() = runBlocking {
        // Regression guard. On a real device this produced twins 0ms, 9ms and
        // 74ms apart -- all inside the 60s window meant to stop them -- and the
        // user saw their spending total doubled.
        val dao = SlowCheckDao()
        listOf(
            async { ExpenseRepository.captureExpense(dao, "GAYATHRI UDAYAN", 130.0, deduplicate = true) },
            async { ExpenseRepository.captureExpense(dao, "GAYATHRI UDAYAN", 130.0, deduplicate = true) }
        ).awaitAll()

        assertEquals("a re-posted notification must not create a second row", 1, dao.inner.rows.size)
    }
}
