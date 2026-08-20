package com.example.crayfarm

internal enum class AuthenticatedProfileDestination {
    APP,
    WAITING_FOR_APPROVAL,
    INACTIVE_ACCOUNT,
    INVALID_ROLE,
    INVALID_OWNER_PROFILE
}

internal fun determineAuthenticatedProfileDestination(
    user: CrayFarmUser
): AuthenticatedProfileDestination {
    return when {
        !user.active -> AuthenticatedProfileDestination.INACTIVE_ACCOUNT

        user.role != "admin" && user.role != "worker" ->
            AuthenticatedProfileDestination.INVALID_ROLE

        user.role == "worker" &&
                (!user.approved || user.farmId.isBlank()) ->
            AuthenticatedProfileDestination.WAITING_FOR_APPROVAL

        user.role == "admin" &&
                (!user.approved || user.farmId.isBlank()) ->
            AuthenticatedProfileDestination.INVALID_OWNER_PROFILE

        else -> AuthenticatedProfileDestination.APP
    }
}

internal fun authenticatedProfileErrorMessage(
    destination: AuthenticatedProfileDestination
): String? {
    return when (destination) {
        AuthenticatedProfileDestination.INACTIVE_ACCOUNT ->
            "This account is currently inactive."

        AuthenticatedProfileDestination.INVALID_ROLE ->
            "This account has an invalid or unsupported role."

        AuthenticatedProfileDestination.INVALID_OWNER_PROFILE ->
            "The farm owner account is not configured correctly."

        AuthenticatedProfileDestination.APP,
        AuthenticatedProfileDestination.WAITING_FOR_APPROVAL -> null
    }
}
