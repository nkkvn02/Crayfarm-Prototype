package com.example.crayfarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Phase6UxRulesTest {
    private val employees = listOf(
        EmployeeSummary(
            uid = "active-1",
            fullName = "Maria Santos",
            email = "maria@crayfarm.test",
            employmentStatus = "active"
        ),
        EmployeeSummary(
            uid = "removed-1",
            fullName = "Juan Dela Cruz",
            email = "juan@crayfarm.test",
            employmentStatus = "removed"
        )
    )

    @Test
    fun filterEmployees_matchesNameEmailAndFriendlyStatus() {
        assertEquals("active-1", filterEmployees(employees, "MARIA").single().uid)
        assertEquals("removed-1", filterEmployees(employees, "juan@").single().uid)
        assertEquals("removed-1", filterEmployees(employees, "Removed").single().uid)
    }

    @Test
    fun filterEmployees_blankQueryKeepsAllEmployees() {
        assertEquals(employees, filterEmployees(employees, "  "))
    }

    @Test
    fun liveReadingFeedback_requiresAReadingNewerThanTheInitialSnapshot() {
        assertFalse(shouldShowLiveReadingFeedback(0L, 1_000L, true))
        assertFalse(shouldShowLiveReadingFeedback(1_000L, 1_000L, true))
        assertFalse(shouldShowLiveReadingFeedback(1_000L, 2_000L, false))
        assertTrue(shouldShowLiveReadingFeedback(1_000L, 2_000L, true))
    }
}
