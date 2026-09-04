package com.example.crayfarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceQrParserTest {
    @Test
    fun validCrayFarmQrReturnsNormalizedDeviceId() {
        assertEquals(
            DeviceQrParseResult.Success("CRAY-ESP32-001"),
            parseDeviceQrPayload("  CRAYFARM_DEVICE:cray-esp32-001  ")
        )
    }

    @Test
    fun randomQrIsRejected() {
        val result = parseDeviceQrPayload("https://example.com/device")

        assertTrue(result is DeviceQrParseResult.Error)
        assertEquals(
            "This QR code is not a CrayFarm device label.",
            (result as DeviceQrParseResult.Error).message
        )
    }

    @Test
    fun emptyAndMalformedDeviceIdsAreRejected() {
        assertTrue(
            parseDeviceQrPayload("CRAYFARM_DEVICE:") is DeviceQrParseResult.Error
        )
        assertTrue(
            parseDeviceQrPayload("CRAYFARM_DEVICE:BAD!") is DeviceQrParseResult.Error
        )
    }

    @Test
    fun manualEntryUsesTheSameLengthAndCharacterRules() {
        assertEquals("CRAY-ESP32_001", normalizeDeviceIdInput("cray-esp32_001!"))
        assertEquals(null, deviceIdValidationError("CRAY-ESP32_001"))
        assertTrue(deviceIdValidationError("SHORT") != null)
    }

    @Test
    fun onlyFarmOwnerRoleCanAddPonds() {
        assertTrue(canAddPond("admin"))
        assertFalse(canAddPond("worker"))
        assertFalse(canAddPond(""))
    }
}
