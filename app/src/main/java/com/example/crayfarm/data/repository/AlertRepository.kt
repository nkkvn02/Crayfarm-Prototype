package com.example.crayfarm

import android.Manifest
import android.os.Build
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.util.Base64
import java.io.ByteArrayOutputStream
import android.util.Patterns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Agriculture
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Opacity
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.Water
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.MutableData
import com.google.firebase.database.Transaction
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal fun evaluateAndCreatePondAlerts(
    user: CrayFarmUser,
    pond: PondSummary,
    readings: PondReadings,
    connections: SensorConnectionState,
    thresholds: PondThresholds,
    existingAlerts: List<PondAlert>
) {
    val candidates = mutableListOf<AlertCandidate>()

    if (connections.temperature && readings.temperature < thresholds.minTemperature) {
        candidates.add(
            AlertCandidate(
                type = "temperature_low",
                sensor = "temperature",
                title = "Low Temperature",
                message =
                    "Temperature reached ${formatAlertValue(readings.temperature)}°C. The minimum safe value is ${formatAlertValue(thresholds.minTemperature)}°C.",
                suggestedAction =
                    "Check the water temperature, heater, pond cover, and surrounding weather conditions.",
                value = readings.temperature,
                threshold = thresholds.minTemperature,
                unit = "°C"
            )
        )
    }

    if (connections.temperature && readings.temperature > thresholds.maxTemperature) {
        candidates.add(
            AlertCandidate(
                type = "temperature_high",
                sensor = "temperature",
                title = "High Temperature",
                message =
                    "Temperature reached ${formatAlertValue(readings.temperature)}°C. The maximum safe value is ${formatAlertValue(thresholds.maxTemperature)}°C.",
                suggestedAction =
                    "Check pond shading, water circulation, aeration, and exposure to direct sunlight.",
                value = readings.temperature,
                threshold = thresholds.maxTemperature,
                unit = "°C"
            )
        )
    }

    if (connections.ph && readings.ph < thresholds.minPh) {
        candidates.add(
            AlertCandidate(
                type = "ph_low",
                sensor = "ph",
                title = "Acidic pH",
                message =
                    "pH reached ${formatAlertValue(readings.ph)}. The minimum safe value is ${formatAlertValue(thresholds.minPh)}.",
                suggestedAction =
                    "Test the water again, inspect possible contamination, and adjust alkalinity gradually.",
                value = readings.ph,
                threshold = thresholds.minPh,
                unit = "pH"
            )
        )
    }

    if (connections.ph && readings.ph > thresholds.maxPh) {
        candidates.add(
            AlertCandidate(
                type = "ph_high",
                sensor = "ph",
                title = "Alkaline pH",
                message =
                    "pH reached ${formatAlertValue(readings.ph)}. The maximum safe value is ${formatAlertValue(thresholds.maxPh)}.",
                suggestedAction =
                    "Test the water again and inspect recent water treatments or materials that may raise pH.",
                value = readings.ph,
                threshold = thresholds.maxPh,
                unit = "pH"
            )
        )
    }

    if (connections.turbidity && readings.turbidity > thresholds.maxTurbidity) {
        candidates.add(
            AlertCandidate(
                type = "turbidity_high",
                sensor = "turbidity",
                title = "High Turbidity",
                message =
                    "Relative turbidity reached ${formatAlertValue(readings.turbidity)}%. The maximum allowed value is ${formatAlertValue(thresholds.maxTurbidity)}%.",
                suggestedAction =
                    "Inspect waste buildup, uneaten feed, sediment, filtration, and water circulation.",
                value = readings.turbidity,
                threshold = thresholds.maxTurbidity,
                unit = "%"
            )
        )
    }

    if (connections.tds && readings.tds < thresholds.minTds) {
        candidates.add(
            AlertCandidate(
                type = "tds_low",
                sensor = "tds",
                title = "Low TDS",
                message =
                    "TDS reached ${formatAlertValue(readings.tds)} ppm. The minimum safe value is ${formatAlertValue(thresholds.minTds)} ppm.",
                suggestedAction =
                    "Verify the sensor reading and inspect recent water replacement or dilution.",
                value = readings.tds,
                threshold = thresholds.minTds,
                unit = "ppm"
            )
        )
    }

    if (connections.tds && readings.tds > thresholds.maxTds) {
        candidates.add(
            AlertCandidate(
                type = "tds_high",
                sensor = "tds",
                title = "High TDS",
                message =
                    "TDS reached ${formatAlertValue(readings.tds)} ppm. The maximum safe value is ${formatAlertValue(thresholds.maxTds)} ppm.",
                suggestedAction =
                    "Inspect dissolved waste and consider a gradual partial water replacement after verifying the sensor.",
                value = readings.tds,
                threshold = thresholds.maxTds,
                unit = "ppm"
            )
        )
    }

    if (connections.waterLevel) {
        if (readings.waterLevel < thresholds.minWaterLevel) {
            candidates.add(
                AlertCandidate(
                    type = "water_level_low",
                    sensor = "waterLevel",
                    title = "Low Water Level",
                    message =
                        "Water level reached ${formatAlertValue(readings.waterLevel)} cm. The minimum safe level is ${formatAlertValue(thresholds.minWaterLevel)} cm.",
                    suggestedAction =
                        "Check for leaks, evaporation, blocked supply lines, and refill the pond safely.",
                    value = readings.waterLevel,
                    threshold = thresholds.minWaterLevel,
                    unit = "cm"
                )
            )
        }

        if (readings.waterLevel > thresholds.maxWaterLevel) {
            candidates.add(
                AlertCandidate(
                    type = "water_level_high",
                    sensor = "waterLevel",
                    title = "High Water Level",
                    message =
                        "Water level reached ${formatAlertValue(readings.waterLevel)} cm. The maximum safe level is ${formatAlertValue(thresholds.maxWaterLevel)} cm.",
                    suggestedAction =
                        "Check the water supply, drainage, overflow path, and reduce the pond level safely if needed.",
                    value = readings.waterLevel,
                    threshold = thresholds.maxWaterLevel,
                    unit = "cm"
                )
            )
        }
    }

    val activeAlertByType = existingAlerts
        .asSequence()
        .filter { alert ->
            !alert.resolved && alert.id.isNotBlank() && alert.type.isNotBlank()
        }
        .groupBy { alert -> alert.type }
        .mapValues { (_, matchingAlerts) ->
            matchingAlerts.maxBy { alert -> alert.createdAt }
        }

    candidates.forEach { candidate ->
        createOrUpdateActivePondAlert(
            user = user,
            pond = pond,
            candidate = candidate,
            legacyActiveAlertId = activeAlertByType[candidate.type]?.id
        )
    }

    val unsafeTypes = candidates.mapTo(mutableSetOf()) { candidate ->
        candidate.type
    }

    val connectedSensors = buildSet {
        if (connections.temperature) add("temperature")
        if (connections.ph) add("ph")
        if (connections.turbidity) add("turbidity")
        if (connections.tds) add("tds")
        if (connections.waterLevel) add("waterLevel")
    }

    activeAlertByType.values
        .filter { alert ->
            shouldResolveAlertCondition(
                alert = alert,
                sensorConnected = alert.sensor in connectedSensors,
                unsafeConditionTypes = unsafeTypes
            )
        }
        .forEach { alert ->
            resolvePondAlertCondition(
                user = user,
                pond = pond,
                conditionType = alert.type,
                legacyActiveAlertId = alert.id
            )
        }
}

