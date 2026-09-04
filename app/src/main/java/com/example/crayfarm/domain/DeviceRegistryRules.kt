package com.example.crayfarm

import java.util.Locale

internal const val DEVICE_STATUS_AVAILABLE = "available"
internal const val DEVICE_STATUS_ASSIGNED = "assigned"

internal data class DeviceRegistryState(
    val exists: Boolean,
    val deviceId: String = "",
    val active: Boolean? = null,
    val status: String = "",
    val farmId: String = "",
    val pondId: String = ""
)

internal enum class DeviceEligibility {
    AVAILABLE,
    NOT_REGISTERED,
    INACTIVE,
    ALREADY_ASSIGNED,
    INVALID_REGISTRY_RECORD
}

internal fun determineDeviceEligibility(
    requestedDeviceId: String,
    state: DeviceRegistryState
): DeviceEligibility {
    if (!state.exists) {
        return DeviceEligibility.NOT_REGISTERED
    }

    val normalizedRequestedId = requestedDeviceId
        .trim()
        .uppercase(Locale.ROOT)
    val normalizedRegisteredId = state.deviceId
        .trim()
        .uppercase(Locale.ROOT)
    val normalizedStatus = state.status
        .trim()
        .lowercase(Locale.ROOT)

    if (
        normalizedRegisteredId.isBlank() ||
        normalizedRegisteredId != normalizedRequestedId ||
        state.active == null ||
        normalizedStatus.isBlank()
    ) {
        return DeviceEligibility.INVALID_REGISTRY_RECORD
    }

    if (
        state.farmId.isNotBlank() ||
        state.pondId.isNotBlank() ||
        normalizedStatus == DEVICE_STATUS_ASSIGNED
    ) {
        return DeviceEligibility.ALREADY_ASSIGNED
    }

    if (!state.active || normalizedStatus != DEVICE_STATUS_AVAILABLE) {
        return DeviceEligibility.INACTIVE
    }

    return DeviceEligibility.AVAILABLE
}

internal fun DeviceEligibility.userMessage(): String = when (this) {
    DeviceEligibility.AVAILABLE -> ""
    DeviceEligibility.NOT_REGISTERED -> "Device not registered in CrayFarm."
    DeviceEligibility.INACTIVE -> "This device is not available for use."
    DeviceEligibility.ALREADY_ASSIGNED ->
        "This device is already linked to another pond."
    DeviceEligibility.INVALID_REGISTRY_RECORD ->
        "This device registry record is incomplete. Contact the system administrator."
}
