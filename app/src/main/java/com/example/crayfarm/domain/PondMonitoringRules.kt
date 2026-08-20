package com.example.crayfarm

import kotlin.math.abs

// Pure monitoring rules intentionally stay independent of Android and Firebase.
internal const val ALERT_SEVERITY_WARNING = "WARNING"
internal const val ALERT_SEVERITY_CRITICAL = "CRITICAL"
internal const val DEVICE_STALE_TIMEOUT_MILLIS = 60_000L
internal const val EMPLOYMENT_STATUS_ACTIVE = "active"
internal const val EMPLOYMENT_STATUS_REMOVED = "removed"
internal const val EMPLOYMENT_STATUS_RESIGNED = "resigned"

enum class DeviceConnectionState {
    ONLINE,
    OFFLINE,
    NO_HEARTBEAT
}

internal enum class SensorReadingPresentation {
    LIVE,
    SENSOR_NOT_CONNECTED,
    STALE,
    NO_DATA
}

internal fun determineDeviceConnectionState(
    lastSeen: Long,
    now: Long,
    staleTimeoutMillis: Long = DEVICE_STALE_TIMEOUT_MILLIS
): DeviceConnectionState {
    if (lastSeen <= 0L) {
        return DeviceConnectionState.NO_HEARTBEAT
    }

    val readingAge = (now - lastSeen).coerceAtLeast(0L)

    return if (readingAge <= staleTimeoutMillis) {
        DeviceConnectionState.ONLINE
    } else {
        DeviceConnectionState.OFFLINE
    }
}

internal fun determineSensorReadingPresentation(
    deviceState: DeviceConnectionState,
    sensorConnected: Boolean,
    hasValue: Boolean
): SensorReadingPresentation {
    return when (deviceState) {
        DeviceConnectionState.ONLINE -> when {
            !sensorConnected ->
                SensorReadingPresentation.SENSOR_NOT_CONNECTED

            hasValue -> SensorReadingPresentation.LIVE
            else -> SensorReadingPresentation.NO_DATA
        }

        DeviceConnectionState.OFFLINE,
        DeviceConnectionState.NO_HEARTBEAT -> if (hasValue) {
            SensorReadingPresentation.STALE
        } else {
            SensorReadingPresentation.NO_DATA
        }
    }
}

internal fun determineAlertSeverity(
    candidate: AlertCandidate
): String {
    val criticalDeviation = when (candidate.sensor) {
        "temperature" -> 2.0
        "ph" -> 0.5
        "turbidity" -> 20.0
        "tds" -> 100.0
        "waterLevel" -> 5.0
        else -> Double.POSITIVE_INFINITY
    }

    return if (
        abs(candidate.value - candidate.threshold) >= criticalDeviation
    ) {
        ALERT_SEVERITY_CRITICAL
    } else {
        ALERT_SEVERITY_WARNING
    }
}

internal fun alertMatchesFilter(
    alert: PondAlert,
    filter: AlertFilter
): Boolean {
    return when (filter) {
        AlertFilter.ALL -> true
        AlertFilter.ACTIVE -> !alert.resolved
        AlertFilter.WARNING ->
            alert.severity.equals(ALERT_SEVERITY_WARNING, ignoreCase = true)

        AlertFilter.CRITICAL ->
            alert.severity.equals(ALERT_SEVERITY_CRITICAL, ignoreCase = true)

        AlertFilter.RESOLVED -> alert.resolved
    }
}

internal enum class AlertConditionAction {
    CREATE,
    UPDATE,
    NO_CHANGE
}

internal fun determineAlertConditionAction(
    activeAlertId: String,
    currentSeverity: String,
    nextSeverity: String
): AlertConditionAction {
    return when {
        activeAlertId.isBlank() -> AlertConditionAction.CREATE
        currentSeverity.equals(nextSeverity, ignoreCase = true) ->
            AlertConditionAction.NO_CHANGE

        else -> AlertConditionAction.UPDATE
    }
}

