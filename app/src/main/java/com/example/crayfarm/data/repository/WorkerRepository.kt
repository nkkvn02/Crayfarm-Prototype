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

internal fun approveEmployeeRequest(
    admin: CrayFarmUser,
    request: EmployeeRequest,
    onSuccess: () -> Unit,
    onError: (String) -> Unit
) {
    val database = FirebaseDatabase.getInstance().reference
    val approvedAt = System.currentTimeMillis()

    val logKey = database
        .child("farms")
        .child(admin.farmId)
        .child("activityLogs")
        .push()
        .key

    if (logKey == null) {
        onError("Unable to create the activity log.")
        return
    }

    val activityLog = createActivityLogMap(
        type = "employee_approved",
        title = "Worker approved",
        message = "${request.workerName} was approved by ${admin.fullName}.",
        actorUid = admin.uid,
        actorName = admin.fullName,
        targetUid = request.workerUid,
        targetName = request.workerName,
        timestamp = approvedAt
    )

    val updates = hashMapOf<String, Any?>(
        "users/${request.workerUid}/farmId" to admin.farmId,
        "users/${request.workerUid}/previousFarmId" to admin.farmId,
        "users/${request.workerUid}/employmentStatus" to EMPLOYMENT_STATUS_ACTIVE,
        "users/${request.workerUid}/approved" to true,
        "users/${request.workerUid}/joinedAt" to approvedAt,
        "users/${request.workerUid}/removedAt" to null,
        "users/${request.workerUid}/removedBy" to null,
        "users/${request.workerUid}/resignedAt" to null,
        "users/${request.workerUid}/resignationMessage" to null,

        "farms/${admin.farmId}/members/${request.workerUid}/role" to "worker",
        "farms/${admin.farmId}/members/${request.workerUid}/joinedAt" to approvedAt,
        "farms/${admin.farmId}/members/${request.workerUid}/status" to EMPLOYMENT_STATUS_ACTIVE,
        "farms/${admin.farmId}/members/${request.workerUid}/active" to true,
        "farms/${admin.farmId}/members/${request.workerUid}/fullName" to request.workerName,
        "farms/${admin.farmId}/members/${request.workerUid}/email" to request.workerEmail,
        "farms/${admin.farmId}/members/${request.workerUid}/leftAt" to null,
        "farms/${admin.farmId}/members/${request.workerUid}/removedAt" to null,
        "farms/${admin.farmId}/members/${request.workerUid}/resignedAt" to null,
        "farms/${admin.farmId}/members/${request.workerUid}/resignationMessage" to null,
        "farms/${admin.farmId}/activityLogs/$logKey" to activityLog,

        "employeeRequests/${request.code}/status" to "approved",
        "employeeRequests/${request.code}/farmId" to admin.farmId,
        "employeeRequests/${request.code}/approvedBy" to admin.uid,
        "employeeRequests/${request.code}/approvedAt" to approvedAt
    )

    database.updateChildren(updates)
        .addOnSuccessListener {
            onSuccess()
        }
        .addOnFailureListener { exception ->
            onError(
                exception.message ?: "Unable to approve the worker."
            )
        }
}

