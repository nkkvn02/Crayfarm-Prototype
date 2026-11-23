package com.example.crayfarm

data class PondReadings(
    val temperature: Double = 0.0,
    val ph: Double = 0.0,
    val turbidity: Double = 0.0,
    val tds: Double = 0.0,
    val waterLevel: Double = 0.0
)

data class PondReadingAvailability(
    val temperature: Boolean = false,
    val ph: Boolean = false,
    val turbidity: Boolean = false,
    val tds: Boolean = false,
    val waterLevel: Boolean = false
)

data class DeviceHealthMetadata(
    val lastSeen: Long = 0L,
    val firmwareVersion: String = "",
    val wifiRssi: Int? = null,
    val uptimeMs: Long? = null
)

data class SensorConnectionState(
    val temperature: Boolean = false,
    val ph: Boolean = false,
    val turbidity: Boolean = false,
    val tds: Boolean = false,
    val waterLevel: Boolean = false
) {
    fun anyConnected(): Boolean {
        return temperature || ph || turbidity || tds || waterLevel
    }

    fun disconnectedCount(): Int {
        return listOf(
            temperature,
            ph,
            turbidity,
            tds,
            waterLevel
        ).count { connected -> !connected }
    }
}

data class PondHistoryRecord(
    val id: String = "",
    val temperature: Double = 0.0,
    val temperatureAvailable: Boolean = false,
    val ph: Double = 0.0,
    val phAvailable: Boolean = false,
    val turbidity: Double = 0.0,
    val turbidityAvailable: Boolean = false,
    val tds: Double = 0.0,
    val tdsAvailable: Boolean = false,
    val waterLevel: Double = 0.0,
    val waterLevelAvailable: Boolean = false,
    val recordedAt: Long = 0L
)

enum class AnalyticsRange(
    val label: String,
    val durationMillis: Long
) {
    DAY("24 Hours", 24L * 60L * 60L * 1000L),
    WEEK("7 Days", 7L * 24L * 60L * 60L * 1000L),
    MONTH("30 Days", 30L * 24L * 60L * 60L * 1000L)
}

enum class AnalyticsSensor(
    val label: String,
    val unit: String
) {
    TEMPERATURE("Temperature", "°C"),
    PH("pH", ""),
    TURBIDITY("Relative Turbidity", "%"),
    TDS("TDS", "ppm"),
    WATER_LEVEL("Water Level", "cm")
}

data class PondSummary(
    val pondId: String = "",
    val pondName: String = "",
    val deviceId: String = ""
)

data class PondThresholds(
    val minTemperature: Double = 20.0,
    val maxTemperature: Double = 30.0,
    val minPh: Double = 6.5,
    val maxPh: Double = 8.5,
    val maxTurbidity: Double = 25.0,
    val minTds: Double = 200.0,
    val maxTds: Double = 500.0,
    val minWaterLevel: Double = 15.0,
    val maxWaterLevel: Double = 25.0
)

data class SensorReportMetrics(
    val sensor: AnalyticsSensor,
    val sampleCount: Int,
    val totalRecordCount: Int,
    val minimum: Double,
    val average: Double,
    val maximum: Double,
    val compliancePercent: Double,
    val availabilityPercent: Double
)

data class PondReportSummary(
    val historyRecordCount: Int,
    val alertCount: Int,
    val acknowledgedAlertCount: Int,
    val warningAlertCount: Int,
    val criticalAlertCount: Int,
    val resolvedAlertCount: Int,
    val overallAvailabilityPercent: Double?,
    val mostFrequentIssue: String?,
    val mostFrequentIssueCount: Int,
    val sensorMetrics: List<SensorReportMetrics>
)

data class PondAlert(
    val id: String = "",
    val type: String = "",
    val severity: String = "WARNING",
    val sensor: String = "",
    val title: String = "",
    val message: String = "",
    val suggestedAction: String = "",
    val value: Double = 0.0,
    val threshold: Double = 0.0,
    val unit: String = "",
    val createdAt: Long = 0L,
    val acknowledged: Boolean = false,
    val acknowledgedAt: Long = 0L,
    val acknowledgedBy: String = "",
    val resolved: Boolean = false,
    val resolvedAt: Long = 0L
)

enum class AlertFilter(val label: String) {
    ALL("All"),
    ACTIVE("Active"),
    WARNING("Warning"),
    CRITICAL("Critical"),
    RESOLVED("Resolved")
}

data class AlertCandidate(
    val type: String,
    val sensor: String,
    val title: String,
    val message: String,
    val suggestedAction: String,
    val value: Double,
    val threshold: Double,
    val unit: String
)

data class CrayFarmUser(
    val uid: String = "",
    val fullName: String = "",
    val email: String = "",
    val role: String = "",
    val farmId: String = "",
    val requestCode: String = "",
    val previousFarmId: String = "",
    val employmentStatus: String = "",
    val approved: Boolean = false,
    val active: Boolean = false,
    val age: Int = 0,
    val address: String = "",
    val contactNumber: String = "",
    val birthday: String = ""
)

data class EmployeeRequest(
    val code: String = "",
    val workerUid: String = "",
    val workerName: String = "",
    val workerEmail: String = "",
    val status: String = "",
    val farmId: String = ""
)

data class EmployeeSummary(
    val uid: String = "",
    val fullName: String = "",
    val email: String = "",
    val age: Int = 0,
    val address: String = "",
    val contactNumber: String = "",
    val birthday: String = "",
    val employmentStatus: String = "active",
    val joinedAt: Long = 0L,
    val leftAt: Long = 0L
)

data class WorkerMembership(
    val uid: String = "",
    val role: String = "worker",
    val status: String = "active",
    val active: Boolean = true,
    val joinedAt: Long = 0L,
    val leftAt: Long = 0L,
    val fullName: String = "",
    val email: String = "",
    val age: Int = 0,
    val address: String = "",
    val contactNumber: String = "",
    val birthday: String = ""
)

data class FarmProfile(
    val farmId: String = "",
    val farmName: String = "",
    val description: String = "",
    val photoBase64: String = "",
    val ownerUid: String = ""
)

data class ActivityLog(
    val id: String = "",
    val type: String = "",
    val title: String = "",
    val message: String = "",
    val actorUid: String = "",
    val actorName: String = "",
    val targetUid: String = "",
    val targetName: String = "",
    val timestamp: Long = 0L
)

enum class AppPage {
    DASHBOARD,
    ANALYTICS,
    REPORTS,
    PROFILE_OR_EMPLOYEES,
    SETTINGS
}

enum class EntryRole {
    OWNER,
    WORKER
}

enum class AuthPage {
    LOGIN,
    FORGOT_PASSWORD,
    REGISTER,
    WAITING
}
