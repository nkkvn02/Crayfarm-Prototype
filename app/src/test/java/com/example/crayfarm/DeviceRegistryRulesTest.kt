package com.example.crayfarm

import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceRegistryRulesTest {
    private val deviceId = "CRAY-X7K9_Q2M8-V4R6_N1P3"

    @Test
    fun missingDeviceIsNotRegistered() {
        assertEquals(
            DeviceEligibility.NOT_REGISTERED,
            determineDeviceEligibility(deviceId, DeviceRegistryState(exists = false))
        )
    }

    @Test
    fun activeAvailableRegisteredDeviceCanBeClaimed() {
        assertEquals(
            DeviceEligibility.AVAILABLE,
            determineDeviceEligibility(deviceId, availableDevice())
        )
    }

    @Test
    fun inactiveDeviceIsRejected() {
        assertEquals(
            DeviceEligibility.INACTIVE,
            determineDeviceEligibility(
                deviceId,
                availableDevice().copy(active = false)
            )
        )
    }

    @Test
    fun assignedDeviceIsRejected() {
        assertEquals(
            DeviceEligibility.ALREADY_ASSIGNED,
            determineDeviceEligibility(
                deviceId,
                availableDevice().copy(
                    status = DEVICE_STATUS_ASSIGNED,
                    farmId = "farm-1",
                    pondId = "pond-1"
                )
            )
        )
    }

    @Test
    fun mismatchedOrIncompleteRegistryRecordIsRejected() {
        assertEquals(
            DeviceEligibility.INVALID_REGISTRY_RECORD,
            determineDeviceEligibility(
                deviceId,
                availableDevice().copy(deviceId = "DIFFERENT-DEVICE")
            )
        )
        assertEquals(
            DeviceEligibility.INVALID_REGISTRY_RECORD,
            determineDeviceEligibility(
                deviceId,
                availableDevice().copy(status = "")
            )
        )
    }

    private fun availableDevice() = DeviceRegistryState(
        exists = true,
        deviceId = deviceId,
        active = true,
        status = DEVICE_STATUS_AVAILABLE
    )
}
