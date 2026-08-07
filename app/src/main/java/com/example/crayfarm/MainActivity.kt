package com.example.crayfarm

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.util.Patterns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.crayfarm.ui.theme.CrayFarmTheme
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.MutableData
import com.google.firebase.database.Transaction
import com.google.firebase.database.ValueEventListener
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Blue = Color(0xFF2839A8)
private val LightBlue = Color(0xFFEAF4FF)
private val SafeGreen = Color(0xFF16803B)
private val WarningOrange = Color(0xFFE76F00)

data class PondReadings(
    val temperature: Double = 0.0,
    val ph: Double = 0.0,
    val turbidity: Double = 0.0,
    val tds: Double = 0.0,
    val waterLevel: Double = 0.0
)

data class PondHistoryRecord(
    val id: String = "",
    val temperature: Double = 0.0,
    val ph: Double = 0.0,
    val turbidity: Double = 0.0,
    val tds: Double = 0.0,
    val waterLevel: Double = 0.0,
    val recordedAt: Long = 0L
)

enum class AnalyticsRange(
    val label: String,
    val durationMillis: Long
) {
    DAY(
        label = "24 Hours",
        durationMillis = 24L * 60L * 60L * 1000L
    ),
    WEEK(
        label = "7 Days",
        durationMillis = 7L * 24L * 60L * 60L * 1000L
    ),
    MONTH(
        label = "30 Days",
        durationMillis = 30L * 24L * 60L * 60L * 1000L
    )
}

enum class AnalyticsSensor(
    val label: String,
    val unit: String
) {
    TEMPERATURE("Temperature", "°C"),
    PH("pH", ""),
    TURBIDITY("Relative Turbidity", "%"),
    TDS("TDS", "ppm"),
    WATER_LEVEL("Water Level", "%")
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
    val minWaterLevel: Double = 40.0
)

data class PondAlert(
    val id: String = "",
    val type: String = "",
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
    val acknowledgedBy: String = ""
)

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
    val active: Boolean = false
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
    val email: String = ""
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
    ROLE_SELECTION,
    LOGIN,
    REGISTER,
    WAITING
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        CrayFarmNotificationHelper.createNotificationChannel(this)

        setContent {
            CrayFarmTheme {
                CrayFarmApp()
            }
        }
    }
}

@Composable
fun CrayFarmApp() {
    val auth = remember { FirebaseAuth.getInstance() }

    val notificationPermissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) {
            // The app can continue even when notification permission is denied.
        }

    LaunchedEffect(Unit) {
        if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.TIRAMISU
        ) {
            notificationPermissionLauncher.launch(
                Manifest.permission.POST_NOTIFICATIONS
            )
        }
    }

    var currentUser by remember {
        mutableStateOf<CrayFarmUser?>(null)
    }

    var isCheckingSession by remember {
        mutableStateOf(true)
    }

    var authPage by remember {
        mutableStateOf(AuthPage.ROLE_SELECTION)
    }

    var selectedEntryRole by remember {
        mutableStateOf<EntryRole?>(null)
    }

    var pendingWorkerUid by remember {
        mutableStateOf("")
    }

    var pendingRequestCode by remember {
        mutableStateOf("")
    }

    LaunchedEffect(Unit) {
        val firebaseUser = auth.currentUser

        if (firebaseUser == null) {
            isCheckingSession = false
        } else {
            loadUserProfile(
                uid = firebaseUser.uid,
                onSuccess = { user ->
                    currentUser = user
                    isCheckingSession = false
                },
                onWaiting = { user ->
                    currentUser = null
                    selectedEntryRole = EntryRole.WORKER
                    pendingWorkerUid = user.uid
                    pendingRequestCode = user.requestCode
                    authPage = AuthPage.WAITING
                    isCheckingSession = false
                },
                onError = {
                    auth.signOut()
                    currentUser = null
                    isCheckingSession = false
                    authPage = AuthPage.ROLE_SELECTION
                }
            )
        }
    }

    when {
        isCheckingSession -> {
            LoadingScreen()
        }

        currentUser == null -> {
            when (authPage) {
                AuthPage.ROLE_SELECTION -> {
                    RoleSelectionScreen(
                        onOwnerSelected = {
                            selectedEntryRole = EntryRole.OWNER
                            authPage = AuthPage.LOGIN
                        },
                        onWorkerSelected = {
                            selectedEntryRole = EntryRole.WORKER
                            authPage = AuthPage.LOGIN
                        }
                    )
                }

                AuthPage.LOGIN -> {
                    val role = selectedEntryRole

                    if (role == null) {
                        authPage = AuthPage.ROLE_SELECTION
                    } else {
                        LoginScreen(
                            entryRole = role,
                            onLoginSuccess = { user ->
                                currentUser = user
                            },
                            onWaitingForApproval = { user ->
                                pendingWorkerUid = user.uid
                                pendingRequestCode = user.requestCode
                                authPage = AuthPage.WAITING
                            },
                            onRegisterClick = {
                                authPage = AuthPage.REGISTER
                            },
                            onBackToRoleSelection = {
                                auth.signOut()
                                selectedEntryRole = null
                                authPage = AuthPage.ROLE_SELECTION
                            }
                        )
                    }
                }

                AuthPage.REGISTER -> {
                    when (selectedEntryRole) {
                        EntryRole.OWNER -> {
                            OwnerRegistrationScreen(
                                onBackToLogin = {
                                    authPage = AuthPage.LOGIN
                                },
                                onRegistrationSuccess = { owner ->
                                    currentUser = owner
                                }
                            )
                        }

                        EntryRole.WORKER -> {
                            RegistrationScreen(
                                onBackToLogin = {
                                    authPage = AuthPage.LOGIN
                                },
                                onRegistrationSuccess = { workerUid, requestCode ->
                                    pendingWorkerUid = workerUid
                                    pendingRequestCode = requestCode
                                    authPage = AuthPage.WAITING
                                }
                            )
                        }

                        null -> {
                            authPage = AuthPage.ROLE_SELECTION
                        }
                    }
                }

                AuthPage.WAITING -> {
                    WaitingForFarmScreen(
                        workerUid = pendingWorkerUid,
                        initialRequestCode = pendingRequestCode,
                        onRequestCodeChanged = { newCode ->
                            pendingRequestCode = newCode
                        },
                        onBackToLogin = {
                            auth.signOut()
                            pendingWorkerUid = ""
                            pendingRequestCode = ""
                            selectedEntryRole = EntryRole.WORKER
                            authPage = AuthPage.LOGIN
                        }
                    )
                }
            }
        }

        else -> {
            AuthenticatedApp(
                user = currentUser!!,
                onLogout = {
                    auth.signOut()
                    currentUser = null
                    selectedEntryRole = null
                    pendingWorkerUid = ""
                    pendingRequestCode = ""
                    authPage = AuthPage.ROLE_SELECTION
                }
            )
        }
    }
}

@Composable
fun AuthenticatedApp(
    user: CrayFarmUser,
    onLogout: () -> Unit
) {
    var selectedPond by remember(user.uid) {
        mutableStateOf<PondSummary?>(null)
    }

    var selectedPage by remember(user.uid) {
        mutableStateOf(AppPage.DASHBOARD)
    }

    if (selectedPond == null) {
        PondSelectionScreen(
            user = user,
            onPondSelected = { pond ->
                selectedPond = pond
                selectedPage = AppPage.DASHBOARD
            },
            onLogout = onLogout
        )

        return
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier.weight(1f)
        ) {
            when (selectedPage) {
                AppPage.DASHBOARD -> {
                    PondDashboardContent(
                        user = user,
                        pond = selectedPond!!,
                        onBackToPonds = {
                            selectedPage = AppPage.DASHBOARD
                            selectedPond = null
                        }
                    )
                }

                AppPage.ANALYTICS -> {
                    PondAnalyticsScreen(
                        user = user,
                        pond = selectedPond!!
                    )
                }

                AppPage.REPORTS -> {
                    PondReportsScreen(
                        user = user,
                        pond = selectedPond!!
                    )
                }

                AppPage.PROFILE_OR_EMPLOYEES -> {
                    if (user.role == "admin") {
                        EmployeesScreen(admin = user)
                    } else {
                        ProfileScreen(user = user)
                    }
                }

                AppPage.SETTINGS -> {
                    SettingsScreen(
                        user = user,
                        pond = selectedPond!!,
                        onLogout = onLogout
                    )
                }
            }
        }

        BottomNavigation(
            selectedPage = selectedPage,
            userRole = user.role,
            onPageSelected = { page ->
                selectedPage = page
            }
        )
    }
}

