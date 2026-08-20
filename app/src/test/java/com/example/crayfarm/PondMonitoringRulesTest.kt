package com.example.crayfarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PondMonitoringRulesTest {
    @Test
    fun deviceIsOnlineWithinConfiguredStaleTimeout() {
        val now = 1_000_000L

        assertEquals(
            DeviceConnectionState.ONLINE,
            determineDeviceConnectionState(
                lastSeen = now - DEVICE_STALE_TIMEOUT_MILLIS,
                now = now
            )
        )
    }

    @Test
    fun deviceBecomesOfflineAfterConfiguredStaleTimeout() {
        val now = 1_000_000L

        assertEquals(
            DeviceConnectionState.OFFLINE,
            determineDeviceConnectionState(
                lastSeen = now - DEVICE_STALE_TIMEOUT_MILLIS - 1L,
                now = now
            )
        )
    }

    @Test
    fun missingHeartbeatHasDedicatedState() {
        assertEquals(
            DeviceConnectionState.NO_HEARTBEAT,
            determineDeviceConnectionState(lastSeen = 0L, now = 1_000L)
        )
    }

    @Test
    fun sensorPresentationKeepsDeviceAndSensorFailuresSeparate() {
        assertEquals(
            SensorReadingPresentation.LIVE,
            determineSensorReadingPresentation(
                DeviceConnectionState.ONLINE,
                sensorConnected = true,
                hasValue = true
            )
        )
        assertEquals(
            SensorReadingPresentation.SENSOR_NOT_CONNECTED,
            determineSensorReadingPresentation(
                DeviceConnectionState.ONLINE,
                sensorConnected = false,
                hasValue = true
            )
        )
        assertEquals(
            SensorReadingPresentation.STALE,
            determineSensorReadingPresentation(
                DeviceConnectionState.OFFLINE,
                sensorConnected = true,
                hasValue = true
            )
        )
        assertEquals(
            SensorReadingPresentation.NO_DATA,
            determineSensorReadingPresentation(
                DeviceConnectionState.OFFLINE,
                sensorConnected = false,
                hasValue = false
            )
        )
    }

    @Test
    fun alertFiltersKeepAcknowledgementSeparateFromResolution() {
        val acknowledgedActive = PondAlert(
            severity = ALERT_SEVERITY_WARNING,
            acknowledged = true,
            resolved = false
        )
        val unacknowledgedResolved = PondAlert(
            severity = ALERT_SEVERITY_CRITICAL,
            acknowledged = false,
            resolved = true,
            resolvedAt = 10L
        )

        assertEquals(
            true,
            alertMatchesFilter(acknowledgedActive, AlertFilter.ACTIVE)
        )
        assertEquals(
            false,
            alertMatchesFilter(acknowledgedActive, AlertFilter.RESOLVED)
        )
        assertEquals(
            true,
            alertMatchesFilter(unacknowledgedResolved, AlertFilter.RESOLVED)
        )
        assertEquals(
            true,
            alertMatchesFilter(unacknowledgedResolved, AlertFilter.CRITICAL)
        )
    }

    @Test
    fun continuingAlertDoesNotCreateDuplicateUntilSeverityChanges() {
        assertEquals(
            AlertConditionAction.CREATE,
            determineAlertConditionAction("", "", ALERT_SEVERITY_WARNING)
        )
        assertEquals(
            AlertConditionAction.NO_CHANGE,
            determineAlertConditionAction(
                "alert-1",
                ALERT_SEVERITY_WARNING,
                ALERT_SEVERITY_WARNING
            )
        )
        assertEquals(
            AlertConditionAction.UPDATE,
            determineAlertConditionAction(
                "alert-1",
                ALERT_SEVERITY_WARNING,
                ALERT_SEVERITY_CRITICAL
            )
        )
    }

    @Test
    fun safeConnectedSensorResolvesAlertButDisconnectDoesNot() {
        val alert = PondAlert(type = "temperature_high")

        assertEquals(
            true,
            shouldResolveAlertCondition(
                alert = alert,
                sensorConnected = true,
                unsafeConditionTypes = emptySet()
            )
        )
        assertEquals(
            false,
            shouldResolveAlertCondition(
                alert = alert,
                sensorConnected = false,
                unsafeConditionTypes = emptySet()
            )
        )
        assertEquals(
            false,
            shouldResolveAlertCondition(
                alert = alert,
                sensorConnected = true,
                unsafeConditionTypes = setOf("temperature_high")
            )
        )
    }

    @Test
    fun severity_isWarningBelowCriticalDeviation() {
        assertEquals(
            ALERT_SEVERITY_WARNING,
            determineAlertSeverity(candidate("temperature", 31.9, 30.0))
        )
    }

    @Test
    fun severity_isCriticalAtBoundary() {
        assertEquals(
            ALERT_SEVERITY_CRITICAL,
            determineAlertSeverity(candidate("temperature", 32.0, 30.0))
        )
        assertEquals(
            ALERT_SEVERITY_CRITICAL,
            determineAlertSeverity(candidate("ph", 6.0, 6.5))
        )
    }

    @Test
    fun unknownSensorDefaultsToWarning() {
        assertEquals(
            ALERT_SEVERITY_WARNING,
            determineAlertSeverity(candidate("unknown", 1_000.0, 0.0))
        )
    }

    @Test
    fun rangeStatus_preservesSpecificLabels() {
        assertEquals(
            "Acidic",
            rangeStatusLabel(6.4, 6.5, 8.5, "Acidic", "Alkaline", 0.5)
        )
        assertEquals(
            "Alkaline",
            rangeStatusLabel(8.6, 6.5, 8.5, "Acidic", "Alkaline", 0.5)
        )
        assertEquals(
            "Critical",
            rangeStatusLabel(9.1, 6.5, 8.5, "Acidic", "Alkaline", 0.5)
        )
        assertEquals(
            "Safe",
            rangeStatusLabel(7.4, 6.5, 8.5, "Acidic", "Alkaline", 0.5)
        )
    }

    @Test
    fun maximumStatus_preservesHighLabel() {
        assertEquals("High", maximumStatusLabel(26.0, 25.0, "High", 20.0))
        assertEquals("Critical", maximumStatusLabel(46.0, 25.0, "High", 20.0))
    }

    @Test
    fun validThresholdsHaveNoValidationError() {
        assertNull(validatePondThresholds(PondThresholds()))
    }

    @Test
    fun invalidThresholdsReturnActionableErrors() {
        assertEquals(
            "Minimum temperature must be lower than maximum temperature.",
            validatePondThresholds(
                PondThresholds(minTemperature = 30.0, maxTemperature = 30.0)
            )
        )
        assertEquals(
            "pH thresholds must be between 0 and 14.",
            validatePondThresholds(PondThresholds(minPh = -1.0))
        )
    }

    @Test
    fun reportSummaryExcludesUnavailableReadingsAndCalculatesCompliance() {
        val records = listOf(
            PondHistoryRecord(
                temperature = 25.0,
                temperatureAvailable = true,
                ph = 7.0,
                phAvailable = true
            ),
            PondHistoryRecord(
                temperature = 35.0,
                temperatureAvailable = true,
                ph = 0.0,
                phAvailable = false
            )
        )

        val summary = calculatePondReportSummary(
            historyRecords = records,
            alerts = emptyList(),
            thresholds = PondThresholds()
        )
        val temperature = summary.sensorMetrics.first {
            it.sensor == AnalyticsSensor.TEMPERATURE
        }
        val ph = summary.sensorMetrics.first {
            it.sensor == AnalyticsSensor.PH
        }

        assertEquals(25.0, temperature.minimum, 0.001)
        assertEquals(30.0, temperature.average, 0.001)
        assertEquals(35.0, temperature.maximum, 0.001)
        assertEquals(50.0, temperature.compliancePercent, 0.001)
        assertEquals(100.0, temperature.availabilityPercent, 0.001)
        assertEquals(7.0, ph.average, 0.001)
        assertEquals(50.0, ph.availabilityPercent, 0.001)
        assertEquals(75.0, summary.overallAvailabilityPercent ?: 0.0, 0.001)
    }

    @Test
    fun reportSummaryCountsAlertLifecycleAndMostFrequentIssue() {
        val alerts = listOf(
            PondAlert(title = "High turbidity", severity = "WARNING"),
            PondAlert(title = "High turbidity", severity = "CRITICAL", resolved = true),
            PondAlert(title = "High TDS", severity = "WARNING", acknowledged = true)
        )

        val summary = calculatePondReportSummary(
            historyRecords = emptyList(),
            alerts = alerts,
            thresholds = PondThresholds()
        )

        assertEquals(2, summary.warningAlertCount)
        assertEquals(1, summary.criticalAlertCount)
        assertEquals(1, summary.resolvedAlertCount)
        assertEquals(1, summary.acknowledgedAlertCount)
        assertEquals("High turbidity", summary.mostFrequentIssue)
        assertEquals(2, summary.mostFrequentIssueCount)
    }

    @Test
    fun reportSummaryHasNoSensorMetricsWhenAllReadingsAreUnavailable() {
        val summary = calculatePondReportSummary(
            historyRecords = listOf(PondHistoryRecord()),
            alerts = emptyList(),
            thresholds = PondThresholds()
        )

        assertEquals(emptyList<SensorReportMetrics>(), summary.sensorMetrics)
        assertNull(summary.overallAvailabilityPercent)
    }

    @Test
    fun workerLifecycleRecognizesArchivedStatuses() {
        assertEquals(true, isFormerEmploymentStatus("removed"))
        assertEquals(true, isFormerEmploymentStatus("RESIGNED"))
        assertEquals(false, isFormerEmploymentStatus("active"))
        assertEquals("Removed", employmentStatusLabel("removed"))
        assertEquals("Resigned", employmentStatusLabel("resigned"))
        assertEquals("Active", employmentStatusLabel(""))
    }

    @Test
    fun workerLifecyclePreservesOriginalJoinDateWhenRestored() {
        assertEquals(100L, preservedJoinedAt(100L, 200L))
        assertEquals(200L, preservedJoinedAt(0L, 200L))
    }

    private fun candidate(
        sensor: String,
        value: Double,
        threshold: Double
    ): AlertCandidate {
        return AlertCandidate(
            type = "test",
            sensor = sensor,
            title = "Test",
            message = "Test",
            suggestedAction = "Test",
            value = value,
            threshold = threshold,
            unit = ""
        )
    }
}
