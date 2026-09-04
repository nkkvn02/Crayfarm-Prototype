package com.example.crayfarm

import java.util.Locale

const val CRAYFARM_DEVICE_QR_PREFIX = "CRAYFARM_DEVICE:"
const val DEVICE_ID_MIN_LENGTH = 6
const val DEVICE_ID_MAX_LENGTH = 32

sealed class DeviceQrParseResult {
    data class Success(val deviceId: String) : DeviceQrParseResult()
    data class Error(val message: String) : DeviceQrParseResult()
}

fun normalizeDeviceIdInput(value: String): String {
    return value
        .uppercase(Locale.ROOT)
        .filter { character ->
            character.isLetterOrDigit() ||
                character == '-' ||
                character == '_'
        }
        .take(DEVICE_ID_MAX_LENGTH)
}

fun deviceIdValidationError(value: String): String? {
    val deviceId = value.trim()

    return when {
        deviceId.length !in DEVICE_ID_MIN_LENGTH..DEVICE_ID_MAX_LENGTH ->
            "Device ID must contain 6 to 32 characters."

        deviceId.any { character ->
            !character.isLetterOrDigit() &&
                character != '-' &&
                character != '_'
        } -> "Device ID can only use letters, numbers, hyphens, and underscores."

        else -> null
    }
}

fun parseDeviceQrPayload(payload: String?): DeviceQrParseResult {
    val cleanPayload = payload?.trim().orEmpty()

    if (!cleanPayload.startsWith(CRAYFARM_DEVICE_QR_PREFIX)) {
        return DeviceQrParseResult.Error(
            "This QR code is not a CrayFarm device label."
        )
    }

    val rawDeviceId = cleanPayload
        .removePrefix(CRAYFARM_DEVICE_QR_PREFIX)
        .trim()

    if (rawDeviceId.isEmpty()) {
        return DeviceQrParseResult.Error(
            "The CrayFarm QR code does not contain a Device ID."
        )
    }

    val validationError = deviceIdValidationError(rawDeviceId)
    if (validationError != null) {
        return DeviceQrParseResult.Error(validationError)
    }

    return DeviceQrParseResult.Success(
        deviceId = rawDeviceId.uppercase(Locale.ROOT)
    )
}

fun canAddPond(userRole: String): Boolean = userRole == "admin"
