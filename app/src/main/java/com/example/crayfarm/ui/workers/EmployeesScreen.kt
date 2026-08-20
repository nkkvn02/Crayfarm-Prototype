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
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
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
fun EmployeesScreen(
    admin: CrayFarmUser
) {
    var requestCode by remember { mutableStateOf("") }
    var foundRequest by remember { mutableStateOf<EmployeeRequest?>(null) }
    var currentEmployees by remember {
        mutableStateOf<List<EmployeeSummary>>(emptyList())
    }
    var formerEmployees by remember {
        mutableStateOf<List<EmployeeSummary>>(emptyList())
    }
    var membershipsByUid by remember {
        mutableStateOf<Map<String, WorkerMembership>>(emptyMap())
    }
    var employeeToRemove by remember {
        mutableStateOf<EmployeeSummary?>(null)
    }
    var employeeToRestore by remember {
        mutableStateOf<EmployeeSummary?>(null)
    }
    var employeeToView by remember {
        mutableStateOf<EmployeeSummary?>(null)
    }
    var message by remember { mutableStateOf("") }
    var messageIsSuccess by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var isChangingEmployee by remember { mutableStateOf(false) }
    var employeeSearchQuery by remember { mutableStateOf("") }

    val filteredCurrentEmployees = remember(
        currentEmployees,
        employeeSearchQuery
    ) {
        filterEmployees(currentEmployees, employeeSearchQuery)
    }

    val filteredFormerEmployees = remember(
        formerEmployees,
        employeeSearchQuery
    ) {
        filterEmployees(formerEmployees, employeeSearchQuery)
    }

    val usersRef = remember {
        FirebaseDatabase.getInstance()
            .getReference("users")
    }

    val activeWorkersQuery = remember(admin.farmId) {
        usersRef
            .orderByChild("farmId")
            .equalTo(admin.farmId)
    }

    val removedWorkersQuery = remember(admin.farmId) {
        usersRef
            .orderByChild("previousFarmId")
            .equalTo(admin.farmId)
    }

    val membersRef = remember(admin.farmId) {
        FirebaseDatabase.getInstance()
            .getReference("farms")
            .child(admin.farmId)
            .child("members")
    }

    DisposableEffect(
        activeWorkersQuery,
        removedWorkersQuery,
        membersRef,
        admin.farmId
    ) {
        fun archivedMembershipEmployees(): List<EmployeeSummary> {
            return membershipsByUid.values
                .filter { membership ->
                    membership.role == "worker" &&
                            isFormerEmploymentStatus(membership.status)
                }
                .map { membership -> membership.toEmployeeSummary() }
        }

        fun mergeFormerEmployees(
            userProfiles: List<EmployeeSummary>
        ): List<EmployeeSummary> {
            return (archivedMembershipEmployees() + userProfiles)
                .associateBy { employee -> employee.uid }
                .values
                .sortedBy { employee -> employee.fullName.lowercase() }
        }

        val membersListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                membershipsByUid = snapshot.children.associate { child ->
                    val membership = child.toWorkerMembership()
                    membership.uid to membership
                }

                currentEmployees = currentEmployees.map { employee ->
                    employee.withMembership(membershipsByUid[employee.uid])
                }
                formerEmployees = mergeFormerEmployees(
                    formerEmployees
                        .filter { employee ->
                            isFormerEmploymentStatus(
                                employee.employmentStatus
                            )
                        }
                        .map { employee ->
                            employee.withMembership(
                                membershipsByUid[employee.uid]
                            )
                        }
                )
            }

            override fun onCancelled(error: DatabaseError) {
                messageIsSuccess = false
                message = error.message
            }
        }

        val activeListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                currentEmployees = snapshot.children
                    .mapNotNull { child ->
                        val role = child.child("role")
                            .getValue(String::class.java)
                            .orEmpty()

                        val approved = child.child("approved")
                            .getValue(Boolean::class.java)
                            ?: false

                        if (role != "worker" || !approved) {
                            return@mapNotNull null
                        }

                        child.toEmployeeSummary(
                            membershipsByUid[child.key.orEmpty()]
                        )
                    }
                    .sortedBy { employee ->
                        employee.fullName.lowercase()
                    }
            }

            override fun onCancelled(error: DatabaseError) {
                messageIsSuccess = false
                message = error.message
            }
        }

        val removedListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val userProfiles = snapshot.children
                    .mapNotNull { child ->
                        val role = child.child("role")
                            .getValue(String::class.java)
                            .orEmpty()

                        val approved = child.child("approved")
                            .getValue(Boolean::class.java)
                            ?: false

                        val employmentStatus =
                            child.child("employmentStatus")
                                .getValue(String::class.java)
                                .orEmpty()

                        if (
                            role != "worker" ||
                            approved ||
                            !isFormerEmploymentStatus(employmentStatus)
                        ) {
                            return@mapNotNull null
                        }

                        child.toEmployeeSummary(
                            membershipsByUid[child.key.orEmpty()]
                        )
                    }
                formerEmployees = mergeFormerEmployees(userProfiles)
            }

            override fun onCancelled(error: DatabaseError) {
                messageIsSuccess = false
                message = error.message
            }
        }

        membersRef.addValueEventListener(membersListener)
        activeWorkersQuery.addValueEventListener(activeListener)
        removedWorkersQuery.addValueEventListener(removedListener)

        onDispose {
            membersRef.removeEventListener(membersListener)
            activeWorkersQuery.removeEventListener(activeListener)
            removedWorkersQuery.removeEventListener(removedListener)
        }
    }

    employeeToView?.let { employee ->
        WorkerPublicProfileDialog(
            employee = employee,
            onDismiss = {
                employeeToView = null
            }
        )
    }

    employeeToRemove?.let { employee ->
        AlertDialog(
            onDismissRequest = {
                if (!isChangingEmployee) {
                    employeeToRemove = null
                }
            },
            title = {
                Text("Remove employee?")
            },
            text = {
                Text(
                    "Are you sure you want to remove ${employee.fullName}? " +
                            "The worker will lose access to this farm, but the account will not be deleted."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        isChangingEmployee = true
                        message = ""

                        removeEmployeeFromFarm(
                            admin = admin,
                            employee = employee,
                            onSuccess = {
                                isChangingEmployee = false
                                employeeToRemove = null
                                messageIsSuccess = true
                                message = "Employee removed successfully."
                            },
                            onError = { error ->
                                isChangingEmployee = false
                                messageIsSuccess = false
                                message = error
                            }
                        )
                    },
                    enabled = !isChangingEmployee
                ) {
                    if (isChangingEmployee) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            text = "Remove",
                            color = Color(0xFFC62828)
                        )
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        employeeToRemove = null
                    },
                    enabled = !isChangingEmployee
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    employeeToRestore?.let { employee ->
        AlertDialog(
            onDismissRequest = {
                if (!isChangingEmployee) {
                    employeeToRestore = null
                }
            },
            title = {
                Text("Restore employee?")
            },
            text = {
                Text(
                    "Are you sure you want to restore ${employee.fullName}? " +
                            "The worker will regain access to this farm using the same account."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        isChangingEmployee = true
                        message = ""

                        restoreEmployeeToFarm(
                            admin = admin,
                            employee = employee,
                            onSuccess = {
                                isChangingEmployee = false
                                employeeToRestore = null
                                messageIsSuccess = true
                                message = "Employee restored successfully."
                            },
                            onError = { error ->
                                isChangingEmployee = false
                                messageIsSuccess = false
                                message = error
                            }
                        )
                    },
                    enabled = !isChangingEmployee
                ) {
                    if (isChangingEmployee) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            text = "Restore",
                            color = SafeGreen
                        )
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        employeeToRestore = null
                    },
                    enabled = !isChangingEmployee
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = LightBlue
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            PageHeader(title = "Employees")

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Spacer(modifier = Modifier.height(6.dp))
                }

                item {
                    Text(
                        text = "Add a worker",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF222222)
                    )
                }

                item {
                    Text(
                        text = "Enter the worker's 6-digit request code.",
                        fontSize = 13.sp,
                        color = Color.Gray
                    )
                }

                item {
                    OutlinedTextField(
                        value = requestCode,
                        onValueChange = { value ->
                            requestCode = value
                                .filter { it.isDigit() }
                                .take(6)

                            foundRequest = null
                            message = ""
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Employee request code") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color.White,
                            unfocusedContainerColor = Color.White
                        ),
                        shape = RoundedCornerShape(10.dp)
                    )
                }

                item {
                    Button(
                        onClick = {
                            val cleanCode = requestCode.trim()

                            if (cleanCode.length != 6) {
                                messageIsSuccess = false
                                message = "Enter a valid 6-digit request code."
                                foundRequest = null
                                return@Button
                            }

                            isLoading = true
                            message = ""
                            foundRequest = null

                            FirebaseDatabase.getInstance()
                                .getReference("employeeRequests")
                                .child(cleanCode)
                                .get()
                                .addOnSuccessListener { snapshot ->
                                    isLoading = false

                                    if (!snapshot.exists()) {
                                        messageIsSuccess = false
                                        message = "Employee request was not found."
                                        return@addOnSuccessListener
                                    }

                                    val request = EmployeeRequest(
                                        code = snapshot.child("code")
                                            .getValue(String::class.java)
                                            .orEmpty(),
                                        workerUid = snapshot.child("workerUid")
                                            .getValue(String::class.java)
                                            .orEmpty(),
                                        workerName = snapshot.child("workerName")
                                            .getValue(String::class.java)
                                            .orEmpty(),
                                        workerEmail = snapshot.child("workerEmail")
                                            .getValue(String::class.java)
                                            .orEmpty(),
                                        status = snapshot.child("status")
                                            .getValue(String::class.java)
                                            .orEmpty(),
                                        farmId = snapshot.child("farmId")
                                            .getValue(String::class.java)
                                            .orEmpty()
                                    )

                                    when {
                                        request.workerUid.isBlank() -> {
                                            messageIsSuccess = false
                                            message =
                                                "This request has invalid worker data."
                                        }

                                        request.status != "pending" -> {
                                            messageIsSuccess = false
                                            message =
                                                "This request has already been ${request.status}."
                                        }

                                        else -> {
                                            foundRequest = request
                                        }
                                    }
                                }
                                .addOnFailureListener { exception ->
                                    isLoading = false
                                    messageIsSuccess = false
                                    message = exception.message
                                        ?: "Unable to find employee request."
                                }
                        },
                        enabled = !isLoading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Blue,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(
                                text = "Find Worker",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                if (message.isNotBlank()) {
                    item {
                        Text(
                            text = message,
                            color = if (messageIsSuccess) {
                                SafeGreen
                            } else {
                                Color(0xFFC62828)
                            },
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                foundRequest?.let { request ->
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = Color.White
                            ),
                            elevation = CardDefaults.cardElevation(
                                defaultElevation = 3.dp
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp)
                            ) {
                                Text(
                                    text = "Worker Request",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Blue
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                Text(
                                    text = request.workerName,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )

                                Spacer(modifier = Modifier.height(4.dp))

                                Text(
                                    text = request.workerEmail,
                                    fontSize = 14.sp,
                                    color = Color.Gray
                                )

                                Spacer(modifier = Modifier.height(4.dp))

                                Text(
                                    text = "Code: ${request.code}",
                                    fontSize = 13.sp,
                                    color = Color.Gray
                                )

                                Spacer(modifier = Modifier.height(14.dp))

                                Button(
                                    onClick = {
                                        if (admin.farmId.isBlank()) {
                                            messageIsSuccess = false
                                            message =
                                                "Admin account is not connected to a farm."
                                            return@Button
                                        }

                                        isLoading = true
                                        message = ""

                                        approveEmployeeRequest(
                                            admin = admin,
                                            request = request,
                                            onSuccess = {
                                                isLoading = false
                                                foundRequest = null
                                                requestCode = ""
                                                messageIsSuccess = true
                                                message =
                                                    "Worker approved successfully."
                                            },
                                            onError = { error ->
                                                isLoading = false
                                                messageIsSuccess = false
                                                message = error
                                            }
                                        )
                                    },
                                    enabled = !isLoading,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(46.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = SafeGreen,
                                        contentColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text(
                                        text = "Approve Worker",
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    OutlinedTextField(
                        value = employeeSearchQuery,
                        onValueChange = { query ->
                            employeeSearchQuery = query
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Search employees") },
                        placeholder = { Text("Name, email, or status") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.Search,
                                contentDescription = null
                            )
                        },
                        trailingIcon = if (employeeSearchQuery.isNotBlank()) {
                            {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "Clear employee search",
                                    modifier = Modifier.clickable {
                                        employeeSearchQuery = ""
                                    }
                                )
                            }
                        } else {
                            null
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Blue,
                            unfocusedBorderColor = Color(0xFF8A95AA),
                            focusedContainerColor = Color.White,
                            unfocusedContainerColor = Color.White,
                            cursorColor = Blue
                        )
                    )
                }

                item {
                    Text(
                        text = employeeSectionTitle(
                            label = "Active employees",
                            filteredCount = filteredCurrentEmployees.size,
                            totalCount = currentEmployees.size,
                            hasQuery = employeeSearchQuery.isNotBlank()
                        ),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF222222)
                    )
                }

                if (filteredCurrentEmployees.isEmpty()) {
                    item {
                        EmptyEmployeeCard(
                            text = if (employeeSearchQuery.isBlank()) {
                                "No workers are currently connected to this farm."
                            } else {
                                "No active employees match your search."
                            }
                        )
                    }
                } else {
                    items(
                        items = filteredCurrentEmployees,
                        key = { employee -> "active_${employee.uid}" }
                    ) { employee ->
                        EmployeeCard(
                            employee = employee,
                            actionLabel = "Remove",
                            actionColor = Color(0xFFC62828),
                            onActionClick = {
                                employeeToRemove = employee
                            },
                            onLongClick = {
                                employeeToView = employee
                            }
                        )
                    }
                }

                item {
                    Text(
                        text = employeeSectionTitle(
                            label = "Archived employees",
                            filteredCount = filteredFormerEmployees.size,
                            totalCount = formerEmployees.size,
                            hasQuery = employeeSearchQuery.isNotBlank()
                        ),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF222222)
                    )
                }

                if (filteredFormerEmployees.isEmpty()) {
                    item {
                        EmptyEmployeeCard(
                            text = if (employeeSearchQuery.isBlank()) {
                                "No removed or resigned workers are archived."
                            } else {
                                "No archived employees match your search."
                            }
                        )
                    }
                } else {
                    items(
                        items = filteredFormerEmployees,
                        key = { employee -> "former_${employee.uid}" }
                    ) { employee ->
                        EmployeeCard(
                            employee = employee,
                            actionLabel = "Restore",
                            actionColor = SafeGreen,
                            onActionClick = {
                                employeeToRestore = employee
                            },
                            onLongClick = {
                                employeeToView = employee
                            }
                        )
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }
    }
}

private fun employeeSectionTitle(
    label: String,
    filteredCount: Int,
    totalCount: Int,
    hasQuery: Boolean
): String {
    return if (hasQuery) {
        "$label ($filteredCount of $totalCount)"
    } else {
        "$label ($totalCount)"
    }
}

@Composable
fun EmptyEmployeeCard(
    text: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(18.dp),
            color = Color.Gray,
            fontSize = 14.sp,
            textAlign = TextAlign.Center
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EmployeeCard(
    employee: EmployeeSummary,
    actionLabel: String,
    actionColor: Color,
    onActionClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onLongClick,
                onLongClick = onLongClick
            ),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = employee.fullName,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = employee.email,
                    fontSize = 13.sp,
                    color = Color.Gray
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = employmentStatusLabel(employee.employmentStatus),
                    fontSize = 12.sp,
                    color = if (
                        isFormerEmploymentStatus(employee.employmentStatus)
                    ) {
                        Color(0xFFC62828)
                    } else {
                        SafeGreen
                    },
                    fontWeight = FontWeight.Medium
                )

                if (employee.joinedAt > 0L) {
                    Spacer(modifier = Modifier.height(3.dp))

                    Text(
                        text = "Joined ${formatWorkerLifecycleDate(employee.joinedAt)}",
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                }
            }

            TextButton(
                onClick = onActionClick
            ) {
                Text(
                    text = actionLabel,
                    color = actionColor,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun WorkerPublicProfileDialog(
    employee: EmployeeSummary,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Worker Information",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                WorkerInfoRow(
                    label = "Name",
                    value = employee.fullName
                )

                WorkerInfoRow(
                    label = "Email",
                    value = employee.email
                )

                WorkerInfoRow(
                    label = "Employment status",
                    value = employmentStatusLabel(employee.employmentStatus)
                )

                WorkerInfoRow(
                    label = "Joined farm",
                    value = if (employee.joinedAt > 0L) {
                        formatWorkerLifecycleDate(employee.joinedAt)
                    } else {
                        "Not available"
                    }
                )

                if (employee.leftAt > 0L) {
                    WorkerInfoRow(
                        label = "Left farm",
                        value = formatWorkerLifecycleDate(employee.leftAt)
                    )
                }

                WorkerInfoRow(
                    label = "Age",
                    value = if (employee.age > 0) {
                        employee.age.toString()
                    } else {
                        "Not provided"
                    }
                )

                WorkerInfoRow(
                    label = "Birthday",
                    value = employee.birthday.ifBlank {
                        "Not provided"
                    }
                )

                WorkerInfoRow(
                    label = "Contact No.",
                    value = employee.contactNumber.ifBlank {
                        "Not provided"
                    }
                )

                WorkerInfoRow(
                    label = "Address",
                    value = employee.address.ifBlank {
                        "Not provided"
                    }
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss
            ) {
                Text(
                    text = "Close",
                    color = Blue,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    )
}

@Composable
fun WorkerInfoRow(
    label: String,
    value: String
) {
    Column {
        Text(
            text = label,
            fontSize = 12.sp,
            color = Color.Gray,
            fontWeight = FontWeight.Medium
        )

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = value,
            fontSize = 15.sp,
            color = Color(0xFF222222)
        )
    }
}



internal fun formatActivityTime(timestamp: Long): String {
    if (timestamp <= 0L) {
        return ""
    }

    val formatter = SimpleDateFormat(
        "MMM d, h:mm a",
        Locale.getDefault()
    )

    return formatter.format(Date(timestamp))
}

private fun formatWorkerLifecycleDate(timestamp: Long): String {
    return SimpleDateFormat(
        "MMM d, yyyy",
        Locale.getDefault()
    ).format(Date(timestamp))
}

internal fun activityColor(type: String): Color {
    return when (type) {
        "employee_approved" -> SafeGreen
        "employee_restored" -> SafeGreen
        "employee_removed" -> Color(0xFFC62828)
        "worker_resigned" -> Color(0xFFC62828)
        "login" -> Blue
        "alert" -> WarningOrange
        "pond_added" -> Blue
        "pond_renamed" -> Blue
        "pond_deleted" -> Color(0xFFC62828)
        "thresholds_updated" -> Blue
        "alert_acknowledged" -> WarningOrange
        else -> Color.Gray
    }
}
