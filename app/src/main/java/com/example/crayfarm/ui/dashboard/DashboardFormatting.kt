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

private val SafeGreen = Color(0xFF16803B)
private val WarningOrange = Color(0xFFE76F00)

internal fun sensorPresentationStatus(
    presentation: SensorReadingPresentation,
    liveStatus: Pair<String, Color>
): Pair<String, Color> {
    return when (presentation) {
        SensorReadingPresentation.LIVE -> liveStatus
        SensorReadingPresentation.SENSOR_NOT_CONNECTED ->
            "Sensor not connected" to Color.Gray

        SensorReadingPresentation.STALE ->
            "Stale • Last known" to Color(0xFF9A6700)

        SensorReadingPresentation.NO_DATA ->
            "No data" to Color.Gray
    }
}

internal fun sensorReadingIsVisible(
    presentation: SensorReadingPresentation
): Boolean {
    return presentation == SensorReadingPresentation.LIVE ||
            presentation == SensorReadingPresentation.STALE
}

internal fun formatDeviceLastUpdated(lastSeen: Long): String {
    if (lastSeen <= 0L) {
        return "Last updated: Never"
    }

    val time = SimpleDateFormat(
        "h:mm:ss a",
        Locale.getDefault()
    ).format(Date(lastSeen))

    return "Last updated: $time"
}

internal fun alertSeverityColor(severity: String): Color {
    return when (severity.uppercase()) {
        "CRITICAL" -> Color(0xFFC62828)
        else -> Color(0xFFE76F00)
    }
}


internal fun Double.formatThreshold(): String {
    return if (this % 1.0 == 0.0) {
        this.toInt().toString()
    } else {
        this.toString()
    }
}

internal fun rangeStatus(
    value: Double,
    minimum: Double,
    maximum: Double,
    lowLabel: String,
    highLabel: String,
    criticalDeviation: Double
): Pair<String, Color> {
    val label = rangeStatusLabel(
        value = value,
        minimum = minimum,
        maximum = maximum,
        lowLabel = lowLabel,
        highLabel = highLabel,
        criticalDeviation = criticalDeviation
    )

    return label to when (label) {
        "Safe" -> SafeGreen
        "Critical" -> Color(0xFFC62828)
        else -> WarningOrange
    }
}

internal fun maximumStatus(
    value: Double,
    maximum: Double,
    highLabel: String,
    criticalDeviation: Double
): Pair<String, Color> {
    val label = maximumStatusLabel(
        value = value,
        maximum = maximum,
        highLabel = highLabel,
        criticalDeviation = criticalDeviation
    )

    return label to when (label) {
        "Safe" -> SafeGreen
        "Critical" -> Color(0xFFC62828)
        else -> WarningOrange
    }
}

internal fun minimumStatus(
    value: Double,
    minimum: Double,
    lowLabel: String
): Pair<String, Color> {
    return if (value < minimum) {
        lowLabel to WarningOrange
    } else {
        "Safe" to SafeGreen
    }
}



internal fun temperatureStatus(temp: Double): Pair<String, Color> {
    return when {
        temp < 20 -> "Low" to Color(0xFF1976D2)
        temp <= 30 -> "Safe" to SafeGreen
        else -> "High" to Color.Red
    }
}

internal fun phStatus(ph: Double): Pair<String, Color> {
    return when {
        ph < 6.5 -> "Acidic" to Color.Red
        ph <= 8.5 -> "Safe" to SafeGreen
        else -> "Alkaline" to WarningOrange
    }
}