internal fun removeEmployeeFromFarm(
    admin: CrayFarmUser,
    employee: EmployeeSummary,
    onSuccess: () -> Unit,
    onError: (String) -> Unit
) {
    if (admin.farmId.isBlank()) {
        onError("Admin account is not connected to a farm.")
        return
    }

    val database = FirebaseDatabase.getInstance().reference
    val removedAt = System.currentTimeMillis()

    val logKey = database
        .child("farms")
        .child(admin.farmId)
        .child("activityLogs")
        .push()
        .key

    if (logKey == null) {
        onError("Unable to create the activity log.")
        return
    }

    val activityLog = createActivityLogMap(
        type = "employee_removed",
        title = "Worker removed",
        message = "${employee.fullName} was removed by ${admin.fullName}.",
        actorUid = admin.uid,
        actorName = admin.fullName,
        targetUid = employee.uid,
        targetName = employee.fullName,
        timestamp = removedAt
    )

    val updates = hashMapOf<String, Any?>(
        "users/${employee.uid}/farmId" to "",
        "users/${employee.uid}/previousFarmId" to admin.farmId,
        "users/${employee.uid}/employmentStatus" to EMPLOYMENT_STATUS_REMOVED,
        "users/${employee.uid}/approved" to false,
        "users/${employee.uid}/removedAt" to removedAt,
        "users/${employee.uid}/removedBy" to admin.uid,
        "farms/${admin.farmId}/members/${employee.uid}/role" to "worker",
        "farms/${admin.farmId}/members/${employee.uid}/status" to EMPLOYMENT_STATUS_REMOVED,
        "farms/${admin.farmId}/members/${employee.uid}/active" to false,
        "farms/${admin.farmId}/members/${employee.uid}/fullName" to employee.fullName,
        "farms/${admin.farmId}/members/${employee.uid}/email" to employee.email,
        "farms/${admin.farmId}/members/${employee.uid}/age" to employee.age,
        "farms/${admin.farmId}/members/${employee.uid}/address" to employee.address,
        "farms/${admin.farmId}/members/${employee.uid}/contactNumber" to employee.contactNumber,
        "farms/${admin.farmId}/members/${employee.uid}/birthday" to employee.birthday,
        "farms/${admin.farmId}/members/${employee.uid}/leftAt" to removedAt,
        "farms/${admin.farmId}/members/${employee.uid}/removedAt" to removedAt,
        "farms/${admin.farmId}/members/${employee.uid}/removedBy" to admin.uid,
        "farms/${admin.farmId}/activityLogs/$logKey" to activityLog
    )

    database.updateChildren(updates)
        .addOnSuccessListener {
            onSuccess()
        }
        .addOnFailureListener { exception ->
            onError(
                exception.message ?: "Unable to remove the employee."
            )
        }
}

internal fun restoreEmployeeToFarm(
    admin: CrayFarmUser,
    employee: EmployeeSummary,
    onSuccess: () -> Unit,
    onError: (String) -> Unit
) {
    if (admin.farmId.isBlank()) {
        onError("Admin account is not connected to a farm.")
        return
    }

    val database = FirebaseDatabase.getInstance().reference
    val restoredAt = System.currentTimeMillis()
    val joinedAt = preservedJoinedAt(employee.joinedAt, restoredAt)

    val logKey = database
        .child("farms")
        .child(admin.farmId)
        .child("activityLogs")
        .push()
        .key

    if (logKey == null) {
        onError("Unable to create the activity log.")
        return
    }

    val activityLog = createActivityLogMap(
        type = "employee_restored",
        title = "Worker restored",
        message = "${employee.fullName} was restored by ${admin.fullName}.",
        actorUid = admin.uid,
        actorName = admin.fullName,
        targetUid = employee.uid,
        targetName = employee.fullName,
        timestamp = restoredAt
    )

    val updates = hashMapOf<String, Any?>(
        "users/${employee.uid}/farmId" to admin.farmId,
        "users/${employee.uid}/previousFarmId" to admin.farmId,
        "users/${employee.uid}/employmentStatus" to EMPLOYMENT_STATUS_ACTIVE,
        "users/${employee.uid}/approved" to true,
        "users/${employee.uid}/joinedAt" to joinedAt,
        "users/${employee.uid}/restoredAt" to restoredAt,
        "users/${employee.uid}/restoredBy" to admin.uid,
        "users/${employee.uid}/removedAt" to null,
        "users/${employee.uid}/removedBy" to null,
        "users/${employee.uid}/resignedAt" to null,
        "users/${employee.uid}/resignationMessage" to null,

        "farms/${admin.farmId}/members/${employee.uid}/role" to "worker",
        "farms/${admin.farmId}/members/${employee.uid}/joinedAt" to joinedAt,
        "farms/${admin.farmId}/members/${employee.uid}/status" to EMPLOYMENT_STATUS_ACTIVE,
        "farms/${admin.farmId}/members/${employee.uid}/active" to true,
        "farms/${admin.farmId}/members/${employee.uid}/fullName" to employee.fullName,
        "farms/${admin.farmId}/members/${employee.uid}/email" to employee.email,
        "farms/${admin.farmId}/members/${employee.uid}/age" to employee.age,
        "farms/${admin.farmId}/members/${employee.uid}/address" to employee.address,
        "farms/${admin.farmId}/members/${employee.uid}/contactNumber" to employee.contactNumber,
        "farms/${admin.farmId}/members/${employee.uid}/birthday" to employee.birthday,
        "farms/${admin.farmId}/members/${employee.uid}/leftAt" to null,
        "farms/${admin.farmId}/members/${employee.uid}/removedAt" to null,
        "farms/${admin.farmId}/members/${employee.uid}/removedBy" to null,
        "farms/${admin.farmId}/members/${employee.uid}/resignedAt" to null,
        "farms/${admin.farmId}/members/${employee.uid}/resignationMessage" to null,
        "farms/${admin.farmId}/activityLogs/$logKey" to activityLog
    )

    database.updateChildren(updates)
        .addOnSuccessListener {
            onSuccess()
        }
        .addOnFailureListener { exception ->
            onError(
                exception.message ?: "Unable to restore the employee."
            )
        }
}