internal fun shouldResolveAlertCondition(
    alert: PondAlert,
    sensorConnected: Boolean,
    unsafeConditionTypes: Set<String>
): Boolean {
    return !alert.resolved &&
            sensorConnected &&
            alert.type.isNotBlank() &&
            alert.type !in unsafeConditionTypes
}

internal fun rangeStatusLabel(
    value: Double,
    minimum: Double,
    maximum: Double,
    lowLabel: String,
    highLabel: String,
    criticalDeviation: Double
): String {
    return when {
        value < minimum -> lowLabel
        value > maximum + criticalDeviation -> "Critical"
        value > maximum -> highLabel
        else -> "Safe"
    }
}

internal fun maximumStatusLabel(
    value: Double,
    maximum: Double,
    highLabel: String,
    criticalDeviation: Double
): String {
    return when {
        value > maximum + criticalDeviation -> "Critical"
        value > maximum -> highLabel
        else -> "Safe"
    }
}

internal fun validatePondThresholds(
    thresholds: PondThresholds
): String? {
    return when {
        thresholds.minTemperature >= thresholds.maxTemperature -> {
            "Minimum temperature must be lower than maximum temperature."
        }

        thresholds.minPh >= thresholds.maxPh -> {
            "Minimum pH must be lower than maximum pH."
        }

        thresholds.minTds >= thresholds.maxTds -> {
            "Minimum TDS must be lower than maximum TDS."
        }

        thresholds.maxTurbidity < 0 -> {
            "Maximum turbidity cannot be negative."
        }

        thresholds.minWaterLevel < 0.0 ||
                thresholds.maxWaterLevel < 0.0 -> {
            "Water level thresholds cannot be negative."
        }

        thresholds.minWaterLevel >= thresholds.maxWaterLevel -> {
            "Minimum water level must be lower than maximum water level."
        }

        thresholds.minPh < 0 || thresholds.maxPh > 14 -> {
            "pH thresholds must be between 0 and 14."
        }

        else -> null
    }
}

internal fun isFormerEmploymentStatus(status: String): Boolean {
    return status.equals(EMPLOYMENT_STATUS_REMOVED, ignoreCase = true) ||
            status.equals(EMPLOYMENT_STATUS_RESIGNED, ignoreCase = true)
}

internal fun employmentStatusLabel(status: String): String {
    return when {
        status.equals(EMPLOYMENT_STATUS_REMOVED, ignoreCase = true) ->
            "Removed"
        status.equals(EMPLOYMENT_STATUS_RESIGNED, ignoreCase = true) ->
            "Resigned"
        else -> "Active"
    }
}

internal fun preservedJoinedAt(
    existingJoinedAt: Long,
    fallbackJoinedAt: Long
): Long {
    return existingJoinedAt.takeIf { timestamp -> timestamp > 0L }
        ?: fallbackJoinedAt
}

internal fun filterEmployees(
    employees: List<EmployeeSummary>,
    query: String
): List<EmployeeSummary> {
    val normalizedQuery = query.trim()

    if (normalizedQuery.isBlank()) {
        return employees
    }

    return employees.filter { employee ->
        employee.fullName.contains(normalizedQuery, ignoreCase = true) ||
                employee.email.contains(normalizedQuery, ignoreCase = true) ||
                employmentStatusLabel(employee.employmentStatus)
                    .contains(normalizedQuery, ignoreCase = true)
    }
}

internal fun shouldShowLiveReadingFeedback(
    previousTimestamp: Long,
    currentTimestamp: Long,
    hasAvailableReading: Boolean
): Boolean {
    return hasAvailableReading &&
            previousTimestamp > 0L &&
            currentTimestamp > previousTimestamp
}

