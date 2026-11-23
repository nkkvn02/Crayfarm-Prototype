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
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
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

private val Blue = Color(0xFF2639A6)
private val LightBlue = Color(0xFFC5CDD9)
private val SafeGreen = Color(0xFF16803B)
private val WarningOrange = Color(0xFFE76F00)

@Composable
fun PondDashboardContent(
    user: CrayFarmUser,
    pond: PondSummary,
    onBackToPonds: () -> Unit
) {
    val context = LocalContext.current

    // "Clear" only hides old alerts for this signed-in user
    // on this device. Firebase history is preserved for reports/audit.
    val dashboardPrefs = remember(user.uid) {
        context.getSharedPreferences(
            "crayfarm_dashboard_${user.uid}",
            android.content.Context.MODE_PRIVATE
        )
    }

    val alertsClearKey = remember(user.farmId, pond.pondId) {
        "alertsClearedAt_${user.farmId}_${pond.pondId}"
    }

    var alertsClearedAt by remember(
        user.uid,
        user.farmId,
        pond.pondId
    ) {
        mutableStateOf(
            dashboardPrefs.getLong(
                alertsClearKey,
                0L
            )
        )
    }

    var showClearAlertsConfirmation by remember {
        mutableStateOf(false)
    }

    var readings by remember { mutableStateOf(PondReadings()) }
    var readingAvailability by remember {
        mutableStateOf(PondReadingAvailability())
    }
    var sensorConnections by remember {
        mutableStateOf(SensorConnectionState())
    }
    var deviceHealth by remember {
        mutableStateOf(DeviceHealthMetadata())
    }
    var thresholds by remember { mutableStateOf(PondThresholds()) }
    var hasReadings by remember { mutableStateOf(false) }
    var hasThresholds by remember { mutableStateOf(false) }
    var currentTime by remember { mutableStateOf(System.currentTimeMillis()) }
    var previousReadingTimestamp by remember(pond.pondId) {
        mutableStateOf(0L)
    }
    var showNewReadingFeedback by remember(pond.pondId) {
        mutableStateOf(false)
    }
    var refreshRequest by remember(pond.pondId) { mutableStateOf(0) }
    var isPullRefreshing by remember(pond.pondId) {
        mutableStateOf(false)
    }
    var offlineNotificationSent by remember(
        user.farmId,
        pond.pondId
    ) {
        mutableStateOf(false)
    }

    LaunchedEffect(user.farmId, pond.pondId) {
        while (true) {
            currentTime = System.currentTimeMillis()
            delay(1_000)
        }
    }

    LaunchedEffect(deviceHealth.lastSeen, hasReadings) {
        val currentTimestamp = deviceHealth.lastSeen
        val shouldShowFeedback = shouldShowLiveReadingFeedback(
            previousTimestamp = previousReadingTimestamp,
            currentTimestamp = currentTimestamp,
            hasAvailableReading = hasReadings
        )

        if (currentTimestamp > previousReadingTimestamp) {
            previousReadingTimestamp = currentTimestamp
        }

        if (shouldShowFeedback) {
            showNewReadingFeedback = true
            delay(1_200)
            showNewReadingFeedback = false
        }
    }

    var alerts by remember {
        mutableStateOf<List<PondAlert>>(emptyList())
    }
    var lifecycleAlerts by remember {
        mutableStateOf<List<PondAlert>>(emptyList())
    }
    var alertsError by remember { mutableStateOf("") }
    var showAlertsPage by remember { mutableStateOf(false) }

    var thresholdSensorToEdit by remember(pond.pondId) {
        mutableStateOf<AnalyticsSensor?>(null)
    }

    val seenAlertIds = remember(pond.pondId) {
        mutableSetOf<String>()
    }

    var initialAlertsLoaded by remember(pond.pondId) {
        mutableStateOf(false)
    }

    val latestReadingsRef = remember(
        user.farmId,
        pond.pondId
    ) {
        FirebaseDatabase.getInstance()
            .getReference("farms")
            .child(user.farmId)
            .child("ponds")
            .child(pond.pondId)
            .child("latest")
    }

    val thresholdsRef = remember(
        user.farmId,
        pond.pondId
    ) {
        FirebaseDatabase.getInstance()
            .getReference("farms")
            .child(user.farmId)
            .child("ponds")
            .child(pond.pondId)
            .child("thresholds")
    }

    val alertsQuery = remember(
        user.farmId,
        pond.pondId
    ) {
        FirebaseDatabase.getInstance()
            .getReference("farms")
            .child(user.farmId)
            .child("ponds")
            .child(pond.pondId)
            .child("alerts")
            .orderByChild("createdAt")
            .limitToLast(100)
    }

    LaunchedEffect(refreshRequest) {
        if (refreshRequest == 0) {
            return@LaunchedEffect
        }

        isPullRefreshing = true

        // These one-time reads request a fresh server snapshot. The existing
        // Firebase listeners remain responsible for applying any new data.
        latestReadingsRef.get()
        thresholdsRef.get()
        alertsQuery.get()

        // Keep the indicator visible long enough to make the gesture clear,
        // even when Firebase responds immediately from its synchronized cache.
        delay(900)
        isPullRefreshing = false
    }

    DisposableEffect(latestReadingsRef) {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) {
                    readings = PondReadings()
                    readingAvailability = PondReadingAvailability()
                    sensorConnections = SensorConnectionState()
                    deviceHealth = DeviceHealthMetadata()
                    hasReadings = false
                    return
                }

                val lastSeen = snapshot
                    .readNullableNumber("lastSeen")
                    ?.toLong()
                    ?: snapshot
                        .readNullableNumber("updatedAt")
                        ?.toLong()
                    ?: 0L

                deviceHealth = DeviceHealthMetadata(
                    lastSeen = lastSeen,
                    firmwareVersion = snapshot
                        .child("firmwareVersion")
                        .getValue(String::class.java)
                        .orEmpty(),
                    wifiRssi = (snapshot.child("wifiRssi").value as? Number)
                        ?.toInt(),
                    uptimeMs = (snapshot.child("uptimeMs").value as? Number)
                        ?.toLong()
                )

                val temperature =
                    snapshot.readNullableNumber("temperature")
                val ph =
                    snapshot.readNullableNumber("ph")
                val turbidity =
                    snapshot.readNullableNumber("turbidity")
                val tds =
                    snapshot.readNullableNumber("tds")
                val waterLevel =
                    snapshot.readNullableNumber("waterLevel")

                val temperatureConnected =
                    snapshot.readNullableBoolean(
                        "temperatureConnected"
                    ) ?: (temperature != null)

                val phConnected =
                    snapshot.readNullableBoolean(
                        "phConnected"
                    ) ?: (ph != null)

                val turbidityConnected =
                    snapshot.readNullableBoolean(
                        "turbidityConnected"
                    ) ?: (turbidity != null)

                val tdsConnected =
                    snapshot.readNullableBoolean(
                        "tdsConnected"
                    ) ?: (tds != null)

                val waterLevelConnected =
                    snapshot.readNullableBoolean(
                        "waterLevelConnected"
                    ) ?: (waterLevel != null)

                val updatedAvailability = PondReadingAvailability(
                    temperature =
                        readingAvailability.temperature || temperature != null,
                    ph = readingAvailability.ph || ph != null,
                    turbidity =
                        readingAvailability.turbidity || turbidity != null,
                    tds = readingAvailability.tds || tds != null,
                    waterLevel =
                        readingAvailability.waterLevel || waterLevel != null
                )

                readings = PondReadings(
                    temperature = temperature ?: readings.temperature,
                    ph = ph ?: readings.ph,
                    turbidity = turbidity ?: readings.turbidity,
                    tds = tds ?: readings.tds,
                    waterLevel = waterLevel ?: readings.waterLevel
                )

                readingAvailability = updatedAvailability

                sensorConnections = SensorConnectionState(
                    temperature = temperatureConnected,
                    ph = phConnected,
                    turbidity = turbidityConnected,
                    tds = tdsConnected,
                    waterLevel = waterLevelConnected
                )

                hasReadings = listOf(
                    temperatureConnected && updatedAvailability.temperature,
                    phConnected && updatedAvailability.ph,
                    turbidityConnected && updatedAvailability.turbidity,
                    tdsConnected && updatedAvailability.tds,
                    waterLevelConnected && updatedAvailability.waterLevel
                ).any { available -> available }
            }

            override fun onCancelled(error: DatabaseError) {
                // Keep an already loaded snapshot visible. The freshness timer
                // will move it to stale instead of presenting cached data as live.
            }
        }

        latestReadingsRef.addValueEventListener(listener)

        onDispose {
            latestReadingsRef.removeEventListener(listener)
        }
    }

    DisposableEffect(thresholdsRef) {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                thresholds = PondThresholds(
                    minTemperature = snapshot.readNumberOrDefault(
                        "minTemperature",
                        20.0
                    ),
                    maxTemperature = snapshot.readNumberOrDefault(
                        "maxTemperature",
                        30.0
                    ),
                    minPh = snapshot.readNumberOrDefault(
                        "minPh",
                        6.5
                    ),
                    maxPh = snapshot.readNumberOrDefault(
                        "maxPh",
                        8.5
                    ),
                    maxTurbidity = snapshot.readNumberOrDefault(
                        "maxTurbidity",
                        25.0
                    ),
                    minTds = snapshot.readNumberOrDefault(
                        "minTds",
                        200.0
                    ),
                    maxTds = snapshot.readNumberOrDefault(
                        "maxTds",
                        500.0
                    ),
                    minWaterLevel = if (
                        snapshot.child("maxWaterLevel").exists()
                    ) {
                        snapshot.readNumberOrDefault(
                            "minWaterLevel",
                            15.0
                        )
                    } else {
                        // Legacy versions stored only a percentage minimum.
                        // Until the new cm thresholds are saved, use the new defaults.
                        15.0
                    },
                    maxWaterLevel = snapshot.readNumberOrDefault(
                        "maxWaterLevel",
                        25.0
                    )
                )

                hasThresholds = true
            }

            override fun onCancelled(error: DatabaseError) {
                hasThresholds = false
            }
        }

        thresholdsRef.addValueEventListener(listener)

        onDispose {
            thresholdsRef.removeEventListener(listener)
        }
    }

    DisposableEffect(alertsQuery, alertsClearedAt) {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val allLoadedAlerts = snapshot.children.map { child ->
                    PondAlert(
                        id = child.key.orEmpty(),
                        type = child.child("type")
                            .getValue(String::class.java)
                            .orEmpty(),
                        severity = child.child("severity")
                            .getValue(String::class.java)
                            ?: "WARNING",
                        sensor = child.child("sensor")
                            .getValue(String::class.java)
                            .orEmpty(),
                        title = child.child("title")
                            .getValue(String::class.java)
                            .orEmpty(),
                        message = child.child("message")
                            .getValue(String::class.java)
                            .orEmpty(),
                        suggestedAction = child.child("suggestedAction")
                            .getValue(String::class.java)
                            .orEmpty(),
                        value = child.readNumber("value"),
                        threshold = child.readNumber("threshold"),
                        unit = child.child("unit")
                            .getValue(String::class.java)
                            .orEmpty(),
                        createdAt = child.child("createdAt")
                            .getValue(Long::class.java)
                            ?: 0L,
                        acknowledged = child.child("acknowledged")
                            .getValue(Boolean::class.java)
                            ?: false,
                        acknowledgedAt = child.child("acknowledgedAt")
                            .getValue(Long::class.java)
                            ?: 0L,
                        acknowledgedBy = child.child("acknowledgedBy")
                            .getValue(String::class.java)
                            .orEmpty(),
                        resolved = child.child("resolved")
                            .getValue(Boolean::class.java)
                            ?: false,
                        resolvedAt = child.child("resolvedAt")
                            .getValue(Long::class.java)
                            ?: 0L
                    )
                }.sortedByDescending { alert ->
                        alert.createdAt
                    }

                val loadedAlerts = allLoadedAlerts.filter { alert ->
                    alert.createdAt > alertsClearedAt
                }

                if (initialAlertsLoaded) {
                    loadedAlerts.forEach { alert ->
                        if (
                            alert.id.isNotBlank() &&
                            alert.id !in seenAlertIds
                        ) {
                            CrayFarmNotificationHelper
                                .showAlertNotification(
                                    context = context,
                                    alertId = alert.id,
                                    farmId = user.farmId,
                                    pondId = pond.pondId,
                                    pondName = pond.pondName,
                                    title = "${alert.severity.ifBlank { "WARNING" }}: ${alert.title}",
                                    message = alert.message
                                )
                        }
                    }
                }

                seenAlertIds.clear()
                seenAlertIds.addAll(
                    loadedAlerts.map { alert ->
                        alert.id
                    }
                )

                initialAlertsLoaded = true
                lifecycleAlerts = allLoadedAlerts
                alerts = loadedAlerts
                alertsError = ""
            }

            override fun onCancelled(error: DatabaseError) {
                alertsError = error.message
            }
        }

        alertsQuery.addValueEventListener(listener)

        onDispose {
            alertsQuery.removeEventListener(listener)
        }
    }

    val deviceConnectionState = determineDeviceConnectionState(
        lastSeen = deviceHealth.lastSeen,
        now = currentTime
    )

    val esp32Online =
        deviceConnectionState == DeviceConnectionState.ONLINE

    LaunchedEffect(deviceConnectionState, deviceHealth.lastSeen) {
        if (deviceConnectionState == DeviceConnectionState.OFFLINE &&
            deviceHealth.lastSeen > 0L &&
            !offlineNotificationSent
        ) {
            CrayFarmNotificationHelper.showAlertNotification(
                context = context,
                alertId = "esp32_offline_${user.farmId}_${pond.pondId}",
                farmId = user.farmId,
                pondId = pond.pondId,
                pondName = pond.pondName,
                title = "ESP32 offline",
                message = "${pond.pondName} has not sent a reading for more than 60 seconds."
            )
            offlineNotificationSent = true
        } else if (esp32Online) {
            offlineNotificationSent = false
        }
    }

    LaunchedEffect(
        esp32Online,
        hasReadings,
        hasThresholds,
        readings,
        readingAvailability,
        sensorConnections,
        thresholds,
        lifecycleAlerts,
        initialAlertsLoaded,
        user.farmId,
        pond.pondId
    ) {
        if (
            esp32Online &&
            hasReadings &&
            hasThresholds &&
            initialAlertsLoaded
        ) {
            val alertConnections = SensorConnectionState(
                temperature = sensorConnections.temperature &&
                        readingAvailability.temperature,
                ph = sensorConnections.ph && readingAvailability.ph,
                turbidity = sensorConnections.turbidity &&
                        readingAvailability.turbidity,
                tds = sensorConnections.tds && readingAvailability.tds,
                waterLevel = sensorConnections.waterLevel &&
                        readingAvailability.waterLevel
            )

            evaluateAndCreatePondAlerts(
                user = user,
                pond = pond,
                readings = readings,
                connections = alertConnections,
                thresholds = thresholds,
                existingAlerts = lifecycleAlerts
            )
        }
    }

    val disconnectedStatus =
        "Sensor not connected" to Color.Gray

    val temperaturePresentation = determineSensorReadingPresentation(
        deviceState = deviceConnectionState,
        sensorConnected = sensorConnections.temperature,
        hasValue = readingAvailability.temperature
    )
    val phPresentation = determineSensorReadingPresentation(
        deviceState = deviceConnectionState,
        sensorConnected = sensorConnections.ph,
        hasValue = readingAvailability.ph
    )
    val turbidityPresentation = determineSensorReadingPresentation(
        deviceState = deviceConnectionState,
        sensorConnected = sensorConnections.turbidity,
        hasValue = readingAvailability.turbidity
    )
    val tdsPresentation = determineSensorReadingPresentation(
        deviceState = deviceConnectionState,
        sensorConnected = sensorConnections.tds,
        hasValue = readingAvailability.tds
    )
    val waterLevelPresentation = determineSensorReadingPresentation(
        deviceState = deviceConnectionState,
        sensorConnected = sensorConnections.waterLevel,
        hasValue = readingAvailability.waterLevel
    )

    val tempStatus = if (
        sensorConnections.temperature && readingAvailability.temperature
    ) {
        rangeStatus(
            value = readings.temperature,
            minimum = thresholds.minTemperature,
            maximum = thresholds.maxTemperature,
            lowLabel = "Low",
            highLabel = "High",
            criticalDeviation = 2.0
        )
    } else {
        disconnectedStatus
    }

    val phReadingStatus = if (
        sensorConnections.ph && readingAvailability.ph
    ) {
        rangeStatus(
            value = readings.ph,
            minimum = thresholds.minPh,
            maximum = thresholds.maxPh,
            lowLabel = "Acidic",
            highLabel = "Alkaline",
            criticalDeviation = 0.5
        )
    } else {
        disconnectedStatus
    }

    val turbidityStatus = if (
        sensorConnections.turbidity && readingAvailability.turbidity
    ) {
        maximumStatus(
            value = readings.turbidity,
            maximum = thresholds.maxTurbidity,
            highLabel = "High",
            criticalDeviation = 20.0
        )
    } else {
        disconnectedStatus
    }

    val tdsStatus = if (
        sensorConnections.tds && readingAvailability.tds
    ) {
        rangeStatus(
            value = readings.tds,
            minimum = thresholds.minTds,
            maximum = thresholds.maxTds,
            lowLabel = "Low",
            highLabel = "High",
            criticalDeviation = 100.0
        )
    } else {
        disconnectedStatus
    }

    val waterLevelStatus = if (
        sensorConnections.waterLevel && readingAvailability.waterLevel
    ) {
        rangeStatus(
            value = readings.waterLevel,
            minimum = thresholds.minWaterLevel,
            maximum = thresholds.maxWaterLevel,
            lowLabel = "Low",
            highLabel = "High",
            criticalDeviation = 5.0
        )
    } else {
        disconnectedStatus
    }

    val displayedTempStatus = sensorPresentationStatus(
        temperaturePresentation,
        tempStatus
    )
    val displayedPhStatus = sensorPresentationStatus(
        phPresentation,
        phReadingStatus
    )
    val displayedTurbidityStatus = sensorPresentationStatus(
        turbidityPresentation,
        turbidityStatus
    )
    val displayedTdsStatus = sensorPresentationStatus(
        tdsPresentation,
        tdsStatus
    )
    val displayedWaterLevelStatus = sensorPresentationStatus(
        waterLevelPresentation,
        waterLevelStatus
    )

    val connectedStatuses = buildList {
        if (temperaturePresentation == SensorReadingPresentation.LIVE) {
            add(tempStatus)
        }
        if (phPresentation == SensorReadingPresentation.LIVE) {
            add(phReadingStatus)
        }
        if (turbidityPresentation == SensorReadingPresentation.LIVE) {
            add(turbidityStatus)
        }
        if (tdsPresentation == SensorReadingPresentation.LIVE) {
            add(tdsStatus)
        }
        if (waterLevelPresentation == SensorReadingPresentation.LIVE) {
            add(waterLevelStatus)
        }
    }

    val allSystemsSafe =
        connectedStatuses.isNotEmpty() &&
                connectedStatuses.all { status ->
                    status.first == "Safe"
                }

    val unreadAlertCount = alerts.count { alert ->
        !alert.acknowledged && !alert.resolved
    }

    if (showClearAlertsConfirmation) {
        AlertDialog(
            onDismissRequest = {
                showClearAlertsConfirmation = false
            },
            title = {
                Text(
                    text = "Clear alerts?",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "This clears the alert list for you on this device. Alert history is kept in Firebase and remains available in reports."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val clearedAt = System.currentTimeMillis()

                        dashboardPrefs.edit()
                            .putLong(
                                alertsClearKey,
                                clearedAt
                            )
                            .apply()

                        alertsClearedAt = clearedAt
                        alerts = emptyList()
                        seenAlertIds.clear()

                        val notificationManager =
                            context.getSystemService(
                                android.content.Context.NOTIFICATION_SERVICE
                            ) as? android.app.NotificationManager

                        notificationManager?.cancelAll()

                        showClearAlertsConfirmation = false
                    }
                ) {
                    Text(
                        text = "Clear",
                        color = Color(0xFFC62828),
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showClearAlertsConfirmation = false
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showAlertsPage) {
        PondAlertsScreen(
            user = user,
            pond = pond,
            alerts = alerts,
            errorMessage = alertsError,
            onBack = {
                showAlertsPage = false
            },
            onClearAlerts = {
                showClearAlertsConfirmation = true
            }
        )

        return
    }

    thresholdSensorToEdit?.let { selectedSensor ->
        SensorThresholdDialog(
            user = user,
            pond = pond,
            sensor = selectedSensor,
            currentThresholds = thresholds,
            onDismiss = {
                thresholdSensorToEdit = null
            },
            onSaved = {
                thresholdSensorToEdit = null
            }
        )
    }


    Surface(
        modifier = Modifier.fillMaxSize(),
        color = LightBlue
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            DashboardHeader(
                pondName = pond.pondName,
                unreadAlertCount = unreadAlertCount,
                showNewReadingFeedback = showNewReadingFeedback,
                onBackToPonds = onBackToPonds,
                onAlertsClick = {
                    showAlertsPage = true
                }
            )

            DashboardPullToRefresh(
                isRefreshing = isPullRefreshing,
                onRefresh = {
                    refreshRequest += 1
                },
                modifier = Modifier.weight(1f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                SystemStatus(
                    deviceState = deviceConnectionState,
                    deviceHealth = deviceHealth,
                    allSystemsSafe = allSystemsSafe,
                    connectedCount = 5 -
                            sensorConnections.disconnectedCount(),
                    disconnectedCount =
                        sensorConnections.disconnectedCount()
                )

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.White.copy(alpha = 0.72f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            modifier = Modifier.size(7.dp),
                            color = Blue,
                            shape = CircleShape
                        ) {}
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Press and hold a sensor card to change its threshold.",
                            fontSize = 11.sp,
                            color = Color(0xFF536078)
                        )
                    }
                }

                DashboardSectionHeader(
                    title = "Live sensor readings",
                    subtitle = "Current values from your pond monitoring device"
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SensorCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Rounded.Thermostat,
                        symbol = "",
                        title = "Temperature",
                        value = if (
                            sensorReadingIsVisible(temperaturePresentation)
                        ) {
                            String.format(
                                Locale.getDefault(),
                                "%.1f",
                                readings.temperature
                            )
                        } else {
                            "--"
                        },
                        unit = if (
                            sensorReadingIsVisible(temperaturePresentation)
                        ) "°C" else "",
                        status = displayedTempStatus.first,
                        statusColor = displayedTempStatus.second,
                        onLongClick = {
                            thresholdSensorToEdit = AnalyticsSensor.TEMPERATURE
                        }
                    )

                    SensorCard(
                        modifier = Modifier.weight(1f),
                        icon = null,
                        symbol = "pH",
                        title = "pH Level",
                        value = if (sensorReadingIsVisible(phPresentation)) {
                            String.format(
                                Locale.getDefault(),
                                "%.1f",
                                readings.ph
                            )
                        } else {
                            "--"
                        },
                        unit = "",
                        status = displayedPhStatus.first,
                        statusColor = displayedPhStatus.second,
                        onLongClick = {
                            thresholdSensorToEdit = AnalyticsSensor.PH
                        }
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SensorCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Rounded.Opacity,
                        symbol = "",
                        title = "Turbidity",
                        value = if (
                            sensorReadingIsVisible(turbidityPresentation)
                        ) {
                            String.format(
                                Locale.getDefault(),
                                "%.1f",
                                readings.turbidity
                            )
                        } else {
                            "--"
                        },
                        unit = if (
                            sensorReadingIsVisible(turbidityPresentation)
                        ) "%" else "",
                        status = displayedTurbidityStatus.first,
                        statusColor = displayedTurbidityStatus.second,
                        onLongClick = {
                            thresholdSensorToEdit = AnalyticsSensor.TURBIDITY
                        }
                    )

                    SensorCard(
                        modifier = Modifier.weight(1f),
                        icon = null,
                        symbol = "TDS",
                        title = "TDS",
                        value = if (sensorReadingIsVisible(tdsPresentation)) {
                            String.format(
                                Locale.getDefault(),
                                "%.0f",
                                readings.tds
                            )
                        } else {
                            "--"
                        },
                        unit = if (
                            sensorReadingIsVisible(tdsPresentation)
                        ) "ppm" else "",
                        status = displayedTdsStatus.first,
                        statusColor = displayedTdsStatus.second,
                        onLongClick = {
                            thresholdSensorToEdit = AnalyticsSensor.TDS
                        }
                    )
                }

                WaterLevelCard(
                    waterLevel = readings.waterLevel,
                    showValue = sensorReadingIsVisible(
                        waterLevelPresentation
                    ),
                    status = displayedWaterLevelStatus.first,
                    statusColor = displayedWaterLevelStatus.second,
                    onLongClick = {
                        thresholdSensorToEdit = AnalyticsSensor.WATER_LEVEL
                    }
                )

            }
        }
    }
}
}

