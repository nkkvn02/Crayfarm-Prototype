package com.example.crayfarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RegistrationRulesTest {
    @Test
    fun accountTypeMustBeSelected() {
        val errors = validInput(accountType = null)

        assertFalse(errors.isValid)
        assertEquals(
            "Choose Farm Owner or Farm Worker.",
            errors.accountType
        )
    }

    @Test
    fun farmNameIsRequiredOnlyForOwner() {
        val ownerErrors = validInput(
            accountType = EntryRole.OWNER,
            farmName = ""
        )
        val workerErrors = validInput(
            accountType = EntryRole.WORKER,
            farmName = ""
        )

        assertEquals("Enter your farm name.", ownerErrors.farmName)
        assertNull(workerErrors.farmName)
        assertTrue(workerErrors.isValid)
    }

    @Test
    fun invalidEmailAndPasswordMismatchAreInlineErrors() {
        val errors = validateUnifiedRegistration(
            fullName = "Maria Santos",
            accountType = EntryRole.WORKER,
            farmName = "",
            email = "not-an-email",
            password = "secret1",
            confirmPassword = "secret2"
        )

        assertEquals("Enter a valid email address.", errors.email)
        assertEquals("Passwords do not match.", errors.confirmPassword)
    }

    @Test
    fun validOwnerInputPassesValidation() {
        assertTrue(validInput(accountType = EntryRole.OWNER).isValid)
    }

    private fun validInput(
        accountType: EntryRole?,
        farmName: String = "CrayFarm One"
    ): UnifiedRegistrationErrors {
        return validateUnifiedRegistration(
            fullName = "Maria Santos",
            accountType = accountType,
            farmName = farmName,
            email = "maria@crayfarm.test",
            password = "secret1",
            confirmPassword = "secret1"
        )
    }
}