internal fun calculatePondReportSummary(
    historyRecords: List<PondHistoryRecord>,
    alerts: List<PondAlert>,
    thresholds: PondThresholds
): PondReportSummary {
    val metrics = AnalyticsSensor.entries.mapNotNull { sensor ->
        val values = historyRecords.mapNotNull { record ->
            reportValue(record, sensor)
        }

        if (values.isEmpty()) {
            return@mapNotNull null
        }

        val compliantCount = values.count { value ->
            reportValueIsCompliant(sensor, value, thresholds)
        }

        SensorReportMetrics(
            sensor = sensor,
            sampleCount = values.size,
            totalRecordCount = historyRecords.size,
            minimum = values.minOrNull() ?: 0.0,
            average = values.average(),
            maximum = values.maxOrNull() ?: 0.0,
            compliancePercent = percentage(compliantCount, values.size),
            availabilityPercent = percentage(values.size, historyRecords.size)
        )
    }

    val issueCounts = alerts
        .groupingBy { alert -> reportIssueLabel(alert) }
        .eachCount()
    val mostFrequentIssue = issueCounts.entries
        .sortedWith(
            compareByDescending<Map.Entry<String, Int>> { entry -> entry.value }
                .thenBy { entry -> entry.key.lowercase() }
        )
        .firstOrNull()

    val availableSamples = metrics.sumOf { metric -> metric.sampleCount }
    val possibleSamples = historyRecords.size * metrics.size

    return PondReportSummary(
        historyRecordCount = historyRecords.size,
        alertCount = alerts.size,
        acknowledgedAlertCount = alerts.count { alert -> alert.acknowledged },
        warningAlertCount = alerts.count { alert ->
            alert.severity.equals(ALERT_SEVERITY_WARNING, ignoreCase = true)
        },
        criticalAlertCount = alerts.count { alert ->
            alert.severity.equals(ALERT_SEVERITY_CRITICAL, ignoreCase = true)
        },
        resolvedAlertCount = alerts.count { alert -> alert.resolved },
        overallAvailabilityPercent = if (possibleSamples > 0) {
            percentage(availableSamples, possibleSamples)
        } else {
            null
        },
        mostFrequentIssue = mostFrequentIssue?.key,
        mostFrequentIssueCount = mostFrequentIssue?.value ?: 0,
        sensorMetrics = metrics
    )
}

internal fun reportValue(
    record: PondHistoryRecord,
    sensor: AnalyticsSensor
): Double? {
    return when (sensor) {
        AnalyticsSensor.TEMPERATURE ->
            record.temperature.takeIf { record.temperatureAvailable }
        AnalyticsSensor.PH -> record.ph.takeIf { record.phAvailable }
        AnalyticsSensor.TURBIDITY ->
            record.turbidity.takeIf { record.turbidityAvailable }
        AnalyticsSensor.TDS -> record.tds.takeIf { record.tdsAvailable }
        AnalyticsSensor.WATER_LEVEL ->
            record.waterLevel.takeIf { record.waterLevelAvailable }
    }
}

private fun reportValueIsCompliant(
    sensor: AnalyticsSensor,
    value: Double,
    thresholds: PondThresholds
): Boolean {
    return when (sensor) {
        AnalyticsSensor.TEMPERATURE ->
            value in thresholds.minTemperature..thresholds.maxTemperature
        AnalyticsSensor.PH -> value in thresholds.minPh..thresholds.maxPh
        AnalyticsSensor.TURBIDITY -> value <= thresholds.maxTurbidity
        AnalyticsSensor.TDS -> value in thresholds.minTds..thresholds.maxTds
        AnalyticsSensor.WATER_LEVEL ->
            value in thresholds.minWaterLevel..thresholds.maxWaterLevel
    }
}

private fun percentage(part: Int, total: Int): Double {
    return if (total <= 0) {
        0.0
    } else {
        part.toDouble() / total.toDouble() * 100.0
    }
}

private fun reportIssueLabel(alert: PondAlert): String {
    return alert.title.ifBlank {
        alert.type.replace("_", " ").ifBlank { "Uncategorized alert" }
    }
}