internal fun createOrUpdateActivePondAlert(
    user: CrayFarmUser,
    pond: PondSummary,
    candidate: AlertCandidate,
    legacyActiveAlertId: String?
) {
    val database = FirebaseDatabase.getInstance().reference
    val now = System.currentTimeMillis()
    val severity = determineAlertSeverity(candidate)
    val pondRef = database
        .child("farms")
        .child(user.farmId)
        .child("ponds")
        .child(pond.pondId)
    val alertsRef = pondRef.child("alerts")
    val proposedAlertId = alertsRef.push().key ?: return
    val conditionRef = pondRef
        .child("alertConditionStates")
        .child(candidate.type)

    conditionRef.runTransaction(
        object : Transaction.Handler {
            override fun doTransaction(
                currentData: MutableData
            ): Transaction.Result {
                val currentActiveAlertId = currentData
                    .child("activeAlertId")
                    .getValue(String::class.java)
                    .orEmpty()
                val lastResolvedAlertId = currentData
                    .child("lastResolvedAlertId")
                    .getValue(String::class.java)
                    .orEmpty()
                val reusableLegacyId = legacyActiveAlertId
                    .orEmpty()
                    .takeUnless { alertId ->
                        alertId == lastResolvedAlertId
                    }
                    .orEmpty()
                val existingActiveAlertId = currentActiveAlertId
                    .ifBlank { reusableLegacyId }
                val activeAlertId = existingActiveAlertId
                    .ifBlank { proposedAlertId }
                val currentSeverity = currentData
                    .child("severity")
                    .getValue(String::class.java)
                    .orEmpty()

                if (
                    determineAlertConditionAction(
                        activeAlertId = existingActiveAlertId,
                        currentSeverity = currentSeverity,
                        nextSeverity = severity
                    ) == AlertConditionAction.NO_CHANGE
                ) {
                    return Transaction.abort()
                }

                currentData.value = mapOf(
                    "activeAlertId" to activeAlertId,
                    "status" to "active",
                    "severity" to severity,
                    "lastAlertAt" to now,
                    "lastValue" to candidate.value,
                    "lastResolvedAlertId" to lastResolvedAlertId
                )

                return Transaction.success(currentData)
            }

            override fun onComplete(
                error: DatabaseError?,
                committed: Boolean,
                currentData: DataSnapshot?
            ) {
                if (error != null || !committed) {
                    return
                }

                val activeAlertId = currentData
                    ?.child("activeAlertId")
                    ?.getValue(String::class.java)
                    .orEmpty()

                if (activeAlertId.isBlank()) {
                    return
                }

                val alertData = mutableMapOf<String, Any>(
                    "type" to candidate.type,
                    "severity" to severity,
                    "sensor" to candidate.sensor,
                    "title" to candidate.title,
                    "message" to candidate.message,
                    "suggestedAction" to candidate.suggestedAction,
                    "value" to candidate.value,
                    "threshold" to candidate.threshold,
                    "unit" to candidate.unit,
                    "pondId" to pond.pondId,
                    "pondName" to pond.pondName,
                    "lastObservedAt" to now,
                    "resolved" to false,
                    "resolvedAt" to 0L
                )

                if (activeAlertId == proposedAlertId) {
                    alertData["createdAt"] = now
                    alertData["acknowledged"] = false
                    alertData["acknowledgedAt"] = 0L
                    alertData["acknowledgedBy"] = ""
                }

                alertsRef
                    .child(activeAlertId)
                    .updateChildren(alertData)
            }
        }
    )
}

