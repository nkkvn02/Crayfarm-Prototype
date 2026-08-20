package com.example.crayfarm

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

internal enum class ReportLoadPart {
    HISTORY,
    ALERTS,
    THRESHOLDS
}

internal fun interface ReportSubscription {
    fun close()
}

internal interface ReportRepository {
    fun observePondReport(
        farmId: String,
        pondId: String,
        onHistory: (List<PondHistoryRecord>) -> Unit,
        onAlerts: (List<PondAlert>) -> Unit,
        onThresholds: (PondThresholds) -> Unit,
        onError: (ReportLoadPart, String) -> Unit
    ): ReportSubscription
}

internal class FirebaseReportRepository(
    private val database: FirebaseDatabase = FirebaseDatabase.getInstance()
) : ReportRepository {
    override fun observePondReport(
        farmId: String,
        pondId: String,
        onHistory: (List<PondHistoryRecord>) -> Unit,
        onAlerts: (List<PondAlert>) -> Unit,
        onThresholds: (PondThresholds) -> Unit,
        onError: (ReportLoadPart, String) -> Unit
    ): ReportSubscription {
        val pondRef = database.getReference("farms")
            .child(farmId)
            .child("ponds")
            .child(pondId)
        val historyRef = pondRef.child("history")
        val alertsRef = pondRef.child("alerts")
        val thresholdsRef = pondRef.child("thresholds")

        val historyListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                onHistory(
                    snapshot.children.mapNotNull { child ->
                        child.toReportHistoryRecord()
                    }.sortedBy { record -> record.recordedAt }
                )
            }

            override fun onCancelled(error: DatabaseError) {
                onError(ReportLoadPart.HISTORY, error.message)
            }
        }

        val alertsListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                onAlerts(
                    snapshot.children.map { child ->
                        child.toReportAlert()
                    }.sortedByDescending { alert -> alert.createdAt }
                )
            }

            override fun onCancelled(error: DatabaseError) {
                onError(ReportLoadPart.ALERTS, error.message)
            }
        }

        val thresholdsListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                onThresholds(snapshot.toReportThresholds())
            }

            override fun onCancelled(error: DatabaseError) {
                onError(ReportLoadPart.THRESHOLDS, error.message)
            }
        }

        historyRef.addValueEventListener(historyListener)
        alertsRef.addValueEventListener(alertsListener)
        thresholdsRef.addValueEventListener(thresholdsListener)

        return ReportSubscription {
            historyRef.removeEventListener(historyListener)
            alertsRef.removeEventListener(alertsListener)
            thresholdsRef.removeEventListener(thresholdsListener)
        }
    }
}

private fun DataSnapshot.toReportHistoryRecord(): PondHistoryRecord? {
    val recordedAt = child("recordedAt").getValue(Long::class.java)
        ?: key?.toLongOrNull()
        ?: 0L
    if (recordedAt <= 0L) return null

    val temperature = reportNullableNumber("temperature")
    val ph = reportNullableNumber("ph")
    val turbidity = reportNullableNumber("turbidity")
    val tds = reportNullableNumber("tds")
    val waterLevel = reportNullableNumber("waterLevel")

    fun available(value: Double?, connectionKey: String): Boolean {
        return value != null && (
                child(connectionKey).getValue(Boolean::class.java) ?: true
                )
    }

    return PondHistoryRecord(
        id = key.orEmpty(),
        temperature = temperature ?: 0.0,
        temperatureAvailable = available(temperature, "temperatureConnected"),
        ph = ph ?: 0.0,
        phAvailable = available(ph, "phConnected"),
        turbidity = turbidity ?: 0.0,
        turbidityAvailable = available(turbidity, "turbidityConnected"),
        tds = tds ?: 0.0,
        tdsAvailable = available(tds, "tdsConnected"),
        waterLevel = waterLevel ?: 0.0,
        waterLevelAvailable = available(waterLevel, "waterLevelConnected"),
        recordedAt = recordedAt
    )
}

private fun DataSnapshot.toReportAlert(): PondAlert {
    return PondAlert(
        id = key.orEmpty(),
        type = reportString("type"),
        severity = reportString("severity").ifBlank { ALERT_SEVERITY_WARNING },
        sensor = reportString("sensor"),
        title = reportString("title"),
        message = reportString("message"),
        suggestedAction = reportString("suggestedAction"),
        value = reportNullableNumber("value") ?: 0.0,
        threshold = reportNullableNumber("threshold") ?: 0.0,
        unit = reportString("unit"),
        createdAt = child("createdAt").getValue(Long::class.java) ?: 0L,
        acknowledged = child("acknowledged")
            .getValue(Boolean::class.java) ?: false,
        acknowledgedAt = child("acknowledgedAt")
            .getValue(Long::class.java) ?: 0L,
        acknowledgedBy = reportString("acknowledgedBy"),
        resolved = child("resolved").getValue(Boolean::class.java) ?: false,
        resolvedAt = child("resolvedAt").getValue(Long::class.java) ?: 0L
    )
}

private fun DataSnapshot.toReportThresholds(): PondThresholds {
    fun number(key: String, defaultValue: Double): Double {
        return reportNullableNumber(key) ?: defaultValue
    }

    return PondThresholds(
        minTemperature = number("minTemperature", 20.0),
        maxTemperature = number("maxTemperature", 30.0),
        minPh = number("minPh", 6.5),
        maxPh = number("maxPh", 8.5),
        maxTurbidity = number("maxTurbidity", 25.0),
        minTds = number("minTds", 200.0),
        maxTds = number("maxTds", 500.0),
        minWaterLevel = if (child("maxWaterLevel").exists()) {
            number("minWaterLevel", 15.0)
        } else {
            15.0
        },
        maxWaterLevel = number("maxWaterLevel", 25.0)
    )
}

private fun DataSnapshot.reportNullableNumber(key: String): Double? {
    return (child(key).value as? Number)?.toDouble()
}

private fun DataSnapshot.reportString(key: String): String {
    return child(key).getValue(String::class.java).orEmpty()
}
