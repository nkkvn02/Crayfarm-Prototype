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

internal fun DataSnapshot.readNullableNumber(
    key: String
): Double? {
    return (child(key).value as? Number)?.toDouble()
}

internal fun DataSnapshot.readNullableBoolean(
    key: String
): Boolean? {
    return child(key).getValue(Boolean::class.java)
}

internal fun DataSnapshot.readNumber(key: String): Double {
    return (child(key).value as? Number)?.toDouble() ?: 0.0
}

internal fun DataSnapshot.readNumberOrDefault(
    key: String,
    defaultValue: Double
): Double {
    return (child(key).value as? Number)?.toDouble()
        ?: defaultValue
}

internal fun DataSnapshot.toPondHistoryRecord(): PondHistoryRecord? {
    val recordedAt = child("recordedAt")
        .getValue(Long::class.java)
        ?: key?.toLongOrNull()
        ?: 0L

    if (recordedAt <= 0L) {
        return null
    }

    val temperature = readNullableNumber("temperature")
    val ph = readNullableNumber("ph")
    val turbidity = readNullableNumber("turbidity")
    val tds = readNullableNumber("tds")
    val waterLevel = readNullableNumber("waterLevel")

    fun readingIsAvailable(value: Double?, connectionKey: String): Boolean {
        return value != null && (readNullableBoolean(connectionKey) ?: true)
    }

    return PondHistoryRecord(
        id = key.orEmpty(),
        temperature = temperature ?: 0.0,
        temperatureAvailable = readingIsAvailable(
            temperature,
            "temperatureConnected"
        ),
        ph = ph ?: 0.0,
        phAvailable = readingIsAvailable(ph, "phConnected"),
        turbidity = turbidity ?: 0.0,
        turbidityAvailable = readingIsAvailable(
            turbidity,
            "turbidityConnected"
        ),
        tds = tds ?: 0.0,
        tdsAvailable = readingIsAvailable(tds, "tdsConnected"),
        waterLevel = waterLevel ?: 0.0,
        waterLevelAvailable = readingIsAvailable(
            waterLevel,
            "waterLevelConnected"
        ),
        recordedAt = recordedAt
    )
}

internal fun DataSnapshot.toPondThresholds(): PondThresholds {
    return PondThresholds(
        minTemperature = readNumberOrDefault("minTemperature", 20.0),
        maxTemperature = readNumberOrDefault("maxTemperature", 30.0),
        minPh = readNumberOrDefault("minPh", 6.5),
        maxPh = readNumberOrDefault("maxPh", 8.5),
        maxTurbidity = readNumberOrDefault("maxTurbidity", 25.0),
        minTds = readNumberOrDefault("minTds", 200.0),
        maxTds = readNumberOrDefault("maxTds", 500.0),
        minWaterLevel = if (child("maxWaterLevel").exists()) {
            readNumberOrDefault("minWaterLevel", 15.0)
        } else {
            15.0
        },
        maxWaterLevel = readNumberOrDefault("maxWaterLevel", 25.0)
    )
}

internal fun DataSnapshot.toWorkerMembership(): WorkerMembership {
    val status = child("status")
        .getValue(String::class.java)
        .orEmpty()
        .ifBlank { EMPLOYMENT_STATUS_ACTIVE }

    return WorkerMembership(
        uid = key.orEmpty(),
        role = child("role")
            .getValue(String::class.java)
            .orEmpty()
            .ifBlank { "worker" },
        status = status,
        active = child("active")
            .getValue(Boolean::class.java)
            ?: !isFormerEmploymentStatus(status),
        joinedAt = (child("joinedAt").value as? Number)?.toLong() ?: 0L,
        leftAt = (child("leftAt").value as? Number)?.toLong()
            ?: (child("removedAt").value as? Number)?.toLong()
            ?: (child("resignedAt").value as? Number)?.toLong()
            ?: 0L,
        fullName = child("fullName")
            .getValue(String::class.java)
            .orEmpty(),
        email = child("email")
            .getValue(String::class.java)
            .orEmpty(),
        age = (child("age").value as? Number)?.toInt() ?: 0,
        address = child("address")
            .getValue(String::class.java)
            .orEmpty(),
        contactNumber = child("contactNumber")
            .getValue(String::class.java)
            .orEmpty(),
        birthday = child("birthday")
            .getValue(String::class.java)
            .orEmpty()
    )
}

internal fun DataSnapshot.toEmployeeSummary(
    membership: WorkerMembership?
): EmployeeSummary {
    val status = child("employmentStatus")
        .getValue(String::class.java)
        .orEmpty()
        .ifBlank { membership?.status ?: EMPLOYMENT_STATUS_ACTIVE }
    val userJoinedAt = (child("joinedAt").value as? Number)?.toLong() ?: 0L
    val userLeftAt = (child("removedAt").value as? Number)?.toLong()
        ?: (child("resignedAt").value as? Number)?.toLong()
        ?: 0L

    return EmployeeSummary(
        uid = key.orEmpty(),
        fullName = child("fullName")
            .getValue(String::class.java)
            .orEmpty(),
        email = child("email")
            .getValue(String::class.java)
            .orEmpty(),
        age = (child("age").value as? Number)?.toInt() ?: 0,
        address = child("address")
            .getValue(String::class.java)
            .orEmpty(),
        contactNumber = child("contactNumber")
            .getValue(String::class.java)
            .orEmpty(),
        birthday = child("birthday")
            .getValue(String::class.java)
            .orEmpty(),
        employmentStatus = status,
        joinedAt = preservedJoinedAt(
            membership?.joinedAt ?: 0L,
            userJoinedAt
        ),
        leftAt = membership?.leftAt?.takeIf { timestamp -> timestamp > 0L }
            ?: userLeftAt
    )
}

internal fun WorkerMembership.toEmployeeSummary(): EmployeeSummary {
    return EmployeeSummary(
        uid = uid,
        fullName = fullName,
        email = email,
        age = age,
        address = address,
        contactNumber = contactNumber,
        birthday = birthday,
        employmentStatus = status,
        joinedAt = joinedAt,
        leftAt = leftAt
    )
}

internal fun EmployeeSummary.withMembership(
    membership: WorkerMembership?
): EmployeeSummary {
    if (membership == null) {
        return this
    }

    return copy(
        employmentStatus = membership.status.ifBlank { employmentStatus },
        joinedAt = preservedJoinedAt(membership.joinedAt, joinedAt),
        leftAt = membership.leftAt.takeIf { timestamp -> timestamp > 0L }
            ?: leftAt
    )
}