internal fun resolvePondAlertCondition(
    user: CrayFarmUser,
    pond: PondSummary,
    conditionType: String,
    legacyActiveAlertId: String
) {
    val database = FirebaseDatabase.getInstance().reference
    val now = System.currentTimeMillis()
    val pondRef = database
        .child("farms")
        .child(user.farmId)
        .child("ponds")
        .child(pond.pondId)
    val conditionRef = pondRef
        .child("alertConditionStates")
        .child(conditionType)

    conditionRef.runTransaction(
        object : Transaction.Handler {
            override fun doTransaction(
                currentData: MutableData
            ): Transaction.Result {
                val currentActiveAlertId = currentData
                    .child("activeAlertId")
                    .getValue(String::class.java)
                    .orEmpty()
                val previousResolvedAlertId = currentData
                    .child("lastResolvedAlertId")
                    .getValue(String::class.java)
                    .orEmpty()
                val alertId = currentActiveAlertId
                    .ifBlank { legacyActiveAlertId }

                if (
                    alertId.isBlank() ||
                    (
                        currentActiveAlertId.isBlank() &&
                        alertId == previousResolvedAlertId
                    )
                ) {
                    return Transaction.abort()
                }

                currentData.value = mapOf(
                    "activeAlertId" to "",
                    "status" to "resolved",
                    "severity" to currentData
                        .child("severity")
                        .getValue(String::class.java)
                        .orEmpty(),
                    "lastAlertAt" to (
                        (currentData.child("lastAlertAt").value as? Number)
                            ?.toLong()
                            ?: 0L
                        ),
                    "lastValue" to (
                        (currentData.child("lastValue").value as? Number)
                            ?.toDouble()
                            ?: 0.0
                        ),
                    "lastResolvedAlertId" to alertId,
                    "resolvedAt" to now
                )

                return Transaction.success(currentData)
            }

            override fun onComplete(
                error: DatabaseError?,
                committed: Boolean,
                currentData: DataSnapshot?
            ) {
                if (error != null || !committed) {
                    return
                }

                val resolvedAlertId = currentData
                    ?.child("lastResolvedAlertId")
                    ?.getValue(String::class.java)
                    .orEmpty()

                if (resolvedAlertId.isBlank()) {
                    return
                }

                pondRef
                    .child("alerts")
                    .child(resolvedAlertId)
                    .updateChildren(
                        mapOf<String, Any>(
                            "resolved" to true,
                            "resolvedAt" to now,
                            "lastObservedAt" to now
                        )
                    )
            }
        }
    )
}

internal fun acknowledgePondAlert(
    user: CrayFarmUser,
    pond: PondSummary,
    alert: PondAlert,
    onSuccess: () -> Unit,
    onError: (String) -> Unit
) {
    val acknowledgedAt = System.currentTimeMillis()

    val updates = mapOf<String, Any>(
        "acknowledged" to true,
        "acknowledgedAt" to acknowledgedAt,
        "acknowledgedBy" to user.uid,
        "acknowledgedByName" to user.fullName
    )

    FirebaseDatabase.getInstance()
        .getReference("farms")
        .child(user.farmId)
        .child("ponds")
        .child(pond.pondId)
        .child("alerts")
        .child(alert.id)
        .updateChildren(updates)
        .addOnSuccessListener {
            addActivityLog(
                farmId = user.farmId,
                type = "alert_acknowledged",
                title = "Alert acknowledged",
                message =
                    "${alert.title} for ${pond.pondName} was acknowledged by ${user.fullName}.",
                actorUid = user.uid,
                actorName = user.fullName
            )

            onSuccess()
        }
        .addOnFailureListener { exception ->
            onError(
                exception.message
                    ?: "Unable to acknowledge the alert."
            )
        }
}