internal fun generateNewWorkerRequestCode(
    workerUid: String,
    oldRequestCode: String,
    onSuccess: (String) -> Unit,
    onError: (String) -> Unit
) {
    val database = FirebaseDatabase.getInstance().reference

    database.child("users")
        .child(workerUid)
        .get()
        .addOnSuccessListener { userSnapshot ->
            if (!userSnapshot.exists()) {
                onError("Worker profile was not found.")
                return@addOnSuccessListener
            }

            val fullName = userSnapshot.child("fullName")
                .getValue(String::class.java)
                .orEmpty()

            val email = userSnapshot.child("email")
                .getValue(String::class.java)
                .orEmpty()

            createUniqueEmployeeRequestCode(
                database = database,
                onSuccess = { newCode ->
                    val createdAt = System.currentTimeMillis()

                    val employeeRequest = mapOf(
                        "workerUid" to workerUid,
                        "workerName" to fullName,
                        "workerEmail" to email,
                        "code" to newCode,
                        "status" to "pending",
                        "farmId" to "",
                        "createdAt" to createdAt
                    )

                    val updates = hashMapOf<String, Any?>(
                        "users/$workerUid/requestCode" to newCode,
                        "employeeRequests/$newCode" to employeeRequest
                    )

                    if (oldRequestCode.isNotBlank() &&
                        oldRequestCode != newCode
                    ) {
                        updates[
                            "employeeRequests/$oldRequestCode/status"
                        ] = "replaced"

                        updates[
                            "employeeRequests/$oldRequestCode/replacedAt"
                        ] = createdAt
                    }

                    database.updateChildren(updates)
                        .addOnSuccessListener {
                            onSuccess(newCode)
                        }
                        .addOnFailureListener { exception ->
                            onError(
                                exception.message
                                    ?: "Unable to save the new request code."
                            )
                        }
                },
                onError = onError
            )
        }
        .addOnFailureListener { exception ->
            onError(
                exception.message ?: "Unable to load the worker profile."
            )
        }
}

internal fun createUniqueEmployeeRequestCode(
    database: com.google.firebase.database.DatabaseReference,
    attemptsRemaining: Int = 10,
    onSuccess: (String) -> Unit,
    onError: (String) -> Unit
) {
    if (attemptsRemaining <= 0) {
        onError("Unable to generate a unique request code. Try again.")
        return
    }

    val code = generateEmployeeRequestCode()

    database.child("employeeRequests")
        .child(code)
        .get()
        .addOnSuccessListener { snapshot ->
            if (snapshot.exists()) {
                createUniqueEmployeeRequestCode(
                    database = database,
                    attemptsRemaining = attemptsRemaining - 1,
                    onSuccess = onSuccess,
                    onError = onError
                )
            } else {
                onSuccess(code)
            }
        }
        .addOnFailureListener { exception ->
            onError(
                exception.message
                    ?: "Unable to check the request code."
            )
        }
}