@Composable
fun PondSelectionScreen(
    user: CrayFarmUser,
    onPondSelected: (PondSummary) -> Unit,
    onLogout: () -> Unit
) {
    var ponds by remember {
        mutableStateOf<List<PondSummary>>(emptyList())
    }

    var isLoading by remember {
        mutableStateOf(true)
    }

    var errorMessage by remember {
        mutableStateOf("")
    }

    var successMessage by remember {
        mutableStateOf("")
    }

    var showAddPondDialog by remember {
        mutableStateOf(false)
    }

    var showLogoutConfirmation by remember {
        mutableStateOf(false)
    }

    var pondToEdit by remember {
        mutableStateOf<PondSummary?>(null)
    }

    var pondToDelete by remember {
        mutableStateOf<PondSummary?>(null)
    }

    val pondsRef = remember(user.farmId) {
        FirebaseDatabase.getInstance()
            .getReference("farms")
            .child(user.farmId)
            .child("ponds")
    }

    DisposableEffect(pondsRef) {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                ponds = snapshot.children.mapNotNull { child ->
                    val pondId = child.key.orEmpty()
                    val pondName = child.child("pondName")
                        .getValue(String::class.java)
                        .orEmpty()
                    val deviceId = child.child("deviceId")
                        .getValue(String::class.java)
                        .orEmpty()

                    if (pondId.isBlank() || pondName.isBlank()) {
                        null
                    } else {
                        PondSummary(
                            pondId = pondId,
                            pondName = pondName,
                            deviceId = deviceId
                        )
                    }
                }.sortedBy { pond ->
                    pond.pondName.lowercase()
                }

                isLoading = false
                errorMessage = ""
            }

            override fun onCancelled(error: DatabaseError) {
                isLoading = false
                errorMessage = error.message
            }
        }

        pondsRef.addValueEventListener(listener)

        onDispose {
            pondsRef.removeEventListener(listener)
        }
    }

    if (showLogoutConfirmation) {
        AlertDialog(
            onDismissRequest = {
                showLogoutConfirmation = false
            },
            title = {
                Text(
                    text = "Logout?",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to log out of your CrayFarm account?"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLogoutConfirmation = false
                        onLogout()
                    }
                ) {
                    Text(
                        text = "Logout",
                        color = Color(0xFFC62828),
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showLogoutConfirmation = false
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showAddPondDialog) {
        AddPondDialog(
            user = user,
            onDismiss = {
                showAddPondDialog = false
            },
            onPondCreated = { pond ->
                showAddPondDialog = false
                onPondSelected(pond)
            }
        )
    }

    pondToEdit?.let { pond ->
        EditPondNameDialog(
            user = user,
            pond = pond,
            onDismiss = {
                pondToEdit = null
            },
            onUpdated = {
                pondToEdit = null
                errorMessage = ""
                successMessage = "Pond name updated successfully."
            },
            onError = { error ->
                errorMessage = error
                successMessage = ""
            }
        )
    }

    pondToDelete?.let { pond ->
        DeletePondDialog(
            user = user,
            pond = pond,
            onDismiss = {
                pondToDelete = null
            },
            onDeleted = {
                pondToDelete = null
                errorMessage = ""
                successMessage =
                    "Pond deleted and ESP32 unlinked successfully."
            },
            onError = { error ->
                errorMessage = error
                successMessage = ""
            }
        )
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Blue
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 24.dp, vertical = 24.dp)
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "←",
                    modifier = Modifier
                        .clickable {
                            showLogoutConfirmation = true
                        }
                        .padding(6.dp),
                    color = Color.White,
                    fontSize = 28.sp
                )

                Spacer(modifier = Modifier.width(10.dp))

                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "Select Pond",
                        color = Color.White,
                        fontSize = 27.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = "Choose a pond to open its dashboard.",
                        color = Color(0xFFDDE3FF),
                        fontSize = 13.sp
                    )

                    Spacer(modifier = Modifier.height(3.dp))

                    Text(
                        text = "Tap the pencil to rename. Press and hold a pond to delete it.",
                        color = Color(0xFFBFC8FF),
                        fontSize = 11.sp
                    )
                }
            }

            if (successMessage.isNotBlank()) {
                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = successMessage,
                    modifier = Modifier.fillMaxWidth(),
                    color = Color(0xFFB9F6CA),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
            }

            if (errorMessage.isNotBlank()) {
                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = errorMessage,
                    modifier = Modifier.fillMaxWidth(),
                    color = Color(0xFFFFCDD2),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(22.dp))

            when {
                isLoading -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(
                            color = Color.White
                        )
                    }
                }

                else -> {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        items(
                            items = ponds,
                            key = { pond -> pond.pondId }
                        ) { pond ->
                            PondSelectionCard(
                                pond = pond,
                                onOpenClick = {
                                    onPondSelected(pond)
                                },
                                onEditClick = {
                                    successMessage = ""
                                    errorMessage = ""
                                    pondToEdit = pond
                                },
                                onDeleteClick = {
                                    successMessage = ""
                                    errorMessage = ""
                                    pondToDelete = pond
                                }
                            )
                        }

                        item {
                            AddPondCard(
                                onClick = {
                                    successMessage = ""
                                    errorMessage = ""
                                    showAddPondDialog = true
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PondSelectionCard(
    pond: PondSummary,
    onOpenClick: () -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(150.dp)
            .combinedClickable(
                onClick = {
                    onOpenClick()
                },
                onLongClick = {
                    onDeleteClick()
                }
            ),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 4.dp
        ),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Surface(
                    modifier = Modifier
                        .size(32.dp)
                        .clickable {
                            onEditClick()
                        },
                    color = Blue.copy(alpha = 0.10f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "⋮",
                            color = Blue,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "≈≈",
                    color = Blue,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(5.dp))

                Text(
                    text = pond.pondName,
                    color = Color.Black,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                if (pond.deviceId.isNotBlank()) {
                    Spacer(modifier = Modifier.height(3.dp))

                    Text(
                        text = pond.deviceId,
                        color = Color.Gray,
                        fontSize = 9.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
fun AddPondCard(
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(150.dp)
            .clickable {
                onClick()
            },
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.96f)
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 4.dp
        ),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "+",
                color = Blue,
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Add Pond",
                color = Blue,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun EditPondNameDialog(
    user: CrayFarmUser,
    pond: PondSummary,
    onDismiss: () -> Unit,
    onUpdated: () -> Unit,
    onError: (String) -> Unit
) {
    var newPondName by remember(pond.pondId) {
        mutableStateOf(pond.pondName)
    }

    var dialogError by remember {
        mutableStateOf("")
    }

    var isSaving by remember {
        mutableStateOf(false)
    }

    AlertDialog(
        onDismissRequest = {
            if (!isSaving) {
                onDismiss()
            }
        },
        title = {
            Text(
                text = "Edit Pond Name",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                OutlinedTextField(
                    value = newPondName,
                    onValueChange = {
                        newPondName = it.take(40)
                        dialogError = ""
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = {
                        Text("Pond name")
                    },
                    singleLine = true
                )

                if (dialogError.isNotBlank()) {
                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = dialogError,
                        color = Color(0xFFC62828),
                        fontSize = 13.sp
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val cleanName = newPondName.trim()

                    when {
                        cleanName.isBlank() -> {
                            dialogError = "Enter a pond name."
                        }

                        cleanName == pond.pondName -> {
                            dialogError =
                                "Enter a different pond name."
                        }

                        else -> {
                            isSaving = true
                            dialogError = ""

                            updatePondName(
                                user = user,
                                pond = pond,
                                newPondName = cleanName,
                                onSuccess = {
                                    isSaving = false
                                    onUpdated()
                                },
                                onError = { error ->
                                    isSaving = false
                                    dialogError = error
                                    onError(error)
                                }
                            )
                        }
                    }
                },
                enabled = !isSaving
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(19.dp),
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
fun DeletePondDialog(
    user: CrayFarmUser,
    pond: PondSummary,
    onDismiss: () -> Unit,
    onDeleted: () -> Unit,
    onError: (String) -> Unit
) {
    var isDeleting by remember {
        mutableStateOf(false)
    }

    var dialogError by remember {
        mutableStateOf("")
    }

    AlertDialog(
        onDismissRequest = {
            if (!isDeleting) {
                onDismiss()
            }
        },
        title = {
            Text(
                text = "Delete Pond?",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                Text(
                    text =
                        "Are you sure you want to delete ${pond.pondName}?"
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text =
                        "Note: Deleting this pond will also unlink ${pond.deviceId.ifBlank { "its ESP32" }}. The device can then be linked to another pond or farm.",
                    color = Color(0xFFC62828),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text =
                        "All readings and settings stored inside this pond will be permanently deleted.",
                    color = Color.Gray,
                    fontSize = 12.sp
                )

                if (dialogError.isNotBlank()) {
                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = dialogError,
                        color = Color(0xFFC62828),
                        fontSize = 13.sp
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    isDeleting = true
                    dialogError = ""

                    deletePondAndUnlinkDevice(
                        user = user,
                        pond = pond,
                        onSuccess = {
                            isDeleting = false
                            onDeleted()
                        },
                        onError = { error ->
                            isDeleting = false
                            dialogError = error
                            onError(error)
                        }
                    )
                },
                enabled = !isDeleting
            ) {
                if (isDeleting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(19.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        text = "Delete Pond",
                        color = Color(0xFFC62828),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isDeleting
            ) {
                Text("Cancel")
            }
        }
    )
}

private fun updatePondName(
    user: CrayFarmUser,
    pond: PondSummary,
    newPondName: String,
    onSuccess: () -> Unit,
    onError: (String) -> Unit
) {
    if (user.farmId.isBlank()) {
        onError("This account is not connected to a farm.")
        return
    }

    FirebaseDatabase.getInstance()
        .getReference("farms")
        .child(user.farmId)
        .child("ponds")
        .child(pond.pondId)
        .child("pondName")
        .setValue(newPondName)
        .addOnSuccessListener {
            addActivityLog(
                farmId = user.farmId,
                type = "pond_renamed",
                title = "Pond renamed",
                message =
                    "${pond.pondName} was renamed to $newPondName by ${user.fullName}.",
                actorUid = user.uid,
                actorName = user.fullName
            )

            onSuccess()
        }
        .addOnFailureListener { exception ->
            onError(
                exception.message
                    ?: "Unable to update the pond name."
            )
        }
}

private fun deletePondAndUnlinkDevice(
    user: CrayFarmUser,
    pond: PondSummary,
    onSuccess: () -> Unit,
    onError: (String) -> Unit
) {
    if (user.farmId.isBlank()) {
        onError("This account is not connected to a farm.")
        return
    }

    val database = FirebaseDatabase.getInstance().reference

    val updates = hashMapOf<String, Any?>(
        "farms/${user.farmId}/ponds/${pond.pondId}" to null
    )

    if (pond.deviceId.isNotBlank()) {
        updates["devices/${pond.deviceId}"] = null
    }

    database.updateChildren(updates)
        .addOnSuccessListener {
            addActivityLog(
                farmId = user.farmId,
                type = "pond_deleted",
                title = "Pond deleted",
                message =
                    "${pond.pondName} was deleted and ${pond.deviceId.ifBlank { "its ESP32" }} was unlinked by ${user.fullName}.",
                actorUid = user.uid,
                actorName = user.fullName
            )

            onSuccess()
        }
        .addOnFailureListener { exception ->
            onError(
                exception.message
                    ?: "Unable to delete the pond."
            )
        }
}

@Composable
fun AddPondDialog(
    user: CrayFarmUser,
    onDismiss: () -> Unit,
    onPondCreated: (PondSummary) -> Unit
) {
    var pondName by remember {
        mutableStateOf("")
    }

    var deviceId by remember {
        mutableStateOf("")
    }

    var errorMessage by remember {
        mutableStateOf("")
    }

    var isSaving by remember {
        mutableStateOf(false)
    }

    AlertDialog(
        onDismissRequest = {
            if (!isSaving) {
                onDismiss()
            }
        },
        title = {
            Text(
                text = "Add Pond",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                Text(
                    text = "Enter the pond name and the unique ESP32 device ID.",
                    color = Color.Gray,
                    fontSize = 13.sp
                )

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = pondName,
                    onValueChange = {
                        pondName = it
                        errorMessage = ""
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Pond name") },
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = deviceId,
                    onValueChange = { value ->
                        deviceId = value
                            .uppercase()
                            .filter { character ->
                                character.isLetterOrDigit() ||
                                        character == '-' ||
                                        character == '_'
                            }
                            .take(32)

                        errorMessage = ""
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("ESP32 device ID") },
                    supportingText = {
                        Text("Example: CRAY-ESP32-001")
                    },
                    singleLine = true
                )

                if (errorMessage.isNotBlank()) {
                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = errorMessage,
                        color = Color(0xFFC62828),
                        fontSize = 13.sp
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val cleanPondName = pondName.trim()
                    val cleanDeviceId = deviceId.trim()

                    when {
                        cleanPondName.isBlank() -> {
                            errorMessage = "Enter a pond name."
                        }

                        cleanDeviceId.length < 6 -> {
                            errorMessage =
                                "Enter a valid ESP32 device ID."
                        }

                        else -> {
                            isSaving = true
                            errorMessage = ""

                            linkEsp32AndCreatePond(
                                user = user,
                                pondName = cleanPondName,
                                deviceId = cleanDeviceId,
                                onSuccess = { pond ->
                                    isSaving = false
                                    onPondCreated(pond)
                                },
                                onError = { error ->
                                    isSaving = false
                                    errorMessage = error
                                }
                            )
                        }
                    }
                },
                enabled = !isSaving
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(19.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        text = "Link Device",
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

private fun linkEsp32AndCreatePond(
    user: CrayFarmUser,
    pondName: String,
    deviceId: String,
    onSuccess: (PondSummary) -> Unit,
    onError: (String) -> Unit
) {
    if (user.farmId.isBlank()) {
        onError("This account is not connected to a farm.")
        return
    }

    val database = FirebaseDatabase.getInstance().reference
    val pondId = database
        .child("farms")
        .child(user.farmId)
        .child("ponds")
        .push()
        .key

    if (pondId == null) {
        onError("Unable to create a pond identifier.")
        return
    }

    val normalizedDeviceId = deviceId.uppercase()
    val deviceRef = database
        .child("devices")
        .child(normalizedDeviceId)

    val linkedAt = System.currentTimeMillis()

    deviceRef.runTransaction(
        object : Transaction.Handler {
            override fun doTransaction(
                currentData: MutableData
            ): Transaction.Result {
                if (currentData.value != null) {
                    return Transaction.abort()
                }

                currentData.value = mapOf(
                    "deviceId" to normalizedDeviceId,
                    "farmId" to user.farmId,
                    "pondId" to pondId,
                    "linkedAt" to linkedAt,
                    "linkedBy" to user.uid
                )

                return Transaction.success(currentData)
            }

            override fun onComplete(
                error: DatabaseError?,
                committed: Boolean,
                currentData: DataSnapshot?
            ) {
                when {
                    error != null -> {
                        onError(
                            error.message.ifBlank {
                                "Unable to check the ESP32 device."
                            }
                        )
                    }

                    !committed -> {
                        val linkedFarmId = currentData
                            ?.child("farmId")
                            ?.getValue(String::class.java)
                            .orEmpty()

                        onError(
                            if (
                                linkedFarmId.isNotBlank() &&
                                linkedFarmId != user.farmId
                            ) {
                                "This ESP32 is already linked to another farm."
                            } else {
                                "This ESP32 is already linked to a pond."
                            }
                        )
                    }

                    else -> {
                        val pondData = mapOf(
                            "pondName" to pondName,
                            "deviceId" to normalizedDeviceId,
                            "createdAt" to linkedAt,
                            "createdBy" to user.uid,
                            "active" to true
                        )

                        database
                            .child("farms")
                            .child(user.farmId)
                            .child("ponds")
                            .child(pondId)
                            .setValue(pondData)
                            .addOnSuccessListener {
                                addActivityLog(
                                    farmId = user.farmId,
                                    type = "pond_added",
                                    title = "Pond added",
                                    message =
                                        "$pondName was linked to $normalizedDeviceId by ${user.fullName}.",
                                    actorUid = user.uid,
                                    actorName = user.fullName
                                )

                                onSuccess(
                                    PondSummary(
                                        pondId = pondId,
                                        pondName = pondName,
                                        deviceId = normalizedDeviceId
                                    )
                                )
                            }
                            .addOnFailureListener { exception ->
                                deviceRef.removeValue()

                                onError(
                                    exception.message
                                        ?: "Unable to save the pond."
                                )
                            }
                    }
                }
            }
        }
    )
}

@Composable
fun RoleSelectionScreen(
    onOwnerSelected: () -> Unit,
    onWorkerSelected: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Blue
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Farmer's Crayfish",
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "Choose Account Type",
                color = Color.White,
                fontSize = 25.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Select how you want to continue.",
                color = Color(0xFFDDE3FF),
                fontSize = 15.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(32.dp))

            RoleChoiceCard(
                title = "Farm Owner",
                description = "Create and manage a farm, workers, ponds, and settings.",
                icon = "♛",
                onClick = onOwnerSelected
            )

            Spacer(modifier = Modifier.height(18.dp))

            RoleChoiceCard(
                title = "Worker",
                description = "Join a farm using an employee request code.",
                icon = "♟",
                onClick = onWorkerSelected
            )
        }
    }
}

@Composable
fun RoleChoiceCard(
    title: String,
    description: String,
    icon: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                onClick()
            },
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 4.dp
        ),
        shape = RoundedCornerShape(18.dp)
    ) {
        Row(
            modifier = Modifier.padding(22.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(58.dp),
                color = Blue.copy(alpha = 0.10f),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = icon,
                        color = Blue,
                        fontSize = 30.sp
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    color = Color(0xFF1F1F1F),
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(5.dp))

                Text(
                    text = description,
                    color = Color.Gray,
                    fontSize = 13.sp
                )
            }

            Text(
                text = "›",
                color = Blue,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun LoginScreen(
    entryRole: EntryRole,
    onLoginSuccess: (CrayFarmUser) -> Unit,
    onWaitingForApproval: (CrayFarmUser) -> Unit,
    onRegisterClick: () -> Unit,
    onBackToRoleSelection: () -> Unit
) {
    val auth = remember { FirebaseAuth.getInstance() }

    val roleTitle = if (entryRole == EntryRole.OWNER) {
        "Farm Owner"
    } else {
        "Worker"
    }

    val expectedFirebaseRole = if (entryRole == EntryRole.OWNER) {
        "admin"
    } else {
        "worker"
    }

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Blue
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 34.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Farmer's Crayfish",
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = "$roleTitle Login",
                color = Color.White,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(28.dp))

            OutlinedTextField(
                value = email,
                onValueChange = {
                    email = it
                    errorMessage = ""
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Email") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email
                ),
                colors = registrationFieldColors(),
                shape = RoundedCornerShape(10.dp)
            )

            Spacer(modifier = Modifier.height(14.dp))

            OutlinedTextField(
                value = password,
                onValueChange = {
                    password = it
                    errorMessage = ""
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Password") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password
                ),
                colors = registrationFieldColors(),
                shape = RoundedCornerShape(10.dp)
            )

            if (errorMessage.isNotBlank()) {
                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = errorMessage,
                    color = Color(0xFFFFCDD2),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(22.dp))

            Button(
                onClick = {
                    val cleanEmail = email.trim()

                    when {
                        cleanEmail.isBlank() -> {
                            errorMessage = "Enter your email address."
                        }

                        !Patterns.EMAIL_ADDRESS.matcher(cleanEmail).matches() -> {
                            errorMessage = "Enter a valid email address."
                        }

                        password.isBlank() -> {
                            errorMessage = "Enter your password."
                        }

                        else -> {
                            isLoading = true
                            errorMessage = ""

                            auth.signInWithEmailAndPassword(
                                cleanEmail,
                                password
                            ).addOnSuccessListener { result ->

                                val uid = result.user?.uid

                                if (uid == null) {
                                    auth.signOut()
                                    isLoading = false
                                    errorMessage =
                                        "Unable to identify the account."
                                    return@addOnSuccessListener
                                }

                                loadUserProfile(
                                    uid = uid,
                                    onSuccess = { user ->
                                        if (user.role != expectedFirebaseRole) {
                                            auth.signOut()
                                            isLoading = false
                                            errorMessage =
                                                "This account is not registered as a $roleTitle."
                                            return@loadUserProfile
                                        }

                                        isLoading = false

                                        addActivityLog(
                                            farmId = user.farmId,
                                            type = "login",
                                            title = "Account login",
                                            message = "${user.fullName} logged in.",
                                            actorUid = user.uid,
                                            actorName = user.fullName
                                        )

                                        onLoginSuccess(user)
                                    },
                                    onWaiting = { user ->
                                        if (entryRole != EntryRole.WORKER) {
                                            auth.signOut()
                                            isLoading = false
                                            errorMessage =
                                                "This account is not registered as a Farm Owner."
                                        } else {
                                            isLoading = false
                                            onWaitingForApproval(user)
                                        }
                                    },
                                    onError = { message ->
                                        auth.signOut()
                                        isLoading = false
                                        errorMessage = message
                                    }
                                )
                            }.addOnFailureListener { exception ->
                                isLoading = false

                                errorMessage = when {
                                    exception.message
                                        ?.contains(
                                            "credential",
                                            ignoreCase = true
                                        ) == true -> {
                                        "Incorrect email or password."
                                    }

                                    exception.message
                                        ?.contains(
                                            "network",
                                            ignoreCase = true
                                        ) == true -> {
                                        "Check your internet connection."
                                    }

                                    else -> {
                                        exception.message ?: "Login failed."
                                    }
                                }
                            }
                        }
                    }
                },
                enabled = !isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF07106A),
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        text = "Login",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            TextButton(
                onClick = onRegisterClick,
                enabled = !isLoading
            ) {
                Text(
                    text = "Don't have a $roleTitle account? Register",
                    color = Color.White
                )
            }

            TextButton(
                onClick = onBackToRoleSelection,
                enabled = !isLoading
            ) {
                Text(
                    text = "Choose a different role",
                    color = Color(0xFFDDE3FF)
                )
            }
        }
    }
}

@Composable
fun OwnerRegistrationScreen(
    onBackToLogin: () -> Unit,
    onRegistrationSuccess: (CrayFarmUser) -> Unit
) {
    val auth = remember { FirebaseAuth.getInstance() }

    var fullName by remember { mutableStateOf("") }
    var farmName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Blue
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 34.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Create Farm Owner Account",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(22.dp))

            OutlinedTextField(
                value = fullName,
                onValueChange = {
                    fullName = it
                    errorMessage = ""
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Owner full name") },
                singleLine = true,
                colors = registrationFieldColors(),
                shape = RoundedCornerShape(10.dp)
            )

            Spacer(modifier = Modifier.height(11.dp))

            OutlinedTextField(
                value = farmName,
                onValueChange = {
                    farmName = it
                    errorMessage = ""
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Farm name") },
                singleLine = true,
                colors = registrationFieldColors(),
                shape = RoundedCornerShape(10.dp)
            )

            Spacer(modifier = Modifier.height(11.dp))

            OutlinedTextField(
                value = email,
                onValueChange = {
                    email = it
                    errorMessage = ""
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Email") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email
                ),
                colors = registrationFieldColors(),
                shape = RoundedCornerShape(10.dp)
            )

            Spacer(modifier = Modifier.height(11.dp))

            OutlinedTextField(
                value = password,
                onValueChange = {
                    password = it
                    errorMessage = ""
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Password") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password
                ),
                colors = registrationFieldColors(),
                shape = RoundedCornerShape(10.dp)
            )

            Spacer(modifier = Modifier.height(11.dp))

            OutlinedTextField(
                value = confirmPassword,
                onValueChange = {
                    confirmPassword = it
                    errorMessage = ""
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Confirm password") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password
                ),
                colors = registrationFieldColors(),
                shape = RoundedCornerShape(10.dp)
            )

            if (errorMessage.isNotBlank()) {
                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = errorMessage,
                    color = Color(0xFFFFCDD2),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            Button(
                onClick = {
                    val cleanName = fullName.trim()
                    val cleanFarmName = farmName.trim()
                    val cleanEmail = email.trim()

                    when {
                        cleanName.isBlank() -> {
                            errorMessage = "Enter the owner's full name."
                        }

                        cleanFarmName.isBlank() -> {
                            errorMessage = "Enter the farm name."
                        }

                        cleanEmail.isBlank() -> {
                            errorMessage = "Enter your email address."
                        }

                        !Patterns.EMAIL_ADDRESS
                            .matcher(cleanEmail)
                            .matches() -> {
                            errorMessage = "Enter a valid email address."
                        }

                        password.length < 6 -> {
                            errorMessage =
                                "Password must contain at least 6 characters."
                        }

                        password != confirmPassword -> {
                            errorMessage = "Passwords do not match."
                        }

                        else -> {
                            isLoading = true
                            errorMessage = ""

                            auth.createUserWithEmailAndPassword(
                                cleanEmail,
                                password
                            ).addOnSuccessListener { result ->
                                val firebaseUser = result.user

                                if (firebaseUser == null) {
                                    isLoading = false
                                    errorMessage =
                                        "Unable to create the owner account."
                                    return@addOnSuccessListener
                                }

                                val database =
                                    FirebaseDatabase.getInstance().reference

                                val farmId = database
                                    .child("farms")
                                    .push()
                                    .key

                                if (farmId == null) {
                                    isLoading = false
                                    firebaseUser.delete()
                                    errorMessage =
                                        "Unable to create a farm identifier."
                                    return@addOnSuccessListener
                                }

                                val createdAt = System.currentTimeMillis()

                                val ownerProfile = mapOf(
                                    "fullName" to cleanName,
                                    "email" to cleanEmail,
                                    "role" to "admin",
                                    "farmId" to farmId,
                                    "approved" to true,
                                    "active" to true,
                                    "employmentStatus" to "active"
                                )

                                val updates = mapOf<String, Any>(
                                    "users/${firebaseUser.uid}" to ownerProfile,

                                    "farms/$farmId/farmName" to cleanFarmName,
                                    "farms/$farmId/ownerUid" to firebaseUser.uid,
                                    "farms/$farmId/createdAt" to createdAt,

                                    "farms/$farmId/members/${firebaseUser.uid}/role" to "admin",
                                    "farms/$farmId/members/${firebaseUser.uid}/joinedAt" to createdAt
                                )

                                database.updateChildren(updates)
                                    .addOnSuccessListener {
                                        val owner = CrayFarmUser(
                                            uid = firebaseUser.uid,
                                            fullName = cleanName,
                                            email = cleanEmail,
                                            role = "admin",
                                            farmId = farmId,
                                            employmentStatus = "active",
                                            approved = true,
                                            active = true
                                        )

                                        addActivityLog(
                                            farmId = farmId,
                                            type = "owner_registered",
                                            title = "Farm created",
                                            message = "$cleanName created $cleanFarmName.",
                                            actorUid = firebaseUser.uid,
                                            actorName = cleanName
                                        )

                                        isLoading = false
                                        onRegistrationSuccess(owner)
                                    }
                                    .addOnFailureListener { exception ->
                                        isLoading = false
                                        firebaseUser.delete()
                                        errorMessage =
                                            exception.message
                                                ?: "Unable to save the farm owner account."
                                    }
                            }.addOnFailureListener { exception ->
                                isLoading = false

                                errorMessage = when {
                                    exception.message
                                        ?.contains(
                                            "already in use",
                                            ignoreCase = true
                                        ) == true -> {
                                        "This email address is already registered."
                                    }

                                    exception.message
                                        ?.contains(
                                            "network",
                                            ignoreCase = true
                                        ) == true -> {
                                        "Check your internet connection."
                                    }

                                    else -> {
                                        exception.message
                                            ?: "Unable to create owner account."
                                    }
                                }
                            }
                        }
                    }
                },
                enabled = !isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF07106A),
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        text = "Create Farm",
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            TextButton(
                onClick = onBackToLogin,
                enabled = !isLoading
            ) {
                Text(
                    text = "Already have an owner account? Login",
                    color = Color.White
                )
            }
        }
    }
}

@Composable
fun RegistrationScreen(
    onBackToLogin: () -> Unit,
    onRegistrationSuccess: (String, String) -> Unit
) {
    val auth = remember { FirebaseAuth.getInstance() }

    var fullName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Blue
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 34.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Create Worker Account",
                color = Color.White,
                fontSize = 25.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(24.dp))

            OutlinedTextField(
                value = fullName,
                onValueChange = {
                    fullName = it
                    errorMessage = ""
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Full name") },
                singleLine = true,
                colors = registrationFieldColors(),
                shape = RoundedCornerShape(10.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = email,
                onValueChange = {
                    email = it
                    errorMessage = ""
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Email") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email
                ),
                colors = registrationFieldColors(),
                shape = RoundedCornerShape(10.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = password,
                onValueChange = {
                    password = it
                    errorMessage = ""
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Password") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password
                ),
                colors = registrationFieldColors(),
                shape = RoundedCornerShape(10.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = confirmPassword,
                onValueChange = {
                    confirmPassword = it
                    errorMessage = ""
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Confirm password") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password
                ),
                colors = registrationFieldColors(),
                shape = RoundedCornerShape(10.dp)
            )

            if (errorMessage.isNotBlank()) {
                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = errorMessage,
                    color = Color(0xFFFFCDD2),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = {
                    val cleanName = fullName.trim()
                    val cleanEmail = email.trim()

                    when {
                        cleanName.isBlank() -> {
                            errorMessage = "Enter your full name."
                        }

                        cleanEmail.isBlank() -> {
                            errorMessage = "Enter your email address."
                        }

                        !Patterns.EMAIL_ADDRESS
                            .matcher(cleanEmail)
                            .matches() -> {
                            errorMessage = "Enter a valid email address."
                        }

                        password.isBlank() -> {
                            errorMessage = "Enter your password."
                        }

                        password.length < 6 -> {
                            errorMessage =
                                "Password must contain at least 6 characters."
                        }

                        confirmPassword.isBlank() -> {
                            errorMessage = "Confirm your password."
                        }

                        password != confirmPassword -> {
                            errorMessage = "Passwords do not match."
                        }

                        else -> {
                            isLoading = true
                            errorMessage = ""

                            auth.createUserWithEmailAndPassword(
                                cleanEmail,
                                password
                            ).addOnSuccessListener { result ->

                                val firebaseUser = result.user

                                if (firebaseUser == null) {
                                    isLoading = false
                                    errorMessage =
                                        "Unable to create the worker account."
                                    return@addOnSuccessListener
                                }

                                val requestCode =
                                    generateEmployeeRequestCode()

                                val workerProfile = mapOf(
                                    "fullName" to cleanName,
                                    "email" to cleanEmail,
                                    "role" to "worker",
                                    "farmId" to "",
                                    "approved" to false,
                                    "active" to true,
                                    "requestCode" to requestCode
                                )

                                val employeeRequest = mapOf(
                                    "workerUid" to firebaseUser.uid,
                                    "workerName" to cleanName,
                                    "workerEmail" to cleanEmail,
                                    "code" to requestCode,
                                    "status" to "pending",
                                    "farmId" to "",
                                    "createdAt" to System.currentTimeMillis()
                                )

                                val database =
                                    FirebaseDatabase.getInstance().reference

                                val updates = mapOf<String, Any>(
                                    "users/${firebaseUser.uid}" to workerProfile,
                                    "employeeRequests/$requestCode" to employeeRequest
                                )

                                database.updateChildren(updates)
                                    .addOnSuccessListener {
                                        isLoading = false

                                        onRegistrationSuccess(
                                            firebaseUser.uid,
                                            requestCode
                                        )
                                    }
                                    .addOnFailureListener { exception ->
                                        isLoading = false

                                        firebaseUser.delete()

                                        errorMessage =
                                            exception.message
                                                ?: "Unable to save worker registration."
                                    }
                            }.addOnFailureListener { exception ->
                                isLoading = false

                                errorMessage = when {
                                    exception.message
                                        ?.contains(
                                            "already in use",
                                            ignoreCase = true
                                        ) == true -> {
                                        "This email address is already registered."
                                    }

                                    exception.message
                                        ?.contains(
                                            "network",
                                            ignoreCase = true
                                        ) == true -> {
                                        "Check your internet connection."
                                    }

                                    else -> {
                                        exception.message
                                            ?: "Unable to create account."
                                    }
                                }
                            }
                        }
                    }
                },
                enabled = !isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF07106A),
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        text = "Register",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            TextButton(
                onClick = onBackToLogin,
                enabled = !isLoading
            ) {
                Text(
                    text = "Already have an account? Login",
                    color = Color.White
                )
            }
        }
    }
}

@Composable
fun WaitingForFarmScreen(
    workerUid: String,
    initialRequestCode: String,
    onRequestCodeChanged: (String) -> Unit,
    onBackToLogin: () -> Unit
) {
    var requestCode by remember(initialRequestCode) {
        mutableStateOf(initialRequestCode)
    }

    var isGenerating by remember {
        mutableStateOf(false)
    }

    var message by remember {
        mutableStateOf("")
    }

    var messageIsSuccess by remember {
        mutableStateOf(false)
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Blue
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 34.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Waiting for Approval",
                color = Color.White,
                fontSize = 27.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = "Show this employee request code to the farm owner.",
                color = Color.White,
                fontSize = 15.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(22.dp))

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = Color.White
                ),
                shape = RoundedCornerShape(14.dp),
                elevation = CardDefaults.cardElevation(
                    defaultElevation = 4.dp
                )
            ) {
                Text(
                    text = if (requestCode.isBlank()) "------" else requestCode,
                    modifier = Modifier.padding(
                        horizontal = 34.dp,
                        vertical = 18.dp
                    ),
                    color = Blue,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 6.sp
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "The owner can enter this code on the Employees page to connect your account to a farm.",
                color = Color.White,
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )

            if (message.isNotBlank()) {
                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = message,
                    color = if (messageIsSuccess) {
                        Color(0xFFB9F6CA)
                    } else {
                        Color(0xFFFFCDD2)
                    },
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    if (workerUid.isBlank()) {
                        messageIsSuccess = false
                        message = "Unable to identify the worker account."
                        return@Button
                    }

                    isGenerating = true
                    message = ""

                    generateNewWorkerRequestCode(
                        workerUid = workerUid,
                        oldRequestCode = requestCode,
                        onSuccess = { newCode ->
                            isGenerating = false
                            requestCode = newCode
                            onRequestCodeChanged(newCode)
                            messageIsSuccess = true
                            message = "A new request code was generated."
                        },
                        onError = { error ->
                            isGenerating = false
                            messageIsSuccess = false
                            message = error
                        }
                    )
                },
                enabled = !isGenerating,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = Blue
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (isGenerating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = Blue,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        text = if (requestCode.isBlank()) {
                            "Generate Request Code"
                        } else {
                            "Generate New Code"
                        },
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = onBackToLogin,
                enabled = !isGenerating,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF07106A),
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Back to Login")
            }
        }
    }
}

@Composable
fun registrationFieldColors() =
    OutlinedTextFieldDefaults.colors(
        focusedContainerColor = Color.White,
        unfocusedContainerColor = Color.White,
        focusedBorderColor = Color.White,
        unfocusedBorderColor = Color.White
    )

@Composable
fun LoadingScreen() {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Blue
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator(
                color = Color.White
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Loading CrayFarm...",
                color = Color.White
            )
        }
    }
}

@Composable
fun PondDashboardContent(
    user: CrayFarmUser,
    pond: PondSummary,
    onBackToPonds: () -> Unit
) {
    val context = LocalContext.current

    var readings by remember { mutableStateOf(PondReadings()) }
    var thresholds by remember { mutableStateOf(PondThresholds()) }
    var hasReadings by remember { mutableStateOf(false) }
    var hasThresholds by remember { mutableStateOf(false) }

    var activities by remember {
        mutableStateOf<List<ActivityLog>>(emptyList())
    }
    var activityError by remember { mutableStateOf("") }

    var alerts by remember {
        mutableStateOf<List<PondAlert>>(emptyList())
    }
    var alertsError by remember { mutableStateOf("") }
    var showAlertsPage by remember { mutableStateOf(false) }

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

    val activityLogsQuery = remember(user.farmId) {
        FirebaseDatabase.getInstance()
            .getReference("farms")
            .child(user.farmId)
            .child("activityLogs")
            .orderByChild("timestamp")
            .limitToLast(20)
    }

    DisposableEffect(latestReadingsRef) {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) {
                    hasReadings = false
                    return
                }

                readings = PondReadings(
                    temperature = snapshot.readNumber("temperature"),
                    ph = snapshot.readNumber("ph"),
                    turbidity = snapshot.readNumber("turbidity"),
                    tds = snapshot.readNumber("tds"),
                    waterLevel = snapshot.readNumber("waterLevel")
                )

                hasReadings = true
            }

            override fun onCancelled(error: DatabaseError) {
                hasReadings = false
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
                    minWaterLevel = snapshot.readNumberOrDefault(
                        "minWaterLevel",
                        40.0
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

    DisposableEffect(alertsQuery) {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val loadedAlerts = snapshot.children.map { child ->
                    PondAlert(
                        id = child.key.orEmpty(),
                        type = child.child("type")
                            .getValue(String::class.java)
                            .orEmpty(),
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
                            .orEmpty()
                    )
                }.sortedByDescending { alert ->
                    alert.createdAt
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
                                    title = alert.title,
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

    DisposableEffect(activityLogsQuery) {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val allActivities = snapshot.children.map { child ->
                    ActivityLog(
                        id = child.key.orEmpty(),
                        type = child.child("type")
                            .getValue(String::class.java)
                            .orEmpty(),
                        title = child.child("title")
                            .getValue(String::class.java)
                            .orEmpty(),
                        message = child.child("message")
                            .getValue(String::class.java)
                            .orEmpty(),
                        actorUid = child.child("actorUid")
                            .getValue(String::class.java)
                            .orEmpty(),
                        actorName = child.child("actorName")
                            .getValue(String::class.java)
                            .orEmpty(),
                        targetUid = child.child("targetUid")
                            .getValue(String::class.java)
                            .orEmpty(),
                        targetName = child.child("targetName")
                            .getValue(String::class.java)
                            .orEmpty(),
                        timestamp = child.child("timestamp")
                            .getValue(Long::class.java)
                            ?: 0L
                    )
                }

                activities = if (user.role == "admin") {
                    allActivities.sortedByDescending { it.timestamp }
                } else {
                    allActivities
                        .filter { activity ->
                            activity.actorUid == user.uid ||
                                    activity.targetUid == user.uid
                        }
                        .sortedByDescending { it.timestamp }
                }

                activityError = ""
            }

            override fun onCancelled(error: DatabaseError) {
                activityError = error.message
            }
        }

        activityLogsQuery.addValueEventListener(listener)

        onDispose {
            activityLogsQuery.removeEventListener(listener)
        }
    }

    LaunchedEffect(
        hasReadings,
        hasThresholds,
        readings,
        thresholds,
        user.farmId,
        pond.pondId
    ) {
        if (hasReadings && hasThresholds) {
            evaluateAndCreatePondAlerts(
                user = user,
                pond = pond,
                readings = readings,
                thresholds = thresholds
            )
        }
    }

    val tempStatus = rangeStatus(
        value = readings.temperature,
        minimum = thresholds.minTemperature,
        maximum = thresholds.maxTemperature,
        lowLabel = "Low",
        highLabel = "High"
    )

    val phReadingStatus = rangeStatus(
        value = readings.ph,
        minimum = thresholds.minPh,
        maximum = thresholds.maxPh,
        lowLabel = "Acidic",
        highLabel = "Alkaline"
    )

    val turbidityStatus = maximumStatus(
        value = readings.turbidity,
        maximum = thresholds.maxTurbidity,
        highLabel = "High"
    )

    val tdsStatus = rangeStatus(
        value = readings.tds,
        minimum = thresholds.minTds,
        maximum = thresholds.maxTds,
        lowLabel = "Low",
        highLabel = "High"
    )

    val waterLevelStatus = minimumStatus(
        value = readings.waterLevel,
        minimum = thresholds.minWaterLevel,
        lowLabel = "Low"
    )

    val allSystemsSafe = listOf(
        tempStatus,
        phReadingStatus,
        turbidityStatus,
        tdsStatus,
        waterLevelStatus
    ).all { status ->
        status.first == "Safe"
    }

    val unreadAlertCount = alerts.count { alert ->
        !alert.acknowledged
    }

    if (showAlertsPage) {
        PondAlertsScreen(
            user = user,
            pond = pond,
            alerts = alerts,
            errorMessage = alertsError,
            onBack = {
                showAlertsPage = false
            }
        )

        return
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = LightBlue
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            DashboardHeader(
                pondName = pond.pondName,
                unreadAlertCount = unreadAlertCount,
                onBackToPonds = onBackToPonds,
                onAlertsClick = {
                    showAlertsPage = true
                }
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SystemStatus(
                    allSystemsSafe = allSystemsSafe
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SensorCard(
                        modifier = Modifier.weight(1f),
                        icon = "♨",
                        title = "Temperature",
                        value = String.format("%.1f", readings.temperature),
                        unit = "°C",
                        status = tempStatus.first,
                        statusColor = tempStatus.second
                    )

                    SensorCard(
                        modifier = Modifier.weight(1f),
                        icon = "pH",
                        title = "pH Level",
                        value = String.format("%.1f", readings.ph),
                        unit = "",
                        status = phReadingStatus.first,
                        statusColor = phReadingStatus.second
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SensorCard(
                        modifier = Modifier.weight(1f),
                        icon = "◒",
                        title = "Turbidity",
                        value = String.format("%.1f", readings.turbidity),
                        unit = "%",
                        status = turbidityStatus.first,
                        statusColor = turbidityStatus.second
                    )

                    SensorCard(
                        modifier = Modifier.weight(1f),
                        icon = "TDS",
                        title = "TDS",
                        value = String.format("%.0f", readings.tds),
                        unit = "ppm",
                        status = tdsStatus.first,
                        statusColor = tdsStatus.second
                    )
                }

                WaterLevelCard(
                    waterLevel = readings.waterLevel,
                    status = waterLevelStatus.first,
                    statusColor = waterLevelStatus.second
                )

                Text(
                    text = "Recent activities",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF1D1B20)
                )

                when {
                    activityError.isNotBlank() -> {
                        Text(
                            text = "Unable to load activities: $activityError",
                            modifier = Modifier.fillMaxWidth(),
                            color = Color(0xFFC62828),
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )
                    }

                    activities.isEmpty() -> {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = Color.White
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "No activity has been recorded yet.",
                                modifier = Modifier.padding(14.dp),
                                color = Color.Gray,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    else -> {
                        activities.take(3).forEach { activity ->
                            ActivityCard(
                                title = activity.title,
                                message = activity.message,
                                time = formatActivityTime(activity.timestamp),
                                color = activityColor(activity.type)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DashboardHeader(
    pondName: String,
    unreadAlertCount: Int,
    onBackToPonds: () -> Unit,
    onAlertsClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Blue)
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier
                .size(48.dp)
                .clickable {
                    onBackToPonds()
                },
            color = Color.Transparent,
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "←",
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.width(4.dp))

        Text(
            text = pondName,
            modifier = Modifier.weight(1f),
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )

        Box(
            modifier = Modifier
                .size(48.dp)
                .clickable {
                    onAlertsClick()
                }
        ) {
            Text(
                text = "🔔",
                modifier = Modifier.align(Alignment.Center),
                fontSize = 22.sp
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
    onBack: () -> Unit
) {
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
                    .background(Blue)
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier
                        .size(48.dp)
                        .clickable {
                            onBack()
                        },
                    color = Color.Transparent,
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "←",
                            color = Color.White,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.width(4.dp))

                Column {
                    Text(
                        text = "Alerts",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = pond.pondName,
                        color = Color(0xFFDDE3FF),
                        fontSize = 12.sp
                    )
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

                alerts.isEmpty() -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "✓",
                            color = SafeGreen,
                            fontSize = 44.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "No alerts for this pond.",
                            color = Color.Gray,
                            fontSize = 15.sp
                        )
                    }
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        item {
                            Spacer(modifier = Modifier.height(4.dp))
                        }

                        items(
                            items = alerts,
                            key = { alert -> alert.id }
                        ) { alert ->
                            PondAlertCard(
                                user = user,
                                pond = pond,
                                alert = alert
                            )
                        }

                        item {
                            Spacer(modifier = Modifier.height(12.dp))
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

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.size(34.dp),
                    color = if (alert.acknowledged) {
                        SafeGreen.copy(alpha = 0.14f)
                    } else {
                        WarningOrange.copy(alpha = 0.16f)
                    },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (alert.acknowledged) "✓" else "!",
                            color = if (alert.acknowledged) {
                                SafeGreen
                            } else {
                                WarningOrange
                            },
                            fontWeight = FontWeight.Bold,
                            fontSize = 19.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = alert.title,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = formatActivityTime(alert.createdAt),
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                }

                Text(
                    text = if (alert.acknowledged) {
                        "Acknowledged"
                    } else {
                        "New"
                    },
                    color = if (alert.acknowledged) {
                        SafeGreen
                    } else {
                        Color(0xFFC62828)
                    },
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = alert.message,
                fontSize = 14.sp,
                color = Color(0xFF333333)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Suggested action",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Blue
            )

            Spacer(modifier = Modifier.height(3.dp))

            Text(
                text = alert.suggestedAction,
                fontSize = 13.sp,
                color = Color.Gray
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
                Spacer(modifier = Modifier.height(12.dp))

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
                        .height(44.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Blue,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(9.dp)
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
    allSystemsSafe: Boolean
) {
    val statusColor = if (allSystemsSafe) {
        SafeGreen
    } else {
        WarningOrange
    }

    val statusIcon = if (allSystemsSafe) {
        "✓"
    } else {
        "!"
    }

    val statusText = if (allSystemsSafe) {
        "Systems are normal"
    } else {
        "One or more readings need attention"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(11.dp),
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
                fontSize = 12.sp,
                color = statusColor,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun SensorCard(
    modifier: Modifier = Modifier,
    icon: String,
    title: String,
    value: String,
    unit: String,
    status: String,
    statusColor: Color
) {
    Card(
        modifier = modifier.height(115.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        shape = RoundedCornerShape(10.dp)
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
                Text(
                    text = icon,
                    fontSize = if (icon == "pH" || icon == "TDS") {
                        16.sp
                    } else {
                        24.sp
                    },
                    fontWeight = FontWeight.Medium
                )

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

@Composable
fun WaterLevelCard(
    waterLevel: Double,
    status: String,
    statusColor: Color
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(92.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        shape = RoundedCornerShape(10.dp)
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
                Text(
                    text = "≈≈≈",
                    fontSize = 30.sp,
                    color = Blue
                )

                Spacer(modifier = Modifier.width(14.dp))

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "${waterLevel.toInt()}%",
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

@Composable
fun PondAnalyticsScreen(
    user: CrayFarmUser,
    pond: PondSummary
) {
    var historyRecords by remember {
        mutableStateOf<List<PondHistoryRecord>>(emptyList())
    }

    var selectedRange by remember {
        mutableStateOf(AnalyticsRange.DAY)
    }

    var selectedSensor by remember {
        mutableStateOf(AnalyticsSensor.TEMPERATURE)
    }

    var isLoading by remember {
        mutableStateOf(true)
    }

    var errorMessage by remember {
        mutableStateOf("")
    }

    val historyRef = remember(
        user.farmId,
        pond.pondId
    ) {
        FirebaseDatabase.getInstance()
            .getReference("farms")
            .child(user.farmId)
            .child("ponds")
            .child(pond.pondId)
            .child("history")
    }

    DisposableEffect(historyRef) {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                historyRecords = snapshot.children.mapNotNull { child ->
                    val recordedAt = child.child("recordedAt")
                        .getValue(Long::class.java)
                        ?: child.key?.toLongOrNull()
                        ?: 0L

                    if (recordedAt <= 0L) {
                        null
                    } else {
                        PondHistoryRecord(
                            id = child.key.orEmpty(),
                            temperature = child.readNumber("temperature"),
                            ph = child.readNumber("ph"),
                            turbidity = child.readNumber("turbidity"),
                            tds = child.readNumber("tds"),
                            waterLevel = child.readNumber("waterLevel"),
                            recordedAt = recordedAt
                        )
                    }
                }.sortedBy { record ->
                    record.recordedAt
                }

                isLoading = false
                errorMessage = ""
            }

            override fun onCancelled(error: DatabaseError) {
                isLoading = false
                errorMessage = error.message
            }
        }

        historyRef.addValueEventListener(listener)

        onDispose {
            historyRef.removeEventListener(listener)
        }
    }

    val rangeStart =
        System.currentTimeMillis() - selectedRange.durationMillis

    val filteredRecords = historyRecords.filter { record ->
        record.recordedAt >= rangeStart
    }

    val chartValues = filteredRecords.map { record ->
        when (selectedSensor) {
            AnalyticsSensor.TEMPERATURE -> record.temperature
            AnalyticsSensor.PH -> record.ph
            AnalyticsSensor.TURBIDITY -> record.turbidity
            AnalyticsSensor.TDS -> record.tds
            AnalyticsSensor.WATER_LEVEL -> record.waterLevel
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = LightBlue
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            PageHeader(
                title = "${pond.pondName} Analytics"
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Spacer(modifier = Modifier.height(4.dp))
                }

                item {
                    Text(
                        text = "Time range",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AnalyticsRange.entries.forEach { range ->
                            AnalyticsChoiceButton(
                                modifier = Modifier.weight(1f),
                                label = range.label,
                                selected = selectedRange == range,
                                onClick = {
                                    selectedRange = range
                                }
                            )
                        }
                    }
                }

                item {
                    Text(
                        text = "Sensor",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                item {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier.height(116.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(
                            items = AnalyticsSensor.entries,
                            key = { sensor -> sensor.name }
                        ) { sensor ->
                            AnalyticsChoiceButton(
                                modifier = Modifier.fillMaxWidth(),
                                label = sensor.label,
                                selected = selectedSensor == sensor,
                                onClick = {
                                    selectedSensor = sensor
                                }
                            )
                        }
                    }
                }

                when {
                    isLoading -> {
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 48.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                CircularProgressIndicator(
                                    color = Blue
                                )
                            }
                        }
                    }

                    errorMessage.isNotBlank() -> {
                        item {
                            Text(
                                text = "Unable to load history: $errorMessage",
                                modifier = Modifier.fillMaxWidth(),
                                color = Color(0xFFC62828),
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    filteredRecords.isEmpty() -> {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = Color.White
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(22.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "No history records yet",
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Bold
                                    )

                                    Spacer(modifier = Modifier.height(7.dp))

                                    Text(
                                        text = "The ESP32 saves one history record every 15 minutes. Keep it online and check again later.",
                                        color = Color.Gray,
                                        fontSize = 13.sp,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }

                    else -> {
                        item {
                            AnalyticsSummaryCard(
                                sensor = selectedSensor,
                                values = chartValues,
                                recordCount = filteredRecords.size
                            )
                        }

                        item {
                            AnalyticsChartCard(
                                sensor = selectedSensor,
                                records = filteredRecords,
                                values = chartValues,
                                selectedRange = selectedRange
                            )
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }
    }
}

@Composable
fun AnalyticsChoiceButton(
    modifier: Modifier,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .height(42.dp)
            .clickable {
                onClick()
            },
        color = if (selected) {
            Blue
        } else {
            Color.White
        },
        shape = RoundedCornerShape(10.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                color = if (selected) {
                    Color.White
                } else {
                    Color(0xFF333333)
                },
                fontSize = 11.sp,
                fontWeight = if (selected) {
                    FontWeight.Bold
                } else {
                    FontWeight.Normal
                },
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun AnalyticsSummaryCard(
    sensor: AnalyticsSensor,
    values: List<Double>,
    recordCount: Int
) {
    val minimum = values.minOrNull() ?: 0.0
    val maximum = values.maxOrNull() ?: 0.0
    val average = values.average()

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = sensor.label,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = Blue
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                AnalyticsMetric(
                    label = "Minimum",
                    value = formatAnalyticsValue(
                        value = minimum,
                        unit = sensor.unit
                    )
                )

                AnalyticsMetric(
                    label = "Average",
                    value = formatAnalyticsValue(
                        value = average,
                        unit = sensor.unit
                    )
                )

                AnalyticsMetric(
                    label = "Maximum",
                    value = formatAnalyticsValue(
                        value = maximum,
                        unit = sensor.unit
                    )
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "$recordCount history records",
                color = Color.Gray,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
fun AnalyticsMetric(
    label: String,
    value: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            color = Color.Gray,
            fontSize = 10.sp
        )

        Spacer(modifier = Modifier.height(3.dp))

        Text(
            text = value,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun AnalyticsChartCard(
    sensor: AnalyticsSensor,
    records: List<PondHistoryRecord>,
    values: List<Double>,
    selectedRange: AnalyticsRange
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "${sensor.label} Trend",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = selectedRange.label,
                color = Color.Gray,
                fontSize = 11.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            SimpleLineChart(
                values = values,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = formatAnalyticsDate(
                        records.first().recordedAt,
                        selectedRange
                    ),
                    color = Color.Gray,
                    fontSize = 10.sp
                )

                Text(
                    text = formatAnalyticsDate(
                        records.last().recordedAt,
                        selectedRange
                    ),
                    color = Color.Gray,
                    fontSize = 10.sp
                )
            }
        }
    }
}

@Composable
fun SimpleLineChart(
    values: List<Double>,
    modifier: Modifier = Modifier
) {
    val minimum = values.minOrNull() ?: 0.0
    val maximum = values.maxOrNull() ?: 0.0
    val range = (maximum - minimum).takeIf { difference ->
        difference > 0.0
    } ?: 1.0

    Canvas(
        modifier = modifier
    ) {
        val leftPadding = 8.dp.toPx()
        val rightPadding = 8.dp.toPx()
        val topPadding = 12.dp.toPx()
        val bottomPadding = 12.dp.toPx()

        val chartWidth =
            size.width - leftPadding - rightPadding

        val chartHeight =
            size.height - topPadding - bottomPadding

        for (line in 0..4) {
            val y =
                topPadding +
                        chartHeight * line / 4f

            drawLine(
                color = Color(0xFFE1E5EC),
                start = Offset(leftPadding, y),
                end = Offset(
                    leftPadding + chartWidth,
                    y
                ),
                strokeWidth = 1.dp.toPx()
            )
        }

        if (values.size == 1) {
            val normalized =
                ((values.first() - minimum) / range)
                    .toFloat()

            val y =
                topPadding +
                        chartHeight * (1f - normalized)

            drawCircle(
                color = Blue,
                radius = 5.dp.toPx(),
                center = Offset(
                    leftPadding + chartWidth / 2f,
                    y
                )
            )

            return@Canvas
        }

        val path = Path()

        values.forEachIndexed { index, value ->
            val x =
                leftPadding +
                        chartWidth *
                        index.toFloat() /
                        (values.size - 1).toFloat()

            val normalized =
                ((value - minimum) / range)
                    .toFloat()

            val y =
                topPadding +
                        chartHeight * (1f - normalized)

            if (index == 0) {
                path.moveTo(x, y)
            } else {
                path.lineTo(x, y)
            }
        }

        drawPath(
            path = path,
            color = Blue,
            style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = 3.dp.toPx(),
                cap = StrokeCap.Round
            )
        )

        val lastIndex = values.lastIndex
        val lastNormalized =
            ((values[lastIndex] - minimum) / range)
                .toFloat()

        val lastY =
            topPadding +
                    chartHeight * (1f - lastNormalized)

        drawCircle(
            color = Blue,
            radius = 4.dp.toPx(),
            center = Offset(
                leftPadding + chartWidth,
                lastY
            )
        )
    }
}

private fun formatAnalyticsValue(
    value: Double,
    unit: String
): String {
    val number = if (value % 1.0 == 0.0) {
        value.toInt().toString()
    } else {
        String.format(
            Locale.getDefault(),
            "%.1f",
            value
        )
    }

    return if (unit.isBlank()) {
        number
    } else {
        "$number $unit"
    }
}

private fun formatAnalyticsDate(
    timestamp: Long,
    range: AnalyticsRange
): String {
    val pattern = when (range) {
        AnalyticsRange.DAY -> "h:mm a"
        AnalyticsRange.WEEK -> "EEE, MMM d"
        AnalyticsRange.MONTH -> "MMM d"
    }

    return SimpleDateFormat(
        pattern,
        Locale.getDefault()
    ).format(Date(timestamp))
}

@Composable
fun PondReportsScreen(
    user: CrayFarmUser,
    pond: PondSummary
) {
    val context = LocalContext.current

    var selectedRange by remember {
        mutableStateOf(AnalyticsRange.DAY)
    }

    var historyRecords by remember {
        mutableStateOf<List<PondHistoryRecord>>(emptyList())
    }

    var alerts by remember {
        mutableStateOf<List<PondAlert>>(emptyList())
    }

    var historyLoaded by remember {
        mutableStateOf(false)
    }

    var alertsLoaded by remember {
        mutableStateOf(false)
    }

    var errorMessage by remember {
        mutableStateOf("")
    }

    var pendingCsvContent by remember {
        mutableStateOf("")
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(
            "text/csv"
        )
    ) { uri ->
        if (uri == null) {
            return@rememberLauncherForActivityResult
        }

        try {
            context.contentResolver
                .openOutputStream(uri)
                ?.bufferedWriter()
                ?.use { writer ->
                    writer.write(pendingCsvContent)
                }

            Toast.makeText(
                context,
                "CSV report saved successfully.",
                Toast.LENGTH_SHORT
            ).show()
        } catch (exception: Exception) {
            Toast.makeText(
                context,
                exception.message
                    ?: "Unable to save the CSV report.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    val historyRef = remember(
        user.farmId,
        pond.pondId
    ) {
        FirebaseDatabase.getInstance()
            .getReference("farms")
            .child(user.farmId)
            .child("ponds")
            .child(pond.pondId)
            .child("history")
    }

    val alertsRef = remember(
        user.farmId,
        pond.pondId
    ) {
        FirebaseDatabase.getInstance()
            .getReference("farms")
            .child(user.farmId)
            .child("ponds")
            .child(pond.pondId)
            .child("alerts")
    }

    DisposableEffect(historyRef) {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                historyRecords = snapshot.children.mapNotNull { child ->
                    val recordedAt = child.child("recordedAt")
                        .getValue(Long::class.java)
                        ?: child.key?.toLongOrNull()
                        ?: 0L

                    if (recordedAt <= 0L) {
                        null
                    } else {
                        PondHistoryRecord(
                            id = child.key.orEmpty(),
                            temperature = child.readNumber("temperature"),
                            ph = child.readNumber("ph"),
                            turbidity = child.readNumber("turbidity"),
                            tds = child.readNumber("tds"),
                            waterLevel = child.readNumber("waterLevel"),
                            recordedAt = recordedAt
                        )
                    }
                }.sortedBy { record ->
                    record.recordedAt
                }

                historyLoaded = true
            }

            override fun onCancelled(error: DatabaseError) {
                historyLoaded = true
                errorMessage = error.message
            }
        }

        historyRef.addValueEventListener(listener)

        onDispose {
            historyRef.removeEventListener(listener)
        }
    }

    DisposableEffect(alertsRef) {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                alerts = snapshot.children.map { child ->
                    PondAlert(
                        id = child.key.orEmpty(),
                        type = child.child("type")
                            .getValue(String::class.java)
                            .orEmpty(),
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
                            .orEmpty()
                    )
                }.sortedByDescending { alert ->
                    alert.createdAt
                }

                alertsLoaded = true
            }

            override fun onCancelled(error: DatabaseError) {
                alertsLoaded = true
                errorMessage = error.message
            }
        }

        alertsRef.addValueEventListener(listener)

        onDispose {
            alertsRef.removeEventListener(listener)
        }
    }

    val rangeStart =
        System.currentTimeMillis() - selectedRange.durationMillis

    val filteredHistory = historyRecords.filter { record ->
        record.recordedAt >= rangeStart
    }

    val filteredAlerts = alerts.filter { alert ->
        alert.createdAt >= rangeStart
    }

    val isLoading =
        !historyLoaded || !alertsLoaded

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = LightBlue
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            PageHeader(
                title = "${pond.pondName} Reports"
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Spacer(modifier = Modifier.height(4.dp))
                }

                item {
                    Text(
                        text = "Report period",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AnalyticsRange.entries.forEach { range ->
                            AnalyticsChoiceButton(
                                modifier = Modifier.weight(1f),
                                label = range.label,
                                selected = selectedRange == range,
                                onClick = {
                                    selectedRange = range
                                }
                            )
                        }
                    }
                }

                when {
                    isLoading -> {
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 50.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                CircularProgressIndicator(
                                    color = Blue
                                )
                            }
                        }
                    }

                    errorMessage.isNotBlank() -> {
                        item {
                            Text(
                                text = "Unable to load report data: $errorMessage",
                                modifier = Modifier.fillMaxWidth(),
                                color = Color(0xFFC62828),
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    else -> {
                        item {
                            ReportOverviewCard(
                                selectedRange = selectedRange,
                                historyCount = filteredHistory.size,
                                alertCount = filteredAlerts.size,
                                acknowledgedCount =
                                    filteredAlerts.count { alert ->
                                        alert.acknowledged
                                    }
                            )
                        }

                        item {
                            Text(
                                text = "Sensor summary",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (filteredHistory.isEmpty()) {
                            item {
                                ReportEmptyCard(
                                    message =
                                        "No history records are available for this period."
                                )
                            }
                        } else {
                            item {
                                ReportSensorSummaryCard(
                                    records = filteredHistory
                                )
                            }
                        }

                        item {
                            Text(
                                text = "Alert summary",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (filteredAlerts.isEmpty()) {
                            item {
                                ReportEmptyCard(
                                    message =
                                        "No alerts were recorded during this period."
                                )
                            }
                        } else {
                            item {
                                ReportAlertSummaryCard(
                                    alerts = filteredAlerts
                                )
                            }
                        }

                        item {
                            Button(
                                onClick = {
                                    if (
                                        filteredHistory.isEmpty() &&
                                        filteredAlerts.isEmpty()
                                    ) {
                                        Toast.makeText(
                                            context,
                                            "There is no report data to export.",
                                            Toast.LENGTH_SHORT
                                        ).show()

                                        return@Button
                                    }

                                    pendingCsvContent =
                                        buildPondReportCsv(
                                            pond = pond,
                                            selectedRange = selectedRange,
                                            historyRecords = filteredHistory,
                                            alerts = filteredAlerts
                                        )

                                    val dateText = SimpleDateFormat(
                                        "yyyy-MM-dd",
                                        Locale.getDefault()
                                    ).format(Date())

                                    val safePondName =
                                        pond.pondName
                                            .replace(
                                                Regex("[^A-Za-z0-9_-]"),
                                                "_"
                                            )
                                            .ifBlank {
                                                "pond"
                                            }

                                    exportLauncher.launch(
                                        "CrayFarm_${safePondName}_${selectedRange.name}_$dateText.csv"
                                    )
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Blue,
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text(
                                    text = "Export Report as CSV",
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        item {
                            Text(
                                text = "The CSV contains the report summary, all sensor-history records, and all alerts within the selected period.",
                                modifier = Modifier.fillMaxWidth(),
                                color = Color.Gray,
                                fontSize = 11.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }
    }
}

@Composable
fun ReportOverviewCard(
    selectedRange: AnalyticsRange,
    historyCount: Int,
    alertCount: Int,
    acknowledgedCount: Int
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = selectedRange.label,
                color = Blue,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                AnalyticsMetric(
                    label = "Records",
                    value = historyCount.toString()
                )

                AnalyticsMetric(
                    label = "Alerts",
                    value = alertCount.toString()
                )

                AnalyticsMetric(
                    label = "Acknowledged",
                    value = acknowledgedCount.toString()
                )
            }
        }
    }
}

@Composable
fun ReportSensorSummaryCard(
    records: List<PondHistoryRecord>
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ReportSensorSummaryRow(
                label = "Temperature",
                unit = "°C",
                values = records.map { record ->
                    record.temperature
                }
            )

            ReportSensorSummaryRow(
                label = "pH",
                unit = "",
                values = records.map { record ->
                    record.ph
                }
            )

            ReportSensorSummaryRow(
                label = "Turbidity",
                unit = "%",
                values = records.map { record ->
                    record.turbidity
                }
            )

            ReportSensorSummaryRow(
                label = "TDS",
                unit = "ppm",
                values = records.map { record ->
                    record.tds
                }
            )

            ReportSensorSummaryRow(
                label = "Water Level",
                unit = "%",
                values = records.map { record ->
                    record.waterLevel
                }
            )
        }
    }
}

@Composable
fun ReportSensorSummaryRow(
    label: String,
    unit: String,
    values: List<Double>
) {
    val minimum = values.minOrNull() ?: 0.0
    val average = values.average()
    val maximum = values.maxOrNull() ?: 0.0

    Column {
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(5.dp))

        Text(
            text =
                "Min ${formatAnalyticsValue(minimum, unit)}  •  " +
                        "Avg ${formatAnalyticsValue(average, unit)}  •  " +
                        "Max ${formatAnalyticsValue(maximum, unit)}",
            color = Color.Gray,
            fontSize = 12.sp
        )
    }
}

@Composable
fun ReportAlertSummaryCard(
    alerts: List<PondAlert>
) {
    val alertGroups = alerts
        .groupingBy { alert ->
            alert.title.ifBlank {
                alert.type.replace("_", " ")
            }
        }
        .eachCount()
        .toList()
        .sortedByDescending { pair ->
            pair.second
        }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            alertGroups.forEach { (title, count) ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = title,
                        modifier = Modifier.weight(1f),
                        fontSize = 13.sp
                    )

                    Text(
                        text = count.toString(),
                        color = Color(0xFFC62828),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun ReportEmptyCard(
    message: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Text(
            text = message,
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            color = Color.Gray,
            fontSize = 13.sp,
            textAlign = TextAlign.Center
        )
    }
}

private fun buildPondReportCsv(
    pond: PondSummary,
    selectedRange: AnalyticsRange,
    historyRecords: List<PondHistoryRecord>,
    alerts: List<PondAlert>
): String {
    val builder = StringBuilder()
    val generatedAt = SimpleDateFormat(
        "yyyy-MM-dd HH:mm:ss",
        Locale.getDefault()
    ).format(Date())

    builder.appendLine("CRAYFARM POND REPORT")
    builder.appendLine(
        "Pond,${csvEscape(pond.pondName)}"
    )
    builder.appendLine(
        "Report Period,${csvEscape(selectedRange.label)}"
    )
    builder.appendLine(
        "Generated At,${csvEscape(generatedAt)}"
    )
    builder.appendLine(
        "History Records,${historyRecords.size}"
    )
    builder.appendLine(
        "Alerts,${alerts.size}"
    )
    builder.appendLine(
        "Acknowledged Alerts,${alerts.count { alert -> alert.acknowledged }}"
    )
    builder.appendLine()

    builder.appendLine("SENSOR SUMMARY")
    builder.appendLine(
        "Sensor,Unit,Minimum,Average,Maximum"
    )

    appendSensorSummaryCsv(
        builder = builder,
        label = "Temperature",
        unit = "C",
        values = historyRecords.map { record ->
            record.temperature
        }
    )

    appendSensorSummaryCsv(
        builder = builder,
        label = "pH",
        unit = "",
        values = historyRecords.map { record ->
            record.ph
        }
    )

    appendSensorSummaryCsv(
        builder = builder,
        label = "Relative Turbidity",
        unit = "%",
        values = historyRecords.map { record ->
            record.turbidity
        }
    )

    appendSensorSummaryCsv(
        builder = builder,
        label = "TDS",
        unit = "ppm",
        values = historyRecords.map { record ->
            record.tds
        }
    )

    appendSensorSummaryCsv(
        builder = builder,
        label = "Water Level",
        unit = "%",
        values = historyRecords.map { record ->
            record.waterLevel
        }
    )

    builder.appendLine()
    builder.appendLine("SENSOR HISTORY")
    builder.appendLine(
        "Recorded At,Temperature C,pH,Relative Turbidity %,TDS ppm,Water Level %"
    )

    val dateFormatter = SimpleDateFormat(
        "yyyy-MM-dd HH:mm:ss",
        Locale.getDefault()
    )

    historyRecords.forEach { record ->
        builder.appendLine(
            listOf(
                dateFormatter.format(
                    Date(record.recordedAt)
                ),
                record.temperature.toString(),
                record.ph.toString(),
                record.turbidity.toString(),
                record.tds.toString(),
                record.waterLevel.toString()
            ).joinToString(",") { value ->
                csvEscape(value)
            }
        )
    }

    builder.appendLine()
    builder.appendLine("ALERTS")
    builder.appendLine(
        "Created At,Type,Sensor,Title,Value,Threshold,Unit,Acknowledged,Acknowledged At,Message,Suggested Action"
    )

    alerts.sortedBy { alert ->
        alert.createdAt
    }.forEach { alert ->
        val acknowledgedAtText =
            if (alert.acknowledgedAt > 0L) {
                dateFormatter.format(
                    Date(alert.acknowledgedAt)
                )
            } else {
                ""
            }

        builder.appendLine(
            listOf(
                dateFormatter.format(
                    Date(alert.createdAt)
                ),
                alert.type,
                alert.sensor,
                alert.title,
                alert.value.toString(),
                alert.threshold.toString(),
                alert.unit,
                alert.acknowledged.toString(),
                acknowledgedAtText,
                alert.message,
                alert.suggestedAction
            ).joinToString(",") { value ->
                csvEscape(value)
            }
        )
    }

    return builder.toString()
}

private fun appendSensorSummaryCsv(
    builder: StringBuilder,
    label: String,
    unit: String,
    values: List<Double>
) {
    if (values.isEmpty()) {
        builder.appendLine(
            listOf(
                label,
                unit,
                "",
                "",
                ""
            ).joinToString(",") { value ->
                csvEscape(value)
            }
        )

        return
    }

    builder.appendLine(
        listOf(
            label,
            unit,
            values.minOrNull().toString(),
            values.average().toString(),
            values.maxOrNull().toString()
        ).joinToString(",") { value ->
            csvEscape(value)
        }
    )
}

private fun csvEscape(value: String): String {
    val escaped = value.replace(
        "\"",
        "\"\""
    )

    return "\"$escaped\""
}

@Composable
fun BottomNavigation(
    selectedPage: AppPage,
    userRole: String,
    onPageSelected: (AppPage) -> Unit
) {
    val thirdLabel = if (userRole == "admin") {
        "Employees"
    } else {
        "Profile"
    }

    val thirdIcon = if (userRole == "admin") {
        "♟"
    } else {
        "●"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        NavigationItem(
            icon = "⌂",
            label = "Dashboard",
            selected = selectedPage == AppPage.DASHBOARD,
            onClick = {
                onPageSelected(AppPage.DASHBOARD)
            }
        )

        NavigationItem(
            icon = "⌁",
            label = "Analytics",
            selected = selectedPage == AppPage.ANALYTICS,
            onClick = {
                onPageSelected(AppPage.ANALYTICS)
            }
        )

        NavigationItem(
            icon = "▤",
            label = "Reports",
            selected = selectedPage == AppPage.REPORTS,
            onClick = {
                onPageSelected(AppPage.REPORTS)
            }
        )

        NavigationItem(
            icon = thirdIcon,
            label = thirdLabel,
            selected = selectedPage == AppPage.PROFILE_OR_EMPLOYEES,
            onClick = {
                onPageSelected(AppPage.PROFILE_OR_EMPLOYEES)
            }
        )

        NavigationItem(
            icon = "⚙",
            label = "Settings",
            selected = selectedPage == AppPage.SETTINGS,
            onClick = {
                onPageSelected(AppPage.SETTINGS)
            }
        )
    }
}

@Composable
fun NavigationItem(
    icon: String,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clickable {
                onClick()
            }
            .padding(horizontal = 5.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = icon,
            color = if (selected) Blue else Color(0xFF282828),
            fontSize = 23.sp
        )

        Text(
            text = label,
            color = if (selected) Blue else Color(0xFF282828),
            fontSize = 9.sp,
            fontWeight = if (selected) {
                FontWeight.Bold
            } else {
                FontWeight.Normal
            }
        )
    }
}

@Composable
fun PageHeader(title: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Blue)
            .statusBarsPadding()
            .padding(horizontal = 18.dp, vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
    }
}


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
    var employeeToRemove by remember {
        mutableStateOf<EmployeeSummary?>(null)
    }
    var employeeToRestore by remember {
        mutableStateOf<EmployeeSummary?>(null)
    }
    var message by remember { mutableStateOf("") }
    var messageIsSuccess by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var isChangingEmployee by remember { mutableStateOf(false) }

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

    DisposableEffect(
        activeWorkersQuery,
        removedWorkersQuery,
        admin.farmId
    ) {
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

                        EmployeeSummary(
                            uid = child.key.orEmpty(),
                            fullName = child.child("fullName")
                                .getValue(String::class.java)
                                .orEmpty(),
                            email = child.child("email")
                                .getValue(String::class.java)
                                .orEmpty()
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
                formerEmployees = snapshot.children
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
                            employmentStatus != "removed"
                        ) {
                            return@mapNotNull null
                        }

                        EmployeeSummary(
                            uid = child.key.orEmpty(),
                            fullName = child.child("fullName")
                                .getValue(String::class.java)
                                .orEmpty(),
                            email = child.child("email")
                                .getValue(String::class.java)
                                .orEmpty()
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

        activeWorkersQuery.addValueEventListener(activeListener)
        removedWorkersQuery.addValueEventListener(removedListener)

        onDispose {
            activeWorkersQuery.removeEventListener(activeListener)
            removedWorkersQuery.removeEventListener(removedListener)
        }
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
                    Text(
                        text = "Current employees (${currentEmployees.size})",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF222222)
                    )
                }

                if (currentEmployees.isEmpty()) {
                    item {
                        EmptyEmployeeCard(
                            text = "No workers are currently connected to this farm."
                        )
                    }
                } else {
                    items(
                        items = currentEmployees,
                        key = { employee -> "active_${employee.uid}" }
                    ) { employee ->
                        EmployeeCard(
                            employee = employee,
                            actionLabel = "Remove",
                            actionColor = Color(0xFFC62828),
                            onActionClick = {
                                employeeToRemove = employee
                            }
                        )
                    }
                }

                item {
                    Text(
                        text = "Former employees (${formerEmployees.size})",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF222222)
                    )
                }

                if (formerEmployees.isEmpty()) {
                    item {
                        EmptyEmployeeCard(
                            text = "No former employees are available to restore."
                        )
                    }
                } else {
                    items(
                        items = formerEmployees,
                        key = { employee -> "former_${employee.uid}" }
                    ) { employee ->
                        EmployeeCard(
                            employee = employee,
                            actionLabel = "Restore",
                            actionColor = SafeGreen,
                            onActionClick = {
                                employeeToRestore = employee
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

@Composable
fun EmployeeCard(
    employee: EmployeeSummary,
    actionLabel: String,
    actionColor: Color,
    onActionClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
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
                    text = "Worker",
                    fontSize = 12.sp,
                    color = Blue,
                    fontWeight = FontWeight.Medium
                )
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

private fun approveEmployeeRequest(
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

    val updates = mapOf<String, Any>(
        "users/${request.workerUid}/farmId" to admin.farmId,
        "users/${request.workerUid}/previousFarmId" to admin.farmId,
        "users/${request.workerUid}/employmentStatus" to "active",
        "users/${request.workerUid}/approved" to true,

        "farms/${admin.farmId}/members/${request.workerUid}/role" to "worker",
        "farms/${admin.farmId}/members/${request.workerUid}/joinedAt" to approvedAt,
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

private fun removeEmployeeFromFarm(
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
        "users/${employee.uid}/employmentStatus" to "removed",
        "users/${employee.uid}/approved" to false,
        "users/${employee.uid}/removedAt" to removedAt,
        "users/${employee.uid}/removedBy" to admin.uid,
        "farms/${admin.farmId}/members/${employee.uid}" to null,
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

private fun restoreEmployeeToFarm(
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
        "users/${employee.uid}/employmentStatus" to "active",
        "users/${employee.uid}/approved" to true,
        "users/${employee.uid}/restoredAt" to restoredAt,
        "users/${employee.uid}/restoredBy" to admin.uid,
        "users/${employee.uid}/removedAt" to null,
        "users/${employee.uid}/removedBy" to null,

        "farms/${admin.farmId}/members/${employee.uid}/role" to "worker",
        "farms/${admin.farmId}/members/${employee.uid}/joinedAt" to restoredAt,
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

private fun addActivityLog(
    farmId: String,
    type: String,
    title: String,
    message: String,
    actorUid: String,
    actorName: String,
    targetUid: String = "",
    targetName: String = ""
) {
    if (farmId.isBlank()) {
        return
    }

    val database = FirebaseDatabase.getInstance().reference
    val timestamp = System.currentTimeMillis()
    val logRef = database
        .child("farms")
        .child(farmId)
        .child("activityLogs")
        .push()

    val activityLog = createActivityLogMap(
        type = type,
        title = title,
        message = message,
        actorUid = actorUid,
        actorName = actorName,
        targetUid = targetUid,
        targetName = targetName,
        timestamp = timestamp
    )

    logRef.setValue(activityLog)
}

private fun createActivityLogMap(
    type: String,
    title: String,
    message: String,
    actorUid: String,
    actorName: String,
    targetUid: String = "",
    targetName: String = "",
    timestamp: Long
): Map<String, Any> {
    return mapOf(
        "type" to type,
        "title" to title,
        "message" to message,
        "actorUid" to actorUid,
        "actorName" to actorName,
        "targetUid" to targetUid,
        "targetName" to targetName,
        "timestamp" to timestamp
    )
}

private fun formatActivityTime(timestamp: Long): String {
    if (timestamp <= 0L) {
        return ""
    }

    val formatter = SimpleDateFormat(
        "MMM d, h:mm a",
        Locale.getDefault()
    )

    return formatter.format(Date(timestamp))
}

private fun activityColor(type: String): Color {
    return when (type) {
        "employee_approved" -> SafeGreen
        "employee_restored" -> SafeGreen
        "employee_removed" -> Color(0xFFC62828)
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

private fun generateNewWorkerRequestCode(
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

private fun createUniqueEmployeeRequestCode(
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

@Composable
fun SettingsScreen(
    user: CrayFarmUser,
    pond: PondSummary,
    onLogout: () -> Unit
) {
    var showLogoutConfirmation by remember {
        mutableStateOf(false)
    }

    var minTemperature by remember(pond.pondId) {
        mutableStateOf("20")
    }
    var maxTemperature by remember(pond.pondId) {
        mutableStateOf("30")
    }
    var minPh by remember(pond.pondId) {
        mutableStateOf("6.5")
    }
    var maxPh by remember(pond.pondId) {
        mutableStateOf("8.5")
    }
    var maxTurbidity by remember(pond.pondId) {
        mutableStateOf("25")
    }
    var minTds by remember(pond.pondId) {
        mutableStateOf("200")
    }
    var maxTds by remember(pond.pondId) {
        mutableStateOf("500")
    }
    var minWaterLevel by remember(pond.pondId) {
        mutableStateOf("40")
    }

    var isLoadingThresholds by remember(pond.pondId) {
        mutableStateOf(true)
    }
    var isSavingThresholds by remember {
        mutableStateOf(false)
    }
    var thresholdMessage by remember {
        mutableStateOf("")
    }
    var thresholdMessageIsSuccess by remember {
        mutableStateOf(false)
    }

    LaunchedEffect(user.farmId, pond.pondId) {
        FirebaseDatabase.getInstance()
            .getReference("farms")
            .child(user.farmId)
            .child("ponds")
            .child(pond.pondId)
            .child("thresholds")
            .get()
            .addOnSuccessListener { snapshot ->
                minTemperature = snapshot.readNumberOrDefault(
                    "minTemperature",
                    20.0
                ).formatThreshold()

                maxTemperature = snapshot.readNumberOrDefault(
                    "maxTemperature",
                    30.0
                ).formatThreshold()

                minPh = snapshot.readNumberOrDefault(
                    "minPh",
                    6.5
                ).formatThreshold()

                maxPh = snapshot.readNumberOrDefault(
                    "maxPh",
                    8.5
                ).formatThreshold()

                maxTurbidity = snapshot.readNumberOrDefault(
                    "maxTurbidity",
                    25.0
                ).formatThreshold()

                minTds = snapshot.readNumberOrDefault(
                    "minTds",
                    200.0
                ).formatThreshold()

                maxTds = snapshot.readNumberOrDefault(
                    "maxTds",
                    500.0
                ).formatThreshold()

                minWaterLevel = snapshot.readNumberOrDefault(
                    "minWaterLevel",
                    40.0
                ).formatThreshold()

                isLoadingThresholds = false
            }
            .addOnFailureListener { exception ->
                isLoadingThresholds = false
                thresholdMessageIsSuccess = false
                thresholdMessage =
                    exception.message
                        ?: "Unable to load pond thresholds."
            }
    }

    if (showLogoutConfirmation) {
        AlertDialog(
            onDismissRequest = {
                showLogoutConfirmation = false
            },
            title = {
                Text(
                    text = "Logout?",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to log out of your CrayFarm account?"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLogoutConfirmation = false
                        onLogout()
                    }
                ) {
                    Text(
                        text = "Logout",
                        color = Color(0xFFC62828),
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showLogoutConfirmation = false
                    }
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
            PageHeader(title = "Settings")

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    Spacer(modifier = Modifier.height(4.dp))
                }

                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = Color.White
                        ),
                        elevation = CardDefaults.cardElevation(
                            defaultElevation = 2.dp
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp)
                        ) {
                            Text(
                                text = user.fullName,
                                fontSize = 19.sp,
                                fontWeight = FontWeight.Bold
                            )

                            Spacer(modifier = Modifier.height(5.dp))

                            Text(
                                text = user.email,
                                fontSize = 13.sp,
                                color = Color.Gray
                            )

                            Spacer(modifier = Modifier.height(5.dp))

                            Text(
                                text = if (user.role == "admin") {
                                    "Farm Owner"
                                } else {
                                    "Worker"
                                },
                                fontSize = 13.sp,
                                color = Blue,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                item {
                    Text(
                        text = "${pond.pondName} Thresholds",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF222222)
                    )
                }

                item {
                    Text(
                        text = "These values control the Safe, Low, and High labels on this pond's dashboard.",
                        fontSize = 13.sp,
                        color = Color.Gray
                    )
                }

                if (isLoadingThresholds) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(
                                color = Blue
                            )
                        }
                    }
                } else {
                    item {
                        ThresholdSettingsCard(
                            minTemperature = minTemperature,
                            onMinTemperatureChanged = {
                                minTemperature = it
                                thresholdMessage = ""
                            },
                            maxTemperature = maxTemperature,
                            onMaxTemperatureChanged = {
                                maxTemperature = it
                                thresholdMessage = ""
                            },
                            minPh = minPh,
                            onMinPhChanged = {
                                minPh = it
                                thresholdMessage = ""
                            },
                            maxPh = maxPh,
                            onMaxPhChanged = {
                                maxPh = it
                                thresholdMessage = ""
                            },
                            maxTurbidity = maxTurbidity,
                            onMaxTurbidityChanged = {
                                maxTurbidity = it
                                thresholdMessage = ""
                            },
                            minTds = minTds,
                            onMinTdsChanged = {
                                minTds = it
                                thresholdMessage = ""
                            },
                            maxTds = maxTds,
                            onMaxTdsChanged = {
                                maxTds = it
                                thresholdMessage = ""
                            },
                            minWaterLevel = minWaterLevel,
                            onMinWaterLevelChanged = {
                                minWaterLevel = it
                                thresholdMessage = ""
                            }
                        )
                    }

                    if (thresholdMessage.isNotBlank()) {
                        item {
                            Text(
                                text = thresholdMessage,
                                modifier = Modifier.fillMaxWidth(),
                                color = if (thresholdMessageIsSuccess) {
                                    SafeGreen
                                } else {
                                    Color(0xFFC62828)
                                },
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    item {
                        Button(
                            onClick = {
                                val values = listOf(
                                    minTemperature.toDoubleOrNull(),
                                    maxTemperature.toDoubleOrNull(),
                                    minPh.toDoubleOrNull(),
                                    maxPh.toDoubleOrNull(),
                                    maxTurbidity.toDoubleOrNull(),
                                    minTds.toDoubleOrNull(),
                                    maxTds.toDoubleOrNull(),
                                    minWaterLevel.toDoubleOrNull()
                                )

                                if (values.any { it == null }) {
                                    thresholdMessageIsSuccess = false
                                    thresholdMessage =
                                        "Enter a valid number in every threshold field."
                                    return@Button
                                }

                                val thresholds = PondThresholds(
                                    minTemperature = values[0]!!,
                                    maxTemperature = values[1]!!,
                                    minPh = values[2]!!,
                                    maxPh = values[3]!!,
                                    maxTurbidity = values[4]!!,
                                    minTds = values[5]!!,
                                    maxTds = values[6]!!,
                                    minWaterLevel = values[7]!!
                                )

                                val validationError =
                                    validatePondThresholds(thresholds)

                                if (validationError != null) {
                                    thresholdMessageIsSuccess = false
                                    thresholdMessage = validationError
                                    return@Button
                                }

                                isSavingThresholds = true
                                thresholdMessage = ""

                                savePondThresholds(
                                    user = user,
                                    pond = pond,
                                    thresholds = thresholds,
                                    onSuccess = {
                                        isSavingThresholds = false
                                        thresholdMessageIsSuccess = true
                                        thresholdMessage =
                                            "Thresholds saved successfully."
                                    },
                                    onError = { error ->
                                        isSavingThresholds = false
                                        thresholdMessageIsSuccess = false
                                        thresholdMessage = error
                                    }
                                )
                            },
                            enabled = !isSavingThresholds,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Blue,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            if (isSavingThresholds) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(22.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text(
                                    text = "Save Thresholds",
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                item {
                    Button(
                        onClick = {
                            showLogoutConfirmation = true
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFC62828),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = "Logout",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
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

@Composable
fun ThresholdSettingsCard(
    minTemperature: String,
    onMinTemperatureChanged: (String) -> Unit,
    maxTemperature: String,
    onMaxTemperatureChanged: (String) -> Unit,
    minPh: String,
    onMinPhChanged: (String) -> Unit,
    maxPh: String,
    onMaxPhChanged: (String) -> Unit,
    maxTurbidity: String,
    onMaxTurbidityChanged: (String) -> Unit,
    minTds: String,
    onMinTdsChanged: (String) -> Unit,
    maxTds: String,
    onMaxTdsChanged: (String) -> Unit,
    minWaterLevel: String,
    onMinWaterLevelChanged: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ThresholdField(
                    modifier = Modifier.weight(1f),
                    label = "Min Temp (°C)",
                    value = minTemperature,
                    onValueChanged = onMinTemperatureChanged
                )

                ThresholdField(
                    modifier = Modifier.weight(1f),
                    label = "Max Temp (°C)",
                    value = maxTemperature,
                    onValueChanged = onMaxTemperatureChanged
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ThresholdField(
                    modifier = Modifier.weight(1f),
                    label = "Min pH",
                    value = minPh,
                    onValueChanged = onMinPhChanged
                )

                ThresholdField(
                    modifier = Modifier.weight(1f),
                    label = "Max pH",
                    value = maxPh,
                    onValueChanged = onMaxPhChanged
                )
            }

            ThresholdField(
                modifier = Modifier.fillMaxWidth(),
                label = "Maximum Relative Turbidity (%)",
                value = maxTurbidity,
                onValueChanged = onMaxTurbidityChanged
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ThresholdField(
                    modifier = Modifier.weight(1f),
                    label = "Min TDS",
                    value = minTds,
                    onValueChanged = onMinTdsChanged
                )

                ThresholdField(
                    modifier = Modifier.weight(1f),
                    label = "Max TDS",
                    value = maxTds,
                    onValueChanged = onMaxTdsChanged
                )
            }

            ThresholdField(
                modifier = Modifier.fillMaxWidth(),
                label = "Minimum Water Level (%)",
                value = minWaterLevel,
                onValueChanged = onMinWaterLevelChanged
            )
        }
    }
}

@Composable
fun ThresholdField(
    modifier: Modifier,
    label: String,
    value: String,
    onValueChanged: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = { enteredValue ->
            val filtered = enteredValue
                .filter { character ->
                    character.isDigit() ||
                            character == '.' ||
                            character == '-'
                }
                .take(8)

            onValueChanged(filtered)
        },
        modifier = modifier,
        label = {
            Text(
                text = label,
                fontSize = 11.sp
            )
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Decimal
        )
    )
}

@Composable
fun ProfileScreen(
    user: CrayFarmUser
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = LightBlue
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            PageHeader(title = "Profile")

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.White
                ),
                elevation = CardDefaults.cardElevation(
                    defaultElevation = 2.dp
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp)
                ) {
                    Text(
                        text = user.fullName,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = user.email,
                        fontSize = 14.sp,
                        color = Color.Gray
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Role: Worker",
                        color = Blue,
                        fontWeight = FontWeight.Medium
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Farm: ${user.farmId}",
                        color = Color.Gray
                    )
                }
            }
        }
    }
}

@Composable
fun TemporaryPage(title: String) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = LightBlue
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            PageHeader(title = title)

            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "$title page will be developed next.",
                    color = Color.Gray,
                    fontSize = 15.sp
                )
            }
        }
    }
}

private const val ALERT_COOLDOWN_MILLIS =
    15L * 60L * 1000L

private fun evaluateAndCreatePondAlerts(
    user: CrayFarmUser,
    pond: PondSummary,
    readings: PondReadings,
    thresholds: PondThresholds
) {
    val candidates = mutableListOf<AlertCandidate>()

    if (readings.temperature < thresholds.minTemperature) {
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

    if (readings.temperature > thresholds.maxTemperature) {
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

    if (readings.ph < thresholds.minPh) {
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

    if (readings.ph > thresholds.maxPh) {
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

    if (readings.turbidity > thresholds.maxTurbidity) {
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
                unit = "NTU"
            )
        )
    }

    if (readings.tds < thresholds.minTds) {
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

    if (readings.tds > thresholds.maxTds) {
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

    if (readings.waterLevel < thresholds.minWaterLevel) {
        candidates.add(
            AlertCandidate(
                type = "water_level_low",
                sensor = "waterLevel",
                title = "Low Water Level",
                message =
                    "Water level reached ${formatAlertValue(readings.waterLevel)}%. The minimum safe value is ${formatAlertValue(thresholds.minWaterLevel)}%.",
                suggestedAction =
                    "Check for leaks, evaporation, blocked supply lines, and refill the pond safely.",
                value = readings.waterLevel,
                threshold = thresholds.minWaterLevel,
                unit = "%"
            )
        )
    }

    candidates.forEach { candidate ->
        createPondAlertWithCooldown(
            user = user,
            pond = pond,
            candidate = candidate
        )
    }
}

private fun createPondAlertWithCooldown(
    user: CrayFarmUser,
    pond: PondSummary,
    candidate: AlertCandidate
) {
    val database = FirebaseDatabase.getInstance().reference
    val now = System.currentTimeMillis()

    val cooldownRef = database
        .child("farms")
        .child(user.farmId)
        .child("ponds")
        .child(pond.pondId)
        .child("alertCooldowns")
        .child(candidate.type)

    cooldownRef.runTransaction(
        object : Transaction.Handler {
            override fun doTransaction(
                currentData: MutableData
            ): Transaction.Result {
                val previousAlertAt =
                    (currentData.child("lastAlertAt").value as? Number)
                        ?.toLong()
                        ?: 0L

                if (
                    previousAlertAt > 0L &&
                    now - previousAlertAt < ALERT_COOLDOWN_MILLIS
                ) {
                    return Transaction.abort()
                }

                currentData.value = mapOf(
                    "lastAlertAt" to now,
                    "lastValue" to candidate.value
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

                val alertRef = database
                    .child("farms")
                    .child(user.farmId)
                    .child("ponds")
                    .child(pond.pondId)
                    .child("alerts")
                    .push()

                val alertData = mapOf(
                    "type" to candidate.type,
                    "sensor" to candidate.sensor,
                    "title" to candidate.title,
                    "message" to candidate.message,
                    "suggestedAction" to candidate.suggestedAction,
                    "value" to candidate.value,
                    "threshold" to candidate.threshold,
                    "unit" to candidate.unit,
                    "pondId" to pond.pondId,
                    "pondName" to pond.pondName,
                    "createdAt" to now,
                    "acknowledged" to false,
                    "acknowledgedAt" to 0L,
                    "acknowledgedBy" to ""
                )

                alertRef.setValue(alertData)
            }
        }
    )
}

private fun acknowledgePondAlert(
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

private fun formatAlertValue(value: Double): String {
    return if (value % 1.0 == 0.0) {
        value.toInt().toString()
    } else {
        String.format(Locale.getDefault(), "%.1f", value)
    }
}

private fun DataSnapshot.readNumber(key: String): Double {
    return (child(key).value as? Number)?.toDouble() ?: 0.0
}

private fun DataSnapshot.readNumberOrDefault(
    key: String,
    defaultValue: Double
): Double {
    return (child(key).value as? Number)?.toDouble()
        ?: defaultValue
}

private fun Double.formatThreshold(): String {
    return if (this % 1.0 == 0.0) {
        this.toInt().toString()
    } else {
        this.toString()
    }
}

private fun rangeStatus(
    value: Double,
    minimum: Double,
    maximum: Double,
    lowLabel: String,
    highLabel: String
): Pair<String, Color> {
    return when {
        value < minimum -> lowLabel to Color(0xFF1976D2)
        value > maximum -> highLabel to Color(0xFFC62828)
        else -> "Safe" to SafeGreen
    }
}

private fun maximumStatus(
    value: Double,
    maximum: Double,
    highLabel: String
): Pair<String, Color> {
    return if (value > maximum) {
        highLabel to Color(0xFFC62828)
    } else {
        "Safe" to SafeGreen
    }
}

private fun minimumStatus(
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

private fun validatePondThresholds(
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

        thresholds.minWaterLevel !in 0.0..100.0 -> {
            "Minimum water level must be between 0 and 100."
        }

        thresholds.minPh < 0 || thresholds.maxPh > 14 -> {
            "pH thresholds must be between 0 and 14."
        }

        else -> null
    }
}

private fun savePondThresholds(
    user: CrayFarmUser,
    pond: PondSummary,
    thresholds: PondThresholds,
    onSuccess: () -> Unit,
    onError: (String) -> Unit
) {
    val thresholdData = mapOf(
        "minTemperature" to thresholds.minTemperature,
        "maxTemperature" to thresholds.maxTemperature,
        "minPh" to thresholds.minPh,
        "maxPh" to thresholds.maxPh,
        "maxTurbidity" to thresholds.maxTurbidity,
        "minTds" to thresholds.minTds,
        "maxTds" to thresholds.maxTds,
        "minWaterLevel" to thresholds.minWaterLevel,
        "updatedAt" to System.currentTimeMillis(),
        "updatedBy" to user.uid
    )

    FirebaseDatabase.getInstance()
        .getReference("farms")
        .child(user.farmId)
        .child("ponds")
        .child(pond.pondId)
        .child("thresholds")
        .setValue(thresholdData)
        .addOnSuccessListener {
            addActivityLog(
                farmId = user.farmId,
                type = "thresholds_updated",
                title = "Thresholds updated",
                message =
                    "${pond.pondName} thresholds were updated by ${user.fullName}.",
                actorUid = user.uid,
                actorName = user.fullName
            )

            onSuccess()
        }
        .addOnFailureListener { exception ->
            onError(
                exception.message
                    ?: "Unable to save pond thresholds."
            )
        }
}


private fun temperatureStatus(temp: Double): Pair<String, Color> {
    return when {
        temp < 20 -> "Low" to Color(0xFF1976D2)
        temp <= 30 -> "Safe" to SafeGreen
        else -> "High" to Color.Red
    }
}

private fun phStatus(ph: Double): Pair<String, Color> {
    return when {
        ph < 6.5 -> "Acidic" to Color.Red
        ph <= 8.5 -> "Safe" to SafeGreen
        else -> "Alkaline" to WarningOrange
    }
}

private fun generateEmployeeRequestCode(): String {
    return (100000..999999).random().toString()
}

private fun loadUserProfile(
    uid: String,
    onSuccess: (CrayFarmUser) -> Unit,
    onWaiting: (CrayFarmUser) -> Unit,
    onError: (String) -> Unit
) {
    FirebaseDatabase.getInstance()
        .getReference("users")
        .child(uid)
        .get()
        .addOnSuccessListener { snapshot ->

            if (!snapshot.exists()) {
                onError("User profile was not found.")
                return@addOnSuccessListener
            }

            val user = CrayFarmUser(
                uid = uid,
                fullName = snapshot.child("fullName")
                    .getValue(String::class.java)
                    .orEmpty(),
                email = snapshot.child("email")
                    .getValue(String::class.java)
                    .orEmpty(),
                role = snapshot.child("role")
                    .getValue(String::class.java)
                    .orEmpty(),
                farmId = snapshot.child("farmId")
                    .getValue(String::class.java)
                    .orEmpty(),
                requestCode = snapshot.child("requestCode")
                    .getValue(String::class.java)
                    .orEmpty(),
                previousFarmId = snapshot.child("previousFarmId")
                    .getValue(String::class.java)
                    .orEmpty(),
                employmentStatus = snapshot.child("employmentStatus")
                    .getValue(String::class.java)
                    .orEmpty(),
                approved = snapshot.child("approved")
                    .getValue(Boolean::class.java)
                    ?: false,
                active = snapshot.child("active")
                    .getValue(Boolean::class.java)
                    ?: false
            )

            when {
                !user.active -> {
                    onError("This account is currently inactive.")
                }

                user.role != "admin" && user.role != "worker" -> {
                    onError("This account has an invalid role.")
                }

                user.role == "worker" &&
                        (!user.approved || user.farmId.isBlank()) -> {
                    onWaiting(user)
                }

                user.role == "admin" &&
                        (!user.approved || user.farmId.isBlank()) -> {
                    onError("The admin account is not configured correctly.")
                }

                else -> {
                    onSuccess(user)
                }
            }
        }
        .addOnFailureListener { exception ->
            onError(
                exception.message ?: "Unable to load the user profile."
            )
        }
}