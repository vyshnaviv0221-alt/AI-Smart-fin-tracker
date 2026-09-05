package com.example.aismartexpensetracker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Budget maths. Pure data class, no Android dependencies, so this runs on the
 * JVM with `./gradlew test`.
 */
class BudgetStatusTest {

    @Test
    fun `no limit set means no ratio and never over budget`() {
        val status = BudgetStatus("Food", spent = 5000.0, limit = null)
        assertNull(status.ratio)
        assertFalse(status.isOver)
        assertFalse(status.isNear)
    }

    @Test
    fun `ratio is spent over limit`() {
        val status = BudgetStatus("Food", spent = 3000.0, limit = 6000.0)
        assertEquals(0.5, status.ratio!!, 0.0001)
    }

    @Test
    fun `over budget when spend exceeds the limit`() {
        val status = BudgetStatus("Shopping", spent = 3540.0, limit = 3000.0)
        assertTrue(status.isOver)
        assertFalse(status.isNear)
    }

    @Test
    fun `exactly at the limit is not over`() {
        val status = BudgetStatus("Bills", spent = 6000.0, limit = 6000.0)
        assertFalse(status.isOver)
        assertTrue(status.isNear)
    }

    @Test
    fun `near covers the last fifteen percent`() {
        assertTrue(BudgetStatus("Bills", 5100.0, 6000.0).isNear)   // 85%
        assertTrue(BudgetStatus("Bills", 5880.0, 6000.0).isNear)   // 98%
        assertFalse(BudgetStatus("Bills", 5000.0, 6000.0).isNear)  // 83%
    }

    @Test
    fun `a zero limit does not divide by zero`() {
        // Guarded by takeIf { it > 0 } -- a zero limit behaves like "unset"
        // rather than producing Infinity.
        val status = BudgetStatus("Rent", spent = 100.0, limit = 0.0)
        assertNull(status.ratio)
        assertFalse(status.isOver)
    }

    @Test
    fun `zero spend against a real limit is not near or over`() {
        val status = BudgetStatus("Travel", spent = 0.0, limit = 4000.0)
        assertEquals(0.0, status.ratio!!, 0.0001)
        assertFalse(status.isOver)
        assertFalse(status.isNear)
    }
}
