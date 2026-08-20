package com.example.crayfarm

internal data class UnifiedRegistrationErrors(
    val fullName: String? = null,
    val accountType: String? = null,
    val farmName: String? = null,
    val email: String? = null,
    val password: String? = null,
    val confirmPassword: String? = null
) {
    val isValid: Boolean
        get() = fullName == null &&
                accountType == null &&
                farmName == null &&
                email == null &&
                password == null &&
                confirmPassword == null
}

internal fun validateUnifiedRegistration(
    fullName: String,
    accountType: EntryRole?,
    farmName: String,
    email: String,
    password: String,
    confirmPassword: String
): UnifiedRegistrationErrors {
    val cleanEmail = email.trim()

    return UnifiedRegistrationErrors(
        fullName = if (fullName.trim().isBlank()) {
            "Enter your full name."
        } else {
            null
        },
        accountType = if (accountType == null) {
            "Choose Farm Owner or Farm Worker."
        } else {
            null
        },
        farmName = if (
            accountType == EntryRole.OWNER && farmName.trim().isBlank()
        ) {
            "Enter your farm name."
        } else {
            null
        },
        email = when {
            cleanEmail.isBlank() -> "Enter your email address."
            !cleanEmail.matches(
                Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
            ) -> "Enter a valid email address."
            else -> null
        },
        password = when {
            password.isBlank() -> "Enter your password."
            password.length < 6 -> "Use at least 6 characters."
            else -> null
        },
        confirmPassword = when {
            confirmPassword.isBlank() -> "Confirm your password."
            confirmPassword != password -> "Passwords do not match."
            else -> null
        }
    )
}