@Composable
private fun DashboardSectionHeader(
    title: String,
    subtitle: String
) {
    Column {
        Text(
            text = title,
            color = Color(0xFF17204A),
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = subtitle,
            color = Color(0xFF68708A),
            fontSize = 11.sp
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DashboardPullToRefresh(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val pullToRefreshState = rememberPullToRefreshState()

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier,
        state = pullToRefreshState,
        indicator = {
            PullToRefreshDefaults.Indicator(
                state = pullToRefreshState,
                isRefreshing = isRefreshing,
                modifier = Modifier.align(Alignment.TopCenter),
                containerColor = Color.White,
                color = Blue
            )
        }
    ) {
        content()
    }
}

@Composable
fun DashboardHeader(
    pondName: String,
    unreadAlertCount: Int,
    showNewReadingFeedback: Boolean,
    onBackToPonds: () -> Unit,
    onAlertsClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.horizontalGradient(
                    listOf(Color(0xFF17266F), Blue)
                )
            )
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier
                .size(42.dp)
                .clickable {
                    onBackToPonds()
                },
            color = Color.White.copy(alpha = 0.12f),
            shape = CircleShape
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Back to ponds",
                    tint = Color.White,
                    modifier = Modifier.size(23.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(4.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = pondName,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )

            Crossfade(
                targetState = showNewReadingFeedback,
                animationSpec = tween(durationMillis = 220),
                label = "live reading feedback"
            ) { readingUpdated ->
                Text(
                    text = if (readingUpdated) {
                        "LIVE READING UPDATED"
                    } else {
                        "POND MONITORING"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (readingUpdated) {
                        Color(0xFFB9F6CA)
                    } else {
                        Color.White.copy(alpha = 0.62f)
                    },
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.9.sp
                )
            }
        }

        Box(
            modifier = Modifier
                .size(42.dp)
                .background(
                    color = Color.White.copy(alpha = 0.12f),
                    shape = CircleShape
                )
                .clickable {
                    onAlertsClick()
                }
        ) {
            Icon(
                imageVector = Icons.Rounded.Notifications,
                contentDescription = "Open alerts",
                tint = Color.White,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(21.dp)
            )

            if (unreadAlertCount > 0) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(20.dp),
                    color = Color(0xFFC62828),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (unreadAlertCount > 9) {
                                "9+"
                            } else {
                                unreadAlertCount.toString()
                            },
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PondAlertsScreen(
    user: CrayFarmUser,
    pond: PondSummary,
    alerts: List<PondAlert>,
    errorMessage: String,
    onBack: () -> Unit,
    onClearAlerts: () -> Unit
) {
    var selectedFilter by remember(pond.pondId) {
        mutableStateOf(AlertFilter.ALL)
    }

    val filteredAlerts = remember(alerts, selectedFilter) {
        alerts.filter { alert ->
            alertMatchesFilter(alert, selectedFilter)
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = LightBlue
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color(0xFF17266F), Blue)
                        )
                    )
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier
                        .size(42.dp)
                        .clickable {
                            onBack()
                        },
                    color = Color.White.copy(alpha = 0.12f),
                    shape = CircleShape
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back to dashboard",
                            tint = Color.White,
                            modifier = Modifier.size(23.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(4.dp))

                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "Alerts",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = pond.pondName,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.62f),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.7.sp
                    )
                }

                if (alerts.isNotEmpty()) {
                    Surface(
                        modifier = Modifier
                            .clickable {
                                onClearAlerts()
                            }
                            .padding(
                                start = 8.dp
                            ),
                        color = Color.White.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "Clear",
                            modifier = Modifier.padding(
                                horizontal = 13.dp,
                                vertical = 8.dp
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AlertFilter.entries.forEach { filter ->
                    val selected = selectedFilter == filter

                    Surface(
                        modifier = Modifier.clickable {
                            selectedFilter = filter
                        },
                        color = if (selected) Blue else Color.White,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(
                            width = 1.dp,
                            color = if (selected) {
                                Blue
                            } else {
                                Blue.copy(alpha = 0.18f)
                            }
                        )
                    ) {
                        Text(
                            text = filter.label,
                            modifier = Modifier.padding(
                                horizontal = 14.dp,
                                vertical = 8.dp
                            ),
                            color = if (selected) Color.White else Blue,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            when {
                errorMessage.isNotBlank() -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "Unable to load alerts: $errorMessage",
                            color = Color(0xFFC62828),
                            textAlign = TextAlign.Center
                        )
                    }
                }

                filteredAlerts.isEmpty() -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = SafeGreen,
                            modifier = Modifier.size(48.dp)
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = if (alerts.isEmpty()) {
                                "No alerts for this pond."
                            } else {
                                "No ${selectedFilter.label.lowercase()} alerts."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFF68708A)
                        )
                    }
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item {
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        items(
                            items = filteredAlerts,
                            key = { alert -> alert.id }
                        ) { alert ->
                            PondAlertCard(
                                user = user,
                                pond = pond,
                                alert = alert
                            )
                        }

                        item {
                            Spacer(modifier = Modifier.height(18.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PondAlertCard(
    user: CrayFarmUser,
    pond: PondSummary,
    alert: PondAlert
) {
    var isAcknowledging by remember(alert.id) {
        mutableStateOf(false)
    }

    var errorMessage by remember(alert.id) {
        mutableStateOf("")
    }

    val lifecycleColor = when {
        alert.resolved -> SafeGreen
        alert.acknowledged -> Blue
        else -> alertSeverityColor(alert.severity)
    }

    val lifecycleLabel = buildList {
        add(alert.severity.ifBlank { "WARNING" }.uppercase())
        add(if (alert.resolved) "Resolved" else "Active")
        if (alert.acknowledged) {
            add("Acknowledged")
        }
    }.joinToString(" • ")

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = Blue.copy(alpha = 0.10f),
                shape = RoundedCornerShape(16.dp)
            ),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 1.dp
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.size(34.dp),
                    color = lifecycleColor.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (alert.resolved) {
                                Icons.Rounded.CheckCircle
                            } else {
                                Icons.Rounded.Notifications
                            },
                            contentDescription = null,
                            tint = lifecycleColor,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = alert.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = formatActivityTime(alert.createdAt),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF68708A)
                    )
                }

                Text(
                    text = lifecycleLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = lifecycleColor,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = alert.message,
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF30364D)
            )

            if (alert.resolved && alert.resolvedAt > 0L) {
                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Resolved ${formatActivityTime(alert.resolvedAt)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = SafeGreen,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Suggested action",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = Blue
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = alert.suggestedAction,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF68708A)
            )

            if (errorMessage.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = errorMessage,
                    color = Color(0xFFC62828),
                    fontSize = 12.sp
                )
            }

            if (!alert.acknowledged) {
                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = {
                        isAcknowledging = true
                        errorMessage = ""

                        acknowledgePondAlert(
                            user = user,
                            pond = pond,
                            alert = alert,
                            onSuccess = {
                                isAcknowledging = false
                            },
                            onError = { error ->
                                isAcknowledging = false
                                errorMessage = error
                            }
                        )
                    },
                    enabled = !isAcknowledging,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Blue,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (isAcknowledging) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            text = "Acknowledge",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SystemStatus(
    deviceState: DeviceConnectionState,
    deviceHealth: DeviceHealthMetadata,
    allSystemsSafe: Boolean,
    connectedCount: Int,
    disconnectedCount: Int
) {
    val statusColor = when {
        deviceState == DeviceConnectionState.OFFLINE -> Color(0xFFC62828)
        deviceState == DeviceConnectionState.NO_HEARTBEAT -> Color.Gray
        connectedCount == 0 -> Color.Gray
        allSystemsSafe && disconnectedCount == 0 -> SafeGreen
        allSystemsSafe -> WarningOrange
        else -> WarningOrange
    }

    val statusIcon = when {
        deviceState == DeviceConnectionState.OFFLINE -> "!"
        deviceState == DeviceConnectionState.NO_HEARTBEAT -> "—"
        connectedCount == 0 -> "—"
        allSystemsSafe && disconnectedCount == 0 -> "✓"
        else -> "!"
    }

    val statusText = when {
        deviceState == DeviceConnectionState.OFFLINE ->
            "ESP32 is offline - showing last known values"

        deviceState == DeviceConnectionState.NO_HEARTBEAT ->
            "Waiting for the first ESP32 reading"

        connectedCount == 0 ->
            "No sensor readings detected"

        disconnectedCount > 0 && allSystemsSafe ->
            "$disconnectedCount sensor(s) not connected"

        disconnectedCount > 0 ->
            "Some readings need attention; $disconnectedCount sensor(s) not connected"

        allSystemsSafe ->
            "Systems are normal"

        else ->
            "One or more readings need attention"
    }

    val stateLabel = when (deviceState) {
        DeviceConnectionState.ONLINE -> "ONLINE"
        DeviceConnectionState.OFFLINE -> "OFFLINE"
        DeviceConnectionState.NO_HEARTBEAT -> "NO DATA"
    }

    val deviceDetail = buildList {
        if (deviceHealth.firmwareVersion.isNotBlank()) {
            add("Firmware ${deviceHealth.firmwareVersion}")
        }
        deviceHealth.wifiRssi?.let { rssi ->
            add("Wi-Fi $rssi dBm")
        }
    }.joinToString("  •  ")

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(11.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = statusIcon,
                    color = statusColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 24.sp
                )

                Spacer(modifier = Modifier.width(10.dp))

                Text(
                    text = statusText,
                    modifier = Modifier.weight(1f),
                    fontSize = 12.sp,
                    color = statusColor,
                    fontWeight = FontWeight.Medium
                )

                Surface(
                    color = statusColor.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = stateLabel,
                        modifier = Modifier.padding(
                            horizontal = 8.dp,
                            vertical = 4.dp
                        ),
                        color = statusColor,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Text(
                text = formatDeviceLastUpdated(deviceHealth.lastSeen),
                modifier = Modifier.padding(start = 34.dp, top = 2.dp),
                color = Color(0xFF68708A),
                fontSize = 10.sp
            )

            if (deviceDetail.isNotBlank()) {
                Text(
                    text = deviceDetail,
                    modifier = Modifier.padding(start = 34.dp, top = 1.dp),
                    color = Color(0xFF68708A),
                    fontSize = 9.sp
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SensorCard(
    modifier: Modifier = Modifier,
    icon: ImageVector?,
    symbol: String,
    title: String,
    value: String,
    unit: String,
    status: String,
    statusColor: Color,
    onLongClick: () -> Unit
) {
    Card(
        modifier = modifier
            .height(115.dp)
            .border(
                width = 1.dp,
                color = Blue.copy(alpha = 0.10f),
                shape = RoundedCornerShape(14.dp)
            )
            .combinedClickable(
                onClick = {},
                onLongClick = onLongClick
            ),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(
            modifier = Modifier.padding(10.dp)
        ) {
            Text(
                text = title,
                fontSize = 11.sp,
                color = Color(0xFF303030)
            )

            Spacer(modifier = Modifier.height(6.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    Surface(
                        modifier = Modifier.size(32.dp),
                        color = Blue.copy(alpha = 0.09f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = Blue,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }
                } else {
                    Surface(
                        color = Blue.copy(alpha = 0.09f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = symbol,
                            modifier = Modifier.padding(
                                horizontal = 7.dp,
                                vertical = 5.dp
                            ),
                            color = Blue,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.width(6.dp))

                Text(
                    text = value,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )

                if (unit.isNotEmpty()) {
                    Text(
                        text = unit,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(start = 2.dp, top = 8.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = status,
                color = statusColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WaterLevelCard(
    waterLevel: Double,
    showValue: Boolean,
    status: String,
    statusColor: Color,
    onLongClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(92.dp)
            .border(
                width = 1.dp,
                color = Blue.copy(alpha = 0.10f),
                shape = RoundedCornerShape(14.dp)
            )
            .combinedClickable(
                onClick = {},
                onLongClick = onLongClick
            ),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(
                text = "Water Level",
                fontSize = 11.sp
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Surface(
                    modifier = Modifier.size(38.dp),
                    color = Blue.copy(alpha = 0.09f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.Water,
                            contentDescription = null,
                            tint = Blue,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (showValue) {
                            String.format(
                                Locale.getDefault(),
                                "%.1f cm",
                                waterLevel
                            )
                        } else {
                            "--"
                        },
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Medium
                    )

                    Text(
                        text = status,
                        color = statusColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun SensorThresholdDialog(
    user: CrayFarmUser,
    pond: PondSummary,
    sensor: AnalyticsSensor,
    currentThresholds: PondThresholds,
    onDismiss: () -> Unit,
    onSaved: () -> Unit
) {
    val defaults = PondThresholds()

    var firstValue by remember(sensor, currentThresholds) {
        mutableStateOf(
            when (sensor) {
                AnalyticsSensor.TEMPERATURE ->
                    currentThresholds.minTemperature.formatThreshold()
                AnalyticsSensor.PH ->
                    currentThresholds.minPh.formatThreshold()
                AnalyticsSensor.TURBIDITY ->
                    currentThresholds.maxTurbidity.formatThreshold()
                AnalyticsSensor.TDS ->
                    currentThresholds.minTds.formatThreshold()
                AnalyticsSensor.WATER_LEVEL ->
                    currentThresholds.minWaterLevel.formatThreshold()
            }
        )
    }

    var secondValue by remember(sensor, currentThresholds) {
        mutableStateOf(
            when (sensor) {
                AnalyticsSensor.TEMPERATURE ->
                    currentThresholds.maxTemperature.formatThreshold()
                AnalyticsSensor.PH ->
                    currentThresholds.maxPh.formatThreshold()
                AnalyticsSensor.TDS ->
                    currentThresholds.maxTds.formatThreshold()
                AnalyticsSensor.WATER_LEVEL ->
                    currentThresholds.maxWaterLevel.formatThreshold()
                else -> ""
            }
        )
    }

    var errorMessage by remember(sensor) {
        mutableStateOf("")
    }

    var isSaving by remember(sensor) {
        mutableStateOf(false)
    }

    fun buildThresholds(): PondThresholds? {
        val first = firstValue.toDoubleOrNull()
        if (first == null) {
            errorMessage = "Enter a valid threshold value."
            return null
        }

        val updated = when (sensor) {
            AnalyticsSensor.TEMPERATURE -> {
                val second = secondValue.toDoubleOrNull()
                if (second == null) {
                    errorMessage = "Enter a valid maximum temperature."
                    return null
                }

                currentThresholds.copy(
                    minTemperature = first,
                    maxTemperature = second
                )
            }

            AnalyticsSensor.PH -> {
                val second = secondValue.toDoubleOrNull()
                if (second == null) {
                    errorMessage = "Enter a valid maximum pH."
                    return null
                }

                currentThresholds.copy(
                    minPh = first,
                    maxPh = second
                )
            }

            AnalyticsSensor.TURBIDITY -> {
                currentThresholds.copy(
                    maxTurbidity = first
                )
            }

            AnalyticsSensor.TDS -> {
                val second = secondValue.toDoubleOrNull()
                if (second == null) {
                    errorMessage = "Enter a valid maximum TDS."
                    return null
                }

                currentThresholds.copy(
                    minTds = first,
                    maxTds = second
                )
            }

            AnalyticsSensor.WATER_LEVEL -> {
                val second = secondValue.toDoubleOrNull()
                if (second == null) {
                    errorMessage = "Enter a valid maximum water level."
                    return null
                }

                currentThresholds.copy(
                    minWaterLevel = first,
                    maxWaterLevel = second
                )
            }
        }

        val validationError = validatePondThresholds(updated)
        if (validationError != null) {
            errorMessage = validationError
            return null
        }

        return updated
    }

    fun defaultThresholdsForSensor(): PondThresholds {
        return when (sensor) {
            AnalyticsSensor.TEMPERATURE ->
                currentThresholds.copy(
                    minTemperature = defaults.minTemperature,
                    maxTemperature = defaults.maxTemperature
                )

            AnalyticsSensor.PH ->
                currentThresholds.copy(
                    minPh = defaults.minPh,
                    maxPh = defaults.maxPh
                )

            AnalyticsSensor.TURBIDITY ->
                currentThresholds.copy(
                    maxTurbidity = defaults.maxTurbidity
                )

            AnalyticsSensor.TDS ->
                currentThresholds.copy(
                    minTds = defaults.minTds,
                    maxTds = defaults.maxTds
                )

            AnalyticsSensor.WATER_LEVEL ->
                currentThresholds.copy(
                    minWaterLevel = defaults.minWaterLevel,
                    maxWaterLevel = defaults.maxWaterLevel
                )
        }
    }

    val title = when (sensor) {
        AnalyticsSensor.TEMPERATURE -> "Temperature Threshold"
        AnalyticsSensor.PH -> "pH Threshold"
        AnalyticsSensor.TURBIDITY -> "Turbidity Threshold"
        AnalyticsSensor.TDS -> "TDS Threshold"
        AnalyticsSensor.WATER_LEVEL -> "Water Level Threshold"
    }

    AlertDialog(
        onDismissRequest = {
            if (!isSaving) {
                onDismiss()
            }
        },
        title = {
            Text(
                text = title,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "These values control the sensor's Safe, Low, and High status on ${pond.pondName}.",
                    fontSize = 13.sp,
                    color = Color.Gray
                )

                when (sensor) {
                    AnalyticsSensor.TEMPERATURE -> {
                        ThresholdField(
                            modifier = Modifier.fillMaxWidth(),
                            label = "Minimum Temperature (°C)",
                            value = firstValue,
                            onValueChanged = {
                                firstValue = it
                                errorMessage = ""
                            }
                        )

                        ThresholdField(
                            modifier = Modifier.fillMaxWidth(),
                            label = "Maximum Temperature (°C)",
                            value = secondValue,
                            onValueChanged = {
                                secondValue = it
                                errorMessage = ""
                            }
                        )

                        Text(
                            text = "Default: 20°C – 30°C",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }

                    AnalyticsSensor.PH -> {
                        ThresholdField(
                            modifier = Modifier.fillMaxWidth(),
                            label = "Minimum pH",
                            value = firstValue,
                            onValueChanged = {
                                firstValue = it
                                errorMessage = ""
                            }
                        )

                        ThresholdField(
                            modifier = Modifier.fillMaxWidth(),
                            label = "Maximum pH",
                            value = secondValue,
                            onValueChanged = {
                                secondValue = it
                                errorMessage = ""
                            }
                        )

                        Text(
                            text = "Default: 6.5 – 8.5",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }

                    AnalyticsSensor.TURBIDITY -> {
                        ThresholdField(
                            modifier = Modifier.fillMaxWidth(),
                            label = "Maximum Relative Turbidity (%)",
                            value = firstValue,
                            onValueChanged = {
                                firstValue = it
                                errorMessage = ""
                            }
                        )

                        Text(
                            text = "Default maximum: 25%",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }

                    AnalyticsSensor.TDS -> {
                        ThresholdField(
                            modifier = Modifier.fillMaxWidth(),
                            label = "Minimum TDS (ppm)",
                            value = firstValue,
                            onValueChanged = {
                                firstValue = it
                                errorMessage = ""
                            }
                        )

                        ThresholdField(
                            modifier = Modifier.fillMaxWidth(),
                            label = "Maximum TDS (ppm)",
                            value = secondValue,
                            onValueChanged = {
                                secondValue = it
                                errorMessage = ""
                            }
                        )

                        Text(
                            text = "Default: 200 – 500 ppm",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }

                    AnalyticsSensor.WATER_LEVEL -> {
                        ThresholdField(
                            modifier = Modifier.fillMaxWidth(),
                            label = "Minimum Water Level (cm)",
                            value = firstValue,
                            onValueChanged = {
                                firstValue = it
                                errorMessage = ""
                            }
                        )

                        ThresholdField(
                            modifier = Modifier.fillMaxWidth(),
                            label = "Maximum Water Level (cm)",
                            value = secondValue,
                            onValueChanged = {
                                secondValue = it
                                errorMessage = ""
                            }
                        )

                        Text(
                            text = "Default safe range: 15 – 25 cm",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }
                }

                if (errorMessage.isNotBlank()) {
                    Text(
                        text = errorMessage,
                        color = Color(0xFFC62828),
                        fontSize = 12.sp
                    )
                }

                TextButton(
                    onClick = {
                        if (isSaving) {
                            return@TextButton
                        }

                        isSaving = true
                        errorMessage = ""

                        savePondThresholds(
                            user = user,
                            pond = pond,
                            thresholds = defaultThresholdsForSensor(),
                            onSuccess = {
                                isSaving = false
                                onSaved()
                            },
                            onError = { error ->
                                isSaving = false
                                errorMessage = error
                            }
                        )
                    },
                    enabled = !isSaving
                ) {
                    Text(
                        text = "Reset to Default",
                        color = Color(0xFFC62828),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (isSaving) {
                        return@TextButton
                    }

                    val updatedThresholds =
                        buildThresholds() ?: return@TextButton

                    isSaving = true
                    errorMessage = ""

                    savePondThresholds(
                        user = user,
                        pond = pond,
                        thresholds = updatedThresholds,
                        onSuccess = {
                            isSaving = false
                            onSaved()
                        },
                        onError = { error ->
                            isSaving = false
                            errorMessage = error
                        }
                    )
                },
                enabled = !isSaving
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        text = "Save",
                        color = Blue,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isSaving
            ) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun ActivityCard(
    title: String,
    message: String,
    time: String,
    color: Color
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(7.dp)
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(23.dp),
                color = color.copy(alpha = 0.22f),
                shape = RoundedCornerShape(3.dp)
            ) {}

            Spacer(modifier = Modifier.width(8.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )

                Text(
                    text = message,
                    fontSize = 9.sp,
                    color = Color.Gray
                )
            }

            Text(
                text = time,
                fontSize = 9.sp,
                color = Color.Gray
            )
        }
    }
}


