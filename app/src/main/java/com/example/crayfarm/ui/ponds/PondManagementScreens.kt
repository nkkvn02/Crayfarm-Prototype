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

private val Blue = Color(0xFF2639A6)
private val LightBlue = Color(0xFFC5CDD9)
private val SafeGreen = Color(0xFF16803B)

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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF101F65),
                        Blue,
                        Color(0xFF1E2D8E)
                    )
                )
            )
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                color = Color.White.copy(alpha = 0.045f),
                radius = size.minDimension * 0.48f,
                center = Offset(size.width, size.height * 0.04f)
            )
        }

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
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Logout",
                    modifier = Modifier
                        .clickable {
                            showLogoutConfirmation = true
                        }
                        .padding(6.dp)
                        .size(24.dp),
                    tint = Color.White
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
                        text = "Tap the menu to rename. Press and hold a pond to delete it.",
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
                        CircularProgressIndicator(color = Color.White)
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
            .border(
                width = 1.dp,
                color = Blue.copy(alpha = 0.12f),
                shape = RoundedCornerShape(18.dp)
            )
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
            defaultElevation = 2.dp
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
                        Icon(
                            imageVector = Icons.Rounded.MoreVert,
                            contentDescription = "Edit ${pond.pondName}",
                            tint = Blue,
                            modifier = Modifier.size(19.dp)
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
                Surface(
                    modifier = Modifier.size(42.dp),
                    color = Blue.copy(alpha = 0.10f),
                    shape = CircleShape
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.Water,
                            contentDescription = null,
                            tint = Blue,
                            modifier = Modifier.size(23.dp)
                        )
                    }
                }

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
            .border(
                width = 1.dp,
                color = Blue.copy(alpha = 0.16f),
                shape = RoundedCornerShape(18.dp)
            )
            .clickable {
                onClick()
            },
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.96f)
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 1.dp
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
            Surface(
                modifier = Modifier.size(44.dp),
                color = Blue.copy(alpha = 0.10f),
                shape = CircleShape
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = null,
                        tint = Blue,
                        modifier = Modifier.size(25.dp)
                    )
                }
            }

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


