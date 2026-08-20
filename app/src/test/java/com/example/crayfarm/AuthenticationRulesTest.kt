package com.example.crayfarm

import org.junit.Assert.assertEquals
import org.junit.Test

class AuthenticationRulesTest {
    @Test
    fun approvedOwnerRoutesToApp() {
        val owner = validUser(role = "admin")

        assertEquals(
            AuthenticatedProfileDestination.APP,
            determineAuthenticatedProfileDestination(owner)
        )
    }

    @Test
    fun approvedWorkerRoutesToApp() {
        val worker = validUser(role = "worker")

        assertEquals(
            AuthenticatedProfileDestination.APP,
            determineAuthenticatedProfileDestination(worker)
        )
    }

    @Test
    fun pendingOrUnlinkedWorkerRoutesToWaiting() {
        assertEquals(
            AuthenticatedProfileDestination.WAITING_FOR_APPROVAL,
            determineAuthenticatedProfileDestination(
                validUser(role = "worker").copy(approved = false)
            )
        )
        assertEquals(
            AuthenticatedProfileDestination.WAITING_FOR_APPROVAL,
            determineAuthenticatedProfileDestination(
                validUser(role = "worker").copy(farmId = "")
            )
        )
    }

    @Test
    fun invalidRoleIsRejectedInsteadOfReceivingAppAccess() {
        val unsupportedUser = validUser(role = "super_admin")

        assertEquals(
            AuthenticatedProfileDestination.INVALID_ROLE,
            determineAuthenticatedProfileDestination(unsupportedUser)
        )
    }

    @Test
    fun inactiveAccountIsRejectedBeforeRoleRouting() {
        val inactiveWorker = validUser(role = "worker").copy(
            active = false,
            approved = false
        )

        assertEquals(
            AuthenticatedProfileDestination.INACTIVE_ACCOUNT,
            determineAuthenticatedProfileDestination(inactiveWorker)
        )
    }

    @Test
    fun incompleteOwnerProfileIsRejected() {
        val incompleteOwner = validUser(role = "admin").copy(farmId = "")

        assertEquals(
            AuthenticatedProfileDestination.INVALID_OWNER_PROFILE,
            determineAuthenticatedProfileDestination(incompleteOwner)
        )
    }

    private fun validUser(role: String): CrayFarmUser {
        return CrayFarmUser(
            uid = "uid-1",
            fullName = "Test User",
            email = "user@crayfarm.test",
            role = role,
            farmId = "farm-1",
            approved = true,
            active = true
        )
    }
}
