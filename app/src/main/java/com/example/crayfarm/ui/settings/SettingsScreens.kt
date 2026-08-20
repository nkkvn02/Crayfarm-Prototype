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
private val WarningOrange = Color(0xFFE76F00)

@Composable
fun SettingsScreen(
    user: CrayFarmUser,
    pond: PondSummary,
    onLogout: () -> Unit
) {
    var showAccount by remember(user.uid) {
        mutableStateOf(false)
    }

    var showFarm by remember(user.uid, user.farmId) {
        mutableStateOf(false)
    }

    if (showAccount) {
        AccountSettingsScreen(
            user = user,
            onBack = {
                showAccount = false
            },
            onLogout = onLogout
        )
        return
    }

    if (showFarm) {
        FarmSettingsScreen(
            user = user,
            onBack = {
                showFarm = false
            }
        )
        return
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
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Spacer(modifier = Modifier.height(4.dp))
                }

                item {
                    SettingsOptionCard(
                        title = "Account",
                        subtitle = if (user.role == "admin") {
                            "Name, email, password, and logout"
                        } else {
                            "Profile, email, password, logout, and leave farm"
                        },
                        onClick = {
                            showAccount = true
                        }
                    )
                }

                item {
                    SettingsOptionCard(
                        title = "Farm",
                        subtitle = if (user.role == "admin") {
                            "Photo, farm name, and description"
                        } else {
                            "View farm information"
                        },
                        onClick = {
                            showFarm = true
                        }
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }
    }
}

@Composable
fun SettingsOptionCard(
    title: String,
    subtitle: String = "",
    danger: Boolean = false,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = if (danger) {
                    Color(0xFFC62828).copy(alpha = 0.16f)
                } else {
                    Blue.copy(alpha = 0.10f)
                },
                shape = RoundedCornerShape(14.dp)
            )
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 1.dp
        ),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (danger) {
                        Color(0xFFC62828)
                    } else {
                        Color(0xFF222222)
                    }
                )

                if (subtitle.isNotBlank()) {
                    Spacer(modifier = Modifier.height(3.dp))

                    Text(
                        text = subtitle,
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
            }

            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = Color(0xFF78809A),
                modifier = Modifier.size(21.dp)
            )
        }
    }
}

@Composable
fun FarmSettingsScreen(
    user: CrayFarmUser,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    var farmName by remember(user.farmId) {
        mutableStateOf("")
    }

    var description by remember(user.farmId) {
        mutableStateOf("")
    }

    var photoBase64 by remember(user.farmId) {
        mutableStateOf("")
    }

    var isLoading by remember(user.farmId) {
        mutableStateOf(true)
    }

    var isSaving by remember {
        mutableStateOf(false)
    }

    var message by remember {
        mutableStateOf("")
    }

    var messageIsSuccess by remember {
        mutableStateOf(false)
    }

    var showNameDialog by remember {
        mutableStateOf(false)
    }

    var showDescriptionDialog by remember {
        mutableStateOf(false)
    }

    var showPhotoConfirmation by remember {
        mutableStateOf(false)
    }

    var editedFarmName by remember {
        mutableStateOf("")
    }

    var editedDescription by remember {
        mutableStateOf("")
    }

    var pendingPhotoBase64 by remember {
        mutableStateOf("")
    }

    val farmRef = remember(user.farmId) {
        FirebaseDatabase.getInstance()
            .getReference("farms")
            .child(user.farmId)
    }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null && user.role == "admin") {
            try {
                val encoded = compressFarmPhotoToBase64(
                    context = context,
                    uri = uri
                )

                if (encoded.isBlank()) {
                    messageIsSuccess = false
                    message = "Unable to process the selected photo."
                } else {
                    pendingPhotoBase64 = encoded
                    showPhotoConfirmation = true
                }
            } catch (exception: Exception) {
                messageIsSuccess = false
                message = exception.message
                    ?: "Unable to process the selected photo."
            }
        }
    }

    DisposableEffect(farmRef) {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                farmName = snapshot.child("farmName")
                    .getValue(String::class.java)
                    .orEmpty()
                    .ifBlank {
                        snapshot.child("name")
                            .getValue(String::class.java)
                            .orEmpty()
                    }

                description = snapshot.child("description")
                    .getValue(String::class.java)
                    .orEmpty()

                photoBase64 = snapshot.child("photoBase64")
                    .getValue(String::class.java)
                    .orEmpty()

                isLoading = false
            }

            override fun onCancelled(error: DatabaseError) {
                isLoading = false
                messageIsSuccess = false
                message = error.message
            }
        }

        farmRef.addValueEventListener(listener)

        onDispose {
            farmRef.removeEventListener(listener)
        }
    }

    if (showNameDialog && user.role == "admin") {
        AlertDialog(
            onDismissRequest = {
                if (!isSaving) {
                    showNameDialog = false
                }
            },
            title = {
                Text(
                    text = "Change Farm Name",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                OutlinedTextField(
                    value = editedFarmName,
                    onValueChange = {
                        editedFarmName = it.take(80)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = {
                        Text("Farm name")
                    },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val cleanName = editedFarmName.trim()

                        if (cleanName.length < 2) {
                            messageIsSuccess = false
                            message = "Enter a valid farm name."
                            return@TextButton
                        }

                        isSaving = true

                        farmRef.updateChildren(
                            mapOf<String, Any>(
                                "farmName" to cleanName,
                                "name" to cleanName,
                                "profileUpdatedAt" to
                                        System.currentTimeMillis()
                            )
                        )
                            .addOnSuccessListener {
                                isSaving = false
                                showNameDialog = false
                                messageIsSuccess = true
                                message = "Farm name updated."
                            }
                            .addOnFailureListener { exception ->
                                isSaving = false
                                messageIsSuccess = false
                                message = exception.message
                                    ?: "Unable to update farm name."
                            }
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
                            text = "Confirm",
                            color = Blue,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showNameDialog = false
                    },
                    enabled = !isSaving
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showDescriptionDialog && user.role == "admin") {
        AlertDialog(
            onDismissRequest = {
                if (!isSaving) {
                    showDescriptionDialog = false
                }
            },
            title = {
                Text(
                    text = "Edit Farm Description",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                OutlinedTextField(
                    value = editedDescription,
                    onValueChange = {
                        editedDescription = it.take(500)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = {
                        Text("Description")
                    },
                    supportingText = {
                        Text("${editedDescription.length}/500")
                    },
                    minLines = 4,
                    maxLines = 7
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        isSaving = true

                        farmRef.child("description")
                            .setValue(editedDescription.trim())
                            .addOnSuccessListener {
                                isSaving = false
                                showDescriptionDialog = false
                                messageIsSuccess = true
                                message = "Farm description updated."
                            }
                            .addOnFailureListener { exception ->
                                isSaving = false
                                messageIsSuccess = false
                                message = exception.message
                                    ?: "Unable to update farm description."
                            }
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
                            text = "Confirm",
                            color = Blue,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDescriptionDialog = false
                    },
                    enabled = !isSaving
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showPhotoConfirmation && user.role == "admin") {
        AlertDialog(
            onDismissRequest = {
                if (!isSaving) {
                    showPhotoConfirmation = false
                    pendingPhotoBase64 = ""
                }
            },
            title = {
                Text(
                    text = "Change Farm Photo?",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Use the selected image as the new farm photo?"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        isSaving = true

                        farmRef.child("photoBase64")
                            .setValue(pendingPhotoBase64)
                            .addOnSuccessListener {
                                isSaving = false
                                showPhotoConfirmation = false
                                pendingPhotoBase64 = ""
                                messageIsSuccess = true
                                message = "Farm photo updated."
                            }
                            .addOnFailureListener { exception ->
                                isSaving = false
                                messageIsSuccess = false
                                message = exception.message
                                    ?: "Unable to update farm photo."
                            }
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
                            text = "Confirm",
                            color = Blue,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showPhotoConfirmation = false
                        pendingPhotoBase64 = ""
                    },
                    enabled = !isSaving
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Blue)
                    .statusBarsPadding()
                    .padding(horizontal = 10.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onBack
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White,
                        modifier = Modifier.size(23.dp)
                    )
                }

                Text(
                    text = "Farm",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }

                return@Column
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Spacer(modifier = Modifier.height(26.dp))
                }

                item {
                    Box(
                        modifier = Modifier
                            .width(132.dp)
                            .height(105.dp)
                    ) {
                        FarmPhotoPreview(
                            photoBase64 = photoBase64,
                            farmName = farmName.ifBlank {
                                "Farm"
                            },
                            modifier = Modifier.fillMaxSize()
                        )

                        if (user.role == "admin") {
                            Card(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(4.dp)
                                    .clickable {
                                        photoPicker.launch("image/*")
                                    },
                                colors = CardDefaults.cardColors(
                                    containerColor = Color.White
                                ),
                                elevation = CardDefaults.cardElevation(
                                    defaultElevation = 3.dp
                                ),
                                shape = RoundedCornerShape(18.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Edit,
                                    contentDescription = "Change farm photo",
                                    tint = Blue,
                                    modifier = Modifier
                                        .padding(7.dp)
                                        .size(17.dp)
                                )
                            }
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(6.dp))
                }

                item {
                    FarmSettingRow(
                        label = "Name",
                        value = farmName.ifBlank {
                            "Not provided"
                        },
                        editable = user.role == "admin",
                        onEdit = {
                            editedFarmName = farmName
                            message = ""
                            showNameDialog = true
                        }
                    )
                }

                item {
                    FarmSettingRow(
                        label = "Description",
                        value = description.ifBlank {
                            "No description added"
                        },
                        editable = user.role == "admin",
                        onEdit = {
                            editedDescription = description
                            message = ""
                            showDescriptionDialog = true
                        }
                    )
                }

                if (user.role != "admin") {
                    item {
                        Text(
                            text = "Farm information is managed by the farm owner.",
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                            textAlign = TextAlign.Center,
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }
                }

                if (message.isNotBlank()) {
                    item {
                        Text(
                            text = message,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp),
                            color = if (messageIsSuccess) {
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
                    Spacer(modifier = Modifier.height(18.dp))
                }
            }
        }
    }
}

@Composable
fun FarmSettingRow(
    label: String,
    value: String,
    editable: Boolean,
    onEdit: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = Blue.copy(alpha = 0.10f),
                shape = RoundedCornerShape(14.dp)
            ),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 1.dp
        ),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = label,
                    fontSize = 13.sp,
                    color = Color(0xFF222222),
                    fontWeight = FontWeight.Medium
                )

                Spacer(modifier = Modifier.height(3.dp))

                Text(
                    text = value,
                    fontSize = 12.sp,
                    color = Color.Gray,
                    maxLines = if (label == "Description") 3 else 1
                )
            }

            if (editable) {
                TextButton(
                    onClick = onEdit,
                    contentPadding = PaddingValues(
                        horizontal = 8.dp,
                        vertical = 4.dp
                    )
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Edit,
                        contentDescription = "Edit $label",
                        tint = Blue,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun FarmPhotoPreview(
    photoBase64: String,
    farmName: String,
    modifier: Modifier = Modifier
) {
    val bitmap = remember(photoBase64) {
        decodeFarmPhoto(photoBase64)
    }

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFE8EAF6)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "$farmName farm photo",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Agriculture,
                        contentDescription = null,
                        tint = Blue,
                        modifier = Modifier.size(43.dp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "No farm photo",
                        color = Color.Gray,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

@Composable
fun FarmInformationCard(
    label: String,
    value: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp
        ),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = label,
                fontSize = 12.sp,
                color = Color.Gray,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = value,
                fontSize = 15.sp,
                color = Color(0xFF222222)
            )
        }
    }
}

private fun updateFarmProfile(
    farmId: String,
    farmName: String,
    description: String,
    photoBase64: String,
    onSuccess: () -> Unit,
    onError: (String) -> Unit
) {
    if (farmId.isBlank()) {
        onError("This account is not connected to a farm.")
        return
    }

    val updates = mapOf<String, Any>(
        "farmName" to farmName,
        "name" to farmName,
        "description" to description,
        "photoBase64" to photoBase64,
        "profileUpdatedAt" to System.currentTimeMillis()
    )

    FirebaseDatabase.getInstance()
        .getReference("farms")
        .child(farmId)
        .updateChildren(updates)
        .addOnSuccessListener {
            onSuccess()
        }
        .addOnFailureListener { exception ->
            onError(
                exception.message
                    ?: "Unable to update farm information."
            )
        }
}

private fun compressFarmPhotoToBase64(
    context: android.content.Context,
    uri: android.net.Uri
): String {
    val original = context.contentResolver
        .openInputStream(uri)
        ?.use { input ->
            BitmapFactory.decodeStream(input)
        }
        ?: return ""

    val maxDimension = 1000
    val width = original.width
    val height = original.height

    val scale = if (
        width > maxDimension ||
        height > maxDimension
    ) {
        minOf(
            maxDimension.toFloat() / width.toFloat(),
            maxDimension.toFloat() / height.toFloat()
        )
    } else {
        1f
    }

    val resized = if (scale < 1f) {
        Bitmap.createScaledBitmap(
            original,
            (width * scale).toInt().coerceAtLeast(1),
            (height * scale).toInt().coerceAtLeast(1),
            true
        )
    } else {
        original
    }

    val output = ByteArrayOutputStream()

    resized.compress(
        Bitmap.CompressFormat.JPEG,
        65,
        output
    )

    val bytes = output.toByteArray()

    if (resized !== original) {
        resized.recycle()
    }

    original.recycle()

    // Keep the RTDB payload small enough for a capstone profile image.
    if (bytes.size > 350_000) {
        throw IllegalArgumentException(
            "The selected photo is still too large. Please choose a smaller image."
        )
    }

    return Base64.encodeToString(
        bytes,
        Base64.NO_WRAP
    )
}

private fun decodeFarmPhoto(
    encoded: String
): Bitmap? {
    if (encoded.isBlank()) {
        return null
    }

    return try {
        val bytes = Base64.decode(
            encoded,
            Base64.NO_WRAP
        )

        BitmapFactory.decodeByteArray(
            bytes,
            0,
            bytes.size
        )
    } catch (_: Exception) {
        null
    }
}

@Composable
fun AccountSettingsScreen(
    user: CrayFarmUser,
    onBack: () -> Unit,
    onLogout: () -> Unit
) {
    var displayedEmail by remember(user.uid) {
        mutableStateOf(user.email)
    }

    var displayedName by remember(user.uid) {
        mutableStateOf(user.fullName)
    }

    var showNameDialog by remember {
        mutableStateOf(false)
    }

    var showNameConfirmation by remember {
        mutableStateOf(false)
    }

    var editedName by remember {
        mutableStateOf("")
    }

    var showPersonalInfoDialog by remember {
        mutableStateOf(false)
    }

    var showPersonalInfoConfirmation by remember {
        mutableStateOf(false)
    }

    var personalName by remember(user.uid) {
        mutableStateOf(user.fullName)
    }

    var personalAge by remember(user.uid) {
        mutableStateOf(
            if (user.age > 0) user.age.toString() else ""
        )
    }

    var personalAddress by remember(user.uid) {
        mutableStateOf(user.address)
    }

    var personalContact by remember(user.uid) {
        mutableStateOf(user.contactNumber)
    }

    var personalBirthday by remember(user.uid) {
        mutableStateOf(user.birthday)
    }

    var pendingPersonalName by remember {
        mutableStateOf("")
    }

    var pendingPersonalAge by remember {
        mutableStateOf(0)
    }

    var pendingPersonalAddress by remember {
        mutableStateOf("")
    }

    var pendingPersonalContact by remember {
        mutableStateOf("")
    }

    var pendingPersonalBirthday by remember {
        mutableStateOf("")
    }

    var showLogoutConfirmation by remember {
        mutableStateOf(false)
    }

    var showEmailDialog by remember {
        mutableStateOf(false)
    }

    var showEmailConfirmation by remember {
        mutableStateOf(false)
    }

    var newEmail by remember {
        mutableStateOf("")
    }

    var emailCurrentPassword by remember {
        mutableStateOf("")
    }

    var showPasswordDialog by remember {
        mutableStateOf(false)
    }

    var showPasswordConfirmation by remember {
        mutableStateOf(false)
    }

    var currentPasswordForPasswordChange by remember {
        mutableStateOf("")
    }

    var newPassword by remember {
        mutableStateOf("")
    }

    var confirmNewPassword by remember {
        mutableStateOf("")
    }

    var showResignDialog by remember {
        mutableStateOf(false)
    }

    var resignationMessage by remember {
        mutableStateOf("")
    }

    var isProcessing by remember {
        mutableStateOf(false)
    }

    var accountMessage by remember {
        mutableStateOf("")
    }

    var accountMessageSuccess by remember {
        mutableStateOf(false)
    }

    if (showNameDialog && user.role == "admin") {
        AlertDialog(
            onDismissRequest = {
                if (!isProcessing) {
                    showNameDialog = false
                }
            },
            title = {
                Text(
                    text = "Change Name",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                OutlinedTextField(
                    value = editedName,
                    onValueChange = {
                        editedName = it.take(60)
                        accountMessage = ""
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = {
                        Text("Full name")
                    },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val cleanName = editedName.trim()

                        when {
                            cleanName.length < 2 -> {
                                accountMessageSuccess = false
                                accountMessage =
                                    "Enter a valid full name."
                            }

                            cleanName == displayedName -> {
                                accountMessageSuccess = false
                                accountMessage =
                                    "Enter a different name."
                            }

                            else -> {
                                showNameDialog = false
                                showNameConfirmation = true
                            }
                        }
                    },
                    enabled = !isProcessing
                ) {
                    Text(
                        text = "Continue",
                        color = Blue,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showNameDialog = false
                    },
                    enabled = !isProcessing
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showNameConfirmation && user.role == "admin") {
        AlertDialog(
            onDismissRequest = {
                if (!isProcessing) {
                    showNameConfirmation = false
                }
            },
            title = {
                Text(
                    text = "Confirm Name Change",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text =
                        "Change your account name from $displayedName to ${editedName.trim()}?"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        isProcessing = true
                        accountMessage = ""

                        updateUserDisplayName(
                            uid = user.uid,
                            newName = editedName.trim(),
                            onSuccess = {
                                displayedName = editedName.trim()
                                editedName = ""
                                isProcessing = false
                                showNameConfirmation = false
                                accountMessageSuccess = true
                                accountMessage =
                                    "Name updated successfully."
                            },
                            onError = { error ->
                                isProcessing = false
                                showNameConfirmation = false
                                accountMessageSuccess = false
                                accountMessage = error
                            }
                        )
                    },
                    enabled = !isProcessing
                ) {
                    if (isProcessing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            text = "Change Name",
                            color = Blue,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showNameConfirmation = false
                    },
                    enabled = !isProcessing
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showPersonalInfoDialog && user.role == "worker") {
        AlertDialog(
            onDismissRequest = {
                if (!isProcessing) {
                    showPersonalInfoDialog = false
                }
            },
            title = {
                Text(
                    text = "Personal Information",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        Text(
                            text = "This information can be viewed by the owner of your current farm.",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }

                    item {
                        OutlinedTextField(
                            value = personalName,
                            onValueChange = {
                                personalName = it.take(60)
                                accountMessage = ""
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = {
                                Text("Full name")
                            },
                            singleLine = true
                        )
                    }

                    item {
                        OutlinedTextField(
                            value = personalAge,
                            onValueChange = { value ->
                                personalAge = value
                                    .filter { it.isDigit() }
                                    .take(3)
                                accountMessage = ""
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = {
                                Text("Age")
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number
                            )
                        )
                    }

                    item {
                        OutlinedTextField(
                            value = personalBirthday,
                            onValueChange = {
                                personalBirthday = it.take(10)
                                accountMessage = ""
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = {
                                Text("Birthday")
                            },
                            supportingText = {
                                Text("Format: YYYY-MM-DD")
                            },
                            singleLine = true
                        )
                    }

                    item {
                        OutlinedTextField(
                            value = personalContact,
                            onValueChange = { value ->
                                personalContact = value
                                    .filter { character ->
                                        character.isDigit() ||
                                                character == '+' ||
                                                character == '-' ||
                                                character == ' '
                                    }
                                    .take(20)
                                accountMessage = ""
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = {
                                Text("Contact No.")
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Phone
                            )
                        )
                    }

                    item {
                        OutlinedTextField(
                            value = personalAddress,
                            onValueChange = {
                                personalAddress = it.take(180)
                                accountMessage = ""
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = {
                                Text("Address")
                            },
                            minLines = 2,
                            maxLines = 4
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val cleanName = personalName.trim()
                        val ageValue = personalAge.toIntOrNull()
                        val cleanBirthday = personalBirthday.trim()
                        val cleanContact = personalContact.trim()
                        val cleanAddress = personalAddress.trim()

                        when {
                            cleanName.length < 2 -> {
                                accountMessageSuccess = false
                                accountMessage =
                                    "Enter a valid full name."
                            }

                            ageValue == null || ageValue !in 1..120 -> {
                                accountMessageSuccess = false
                                accountMessage =
                                    "Enter a valid age."
                            }

                            !isValidBirthday(cleanBirthday) -> {
                                accountMessageSuccess = false
                                accountMessage =
                                    "Enter birthday as YYYY-MM-DD."
                            }

                            cleanContact.length < 7 -> {
                                accountMessageSuccess = false
                                accountMessage =
                                    "Enter a valid contact number."
                            }

                            cleanAddress.length < 3 -> {
                                accountMessageSuccess = false
                                accountMessage =
                                    "Enter a valid address."
                            }

                            else -> {
                                pendingPersonalName = cleanName
                                pendingPersonalAge = ageValue
                                pendingPersonalBirthday = cleanBirthday
                                pendingPersonalContact = cleanContact
                                pendingPersonalAddress = cleanAddress

                                showPersonalInfoDialog = false
                                showPersonalInfoConfirmation = true
                            }
                        }
                    },
                    enabled = !isProcessing
                ) {
                    Text(
                        text = "Continue",
                        color = Blue,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showPersonalInfoDialog = false
                    },
                    enabled = !isProcessing
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    if (
        showPersonalInfoConfirmation &&
        user.role == "worker"
    ) {
        AlertDialog(
            onDismissRequest = {
                if (!isProcessing) {
                    showPersonalInfoConfirmation = false
                }
            },
            title = {
                Text(
                    text = "Confirm Profile Update",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text =
                        "Save these personal details? Your current farm owner will be able to view them."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        isProcessing = true
                        accountMessage = ""

                        updateWorkerPersonalInformation(
                            uid = user.uid,
                            fullName = pendingPersonalName,
                            age = pendingPersonalAge,
                            birthday = pendingPersonalBirthday,
                            contactNumber = pendingPersonalContact,
                            address = pendingPersonalAddress,
                            onSuccess = {
                                displayedName = pendingPersonalName
                                personalName = pendingPersonalName
                                personalAge =
                                    pendingPersonalAge.toString()
                                personalBirthday =
                                    pendingPersonalBirthday
                                personalContact =
                                    pendingPersonalContact
                                personalAddress =
                                    pendingPersonalAddress

                                isProcessing = false
                                showPersonalInfoConfirmation = false
                                accountMessageSuccess = true
                                accountMessage =
                                    "Personal information updated successfully."
                            },
                            onError = { error ->
                                isProcessing = false
                                showPersonalInfoConfirmation = false
                                accountMessageSuccess = false
                                accountMessage = error
                            }
                        )
                    },
                    enabled = !isProcessing
                ) {
                    if (isProcessing) {
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
                    onClick = {
                        showPersonalInfoConfirmation = false
                    },
                    enabled = !isProcessing
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showLogoutConfirmation) {
        AlertDialog(
            onDismissRequest = {
                if (!isProcessing) {
                    showLogoutConfirmation = false
                }
            },
            title = {
                Text(
                    text = "Confirm Logout",
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

    if (showEmailDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!isProcessing) {
                    showEmailDialog = false
                }
            },
            title = {
                Text(
                    text = "Change Email",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Current email: $displayedEmail",
                        fontSize = 13.sp,
                        color = Color.Gray
                    )

                    OutlinedTextField(
                        value = newEmail,
                        onValueChange = {
                            newEmail = it.trim()
                            accountMessage = ""
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = {
                            Text("New email")
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email
                        )
                    )

                    OutlinedTextField(
                        value = emailCurrentPassword,
                        onValueChange = {
                            emailCurrentPassword = it
                            accountMessage = ""
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = {
                            Text("Current password")
                        },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password
                        )
                    )

                    Text(
                        text = "Your current password is required to verify that this account belongs to you.",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val cleanEmail = newEmail.trim()

                        when {
                            !Patterns.EMAIL_ADDRESS.matcher(cleanEmail).matches() -> {
                                accountMessageSuccess = false
                                accountMessage = "Enter a valid email address."
                            }

                            emailCurrentPassword.isBlank() -> {
                                accountMessageSuccess = false
                                accountMessage = "Enter your current password."
                            }

                            cleanEmail.equals(
                                displayedEmail,
                                ignoreCase = true
                            ) -> {
                                accountMessageSuccess = false
                                accountMessage = "Enter a different email address."
                            }

                            else -> {
                                showEmailDialog = false
                                showEmailConfirmation = true
                            }
                        }
                    },
                    enabled = !isProcessing
                ) {
                    Text(
                        text = "Continue",
                        color = Blue,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showEmailDialog = false
                    },
                    enabled = !isProcessing
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showEmailConfirmation) {
        AlertDialog(
            onDismissRequest = {
                if (!isProcessing) {
                    showEmailConfirmation = false
                }
            },
            title = {
                Text(
                    text = "Confirm Email Change",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Change your account email from $displayedEmail to ${newEmail.trim()}?"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        isProcessing = true
                        accountMessage = ""

                        updateCrayFarmEmail(
                            currentPassword = emailCurrentPassword,
                            newEmail = newEmail.trim(),
                            onSuccess = {
                                displayedEmail = newEmail.trim()
                                newEmail = ""
                                emailCurrentPassword = ""
                                isProcessing = false
                                showEmailConfirmation = false
                                accountMessageSuccess = true
                                accountMessage =
                                    "Email updated successfully."
                            },
                            onError = { error ->
                                isProcessing = false
                                showEmailConfirmation = false
                                accountMessageSuccess = false
                                accountMessage = error
                            }
                        )
                    },
                    enabled = !isProcessing
                ) {
                    if (isProcessing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            text = "Change Email",
                            color = Blue,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showEmailConfirmation = false
                    },
                    enabled = !isProcessing
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showPasswordDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!isProcessing) {
                    showPasswordDialog = false
                }
            },
            title = {
                Text(
                    text = "Update Password",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = currentPasswordForPasswordChange,
                        onValueChange = {
                            currentPasswordForPasswordChange = it
                            accountMessage = ""
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = {
                            Text("Current password")
                        },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password
                        )
                    )

                    OutlinedTextField(
                        value = newPassword,
                        onValueChange = {
                            newPassword = it
                            accountMessage = ""
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = {
                            Text("New password")
                        },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password
                        )
                    )

                    OutlinedTextField(
                        value = confirmNewPassword,
                        onValueChange = {
                            confirmNewPassword = it
                            accountMessage = ""
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = {
                            Text("Confirm new password")
                        },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password
                        )
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        when {
                            currentPasswordForPasswordChange.isBlank() -> {
                                accountMessageSuccess = false
                                accountMessage = "Enter your current password."
                            }

                            newPassword.length < 6 -> {
                                accountMessageSuccess = false
                                accountMessage =
                                    "New password must contain at least 6 characters."
                            }

                            newPassword != confirmNewPassword -> {
                                accountMessageSuccess = false
                                accountMessage = "New passwords do not match."
                            }

                            newPassword == currentPasswordForPasswordChange -> {
                                accountMessageSuccess = false
                                accountMessage =
                                    "Your new password must be different from your current password."
                            }

                            else -> {
                                showPasswordDialog = false
                                showPasswordConfirmation = true
                            }
                        }
                    },
                    enabled = !isProcessing
                ) {
                    Text(
                        text = "Continue",
                        color = Blue,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showPasswordDialog = false
                    },
                    enabled = !isProcessing
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showPasswordConfirmation) {
        AlertDialog(
            onDismissRequest = {
                if (!isProcessing) {
                    showPasswordConfirmation = false
                }
            },
            title = {
                Text(
                    text = "Confirm Password Update",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to update your CrayFarm account password?"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        isProcessing = true
                        accountMessage = ""

                        updateCrayFarmPassword(
                            currentPassword =
                                currentPasswordForPasswordChange,
                            newPassword = newPassword,
                            onSuccess = {
                                currentPasswordForPasswordChange = ""
                                newPassword = ""
                                confirmNewPassword = ""
                                isProcessing = false
                                showPasswordConfirmation = false
                                accountMessageSuccess = true
                                accountMessage =
                                    "Password updated successfully."
                            },
                            onError = { error ->
                                isProcessing = false
                                showPasswordConfirmation = false
                                accountMessageSuccess = false
                                accountMessage = error
                            }
                        )
                    },
                    enabled = !isProcessing
                ) {
                    if (isProcessing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            text = "Update Password",
                            color = Blue,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showPasswordConfirmation = false
                    },
                    enabled = !isProcessing
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showResignDialog && user.role == "worker") {
        AlertDialog(
            onDismissRequest = {
                if (!isProcessing) {
                    showResignDialog = false
                }
            },
            title = {
                Text(
                    text = "Leave Farm?",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "This will remove your worker account from the current farm. You will need to be approved again before joining a farm."
                    )

                    OutlinedTextField(
                        value = resignationMessage,
                        onValueChange = {
                            resignationMessage = it.take(300)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = {
                            Text("Message to farm owner (optional)")
                        },
                        minLines = 3,
                        maxLines = 5
                    )

                    Text(
                        text = "${resignationMessage.length}/300",
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.End,
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        isProcessing = true
                        accountMessage = ""

                        resignWorkerFromFarm(
                            user = user,
                            message = resignationMessage.trim(),
                            onSuccess = {
                                isProcessing = false
                                showResignDialog = false
                                onLogout()
                            },
                            onError = { error ->
                                isProcessing = false
                                accountMessageSuccess = false
                                accountMessage = error
                            }
                        )
                    },
                    enabled = !isProcessing
                ) {
                    if (isProcessing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            text = "Confirm Leave Farm",
                            color = Color(0xFFC62828),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showResignDialog = false
                    },
                    enabled = !isProcessing
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Blue)
                    .statusBarsPadding()
                    .padding(horizontal = 10.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onBack
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White,
                        modifier = Modifier.size(23.dp)
                    )
                }

                Text(
                    text = "Account",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Spacer(modifier = Modifier.height(6.dp))
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
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(
                                text = displayedName,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = displayedEmail,
                                fontSize = 13.sp,
                                color = Color.Gray
                            )

                            Spacer(modifier = Modifier.height(4.dp))

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

                if (accountMessage.isNotBlank()) {
                    item {
                        Text(
                            text = accountMessage,
                            modifier = Modifier.fillMaxWidth(),
                            color = if (accountMessageSuccess) {
                                SafeGreen
                            } else {
                                Color(0xFFC62828)
                            },
                            fontSize = 13.sp
                        )
                    }
                }

                if (user.role == "admin") {
                    item {
                        SettingsOptionCard(
                            title = "Change Name",
                            subtitle = displayedName,
                            onClick = {
                                editedName = displayedName
                                accountMessage = ""
                                showNameDialog = true
                            }
                        )
                    }
                }

                item {
                    SettingsOptionCard(
                        title = "Change Email",
                        subtitle = displayedEmail,
                        onClick = {
                            newEmail = ""
                            emailCurrentPassword = ""
                            accountMessage = ""
                            showEmailDialog = true
                        }
                    )
                }

                item {
                    SettingsOptionCard(
                        title = "Update Password",
                        subtitle = "Change your account password",
                        onClick = {
                            currentPasswordForPasswordChange = ""
                            newPassword = ""
                            confirmNewPassword = ""
                            accountMessage = ""
                            showPasswordDialog = true
                        }
                    )
                }

                item {
                    SettingsOptionCard(
                        title = "Logout",
                        subtitle = "Sign out of this device",
                        onClick = {
                            showLogoutConfirmation = true
                        }
                    )
                }

                if (user.role == "worker" && user.farmId.isNotBlank()) {
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    item {
                        Text(
                            text = "Farm Membership",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Gray
                        )
                    }

                    item {
                        SettingsOptionCard(
                            title = "Resign / Leave Farm",
                            subtitle = "Leave your current farm and notify the owner",
                            danger = true,
                            onClick = {
                                resignationMessage = ""
                                accountMessage = ""
                                showResignDialog = true
                            }
                        )
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(18.dp))
                }
            }
        }
    }
}

private fun updateUserDisplayName(
    uid: String,
    newName: String,
    onSuccess: () -> Unit,
    onError: (String) -> Unit
) {
    if (uid.isBlank()) {
        onError("Unable to identify this account.")
        return
    }

    FirebaseDatabase.getInstance()
        .getReference("users")
        .child(uid)
        .child("fullName")
        .setValue(newName)
        .addOnSuccessListener {
            onSuccess()
        }
        .addOnFailureListener { exception ->
            onError(
                exception.message
                    ?: "Unable to update your name."
            )
        }
}

private fun updateWorkerPersonalInformation(
    uid: String,
    fullName: String,
    age: Int,
    birthday: String,
    contactNumber: String,
    address: String,
    onSuccess: () -> Unit,
    onError: (String) -> Unit
) {
    if (uid.isBlank()) {
        onError("Unable to identify this account.")
        return
    }

    val updates = mapOf<String, Any>(
        "fullName" to fullName,
        "age" to age,
        "birthday" to birthday,
        "contactNumber" to contactNumber,
        "address" to address,
        "profileUpdatedAt" to System.currentTimeMillis()
    )

    FirebaseDatabase.getInstance()
        .getReference("users")
        .child(uid)
        .updateChildren(updates)
        .addOnSuccessListener {
            onSuccess()
        }
        .addOnFailureListener { exception ->
            onError(
                exception.message
                    ?: "Unable to update personal information."
            )
        }
}

private fun isValidBirthday(value: String): Boolean {
    if (!Regex("""\d{4}-\d{2}-\d{2}""").matches(value)) {
        return false
    }

    return try {
        val formatter = SimpleDateFormat(
            "yyyy-MM-dd",
            Locale.US
        )

        formatter.isLenient = false

        val date = formatter.parse(value)
            ?: return false

        date.time <= System.currentTimeMillis()
    } catch (_: Exception) {
        false
    }
}

private fun updateCrayFarmEmail(
    currentPassword: String,
    newEmail: String,
    onSuccess: () -> Unit,
    onError: (String) -> Unit
) {
    val firebaseUser = FirebaseAuth.getInstance().currentUser

    if (firebaseUser == null) {
        onError("Your session has expired. Please log in again.")
        return
    }

    val currentEmail = firebaseUser.email.orEmpty()

    if (currentEmail.isBlank()) {
        onError("Unable to verify your current email.")
        return
    }

    val credential = EmailAuthProvider.getCredential(
        currentEmail,
        currentPassword
    )

    firebaseUser.reauthenticate(credential)
        .addOnSuccessListener {
            firebaseUser.updateEmail(newEmail)
                .addOnSuccessListener {
                    FirebaseDatabase.getInstance()
                        .getReference("users")
                        .child(firebaseUser.uid)
                        .child("email")
                        .setValue(newEmail)
                        .addOnSuccessListener {
                            onSuccess()
                        }
                        .addOnFailureListener { exception ->
                            onError(
                                exception.message
                                    ?: "Email changed in Authentication, but the profile email could not be updated."
                            )
                        }
                }
                .addOnFailureListener { exception ->
                    onError(
                        accountAuthErrorMessage(
                            exception.message,
                            fallback = "Unable to update email."
                        )
                    )
                }
        }
        .addOnFailureListener { exception ->
            onError(
                accountAuthErrorMessage(
                    exception.message,
                    fallback = "Current password is incorrect."
                )
            )
        }
}

private fun updateCrayFarmPassword(
    currentPassword: String,
    newPassword: String,
    onSuccess: () -> Unit,
    onError: (String) -> Unit
) {
    val firebaseUser = FirebaseAuth.getInstance().currentUser

    if (firebaseUser == null) {
        onError("Your session has expired. Please log in again.")
        return
    }

    val currentEmail = firebaseUser.email.orEmpty()

    if (currentEmail.isBlank()) {
        onError("Unable to verify your account.")
        return
    }

    val credential = EmailAuthProvider.getCredential(
        currentEmail,
        currentPassword
    )

    firebaseUser.reauthenticate(credential)
        .addOnSuccessListener {
            firebaseUser.updatePassword(newPassword)
                .addOnSuccessListener {
                    onSuccess()
                }
                .addOnFailureListener { exception ->
                    onError(
                        accountAuthErrorMessage(
                            exception.message,
                            fallback = "Unable to update password."
                        )
                    )
                }
        }
        .addOnFailureListener { exception ->
            onError(
                accountAuthErrorMessage(
                    exception.message,
                    fallback = "Current password is incorrect."
                )
            )
        }
}

private fun resignWorkerFromFarm(
    user: CrayFarmUser,
    message: String,
    onSuccess: () -> Unit,
    onError: (String) -> Unit
) {
    if (
        user.role != "worker" ||
        user.uid.isBlank() ||
        user.farmId.isBlank()
    ) {
        onError("This worker account is not connected to a farm.")
        return
    }

    val database = FirebaseDatabase.getInstance().reference
    val resignedAt = System.currentTimeMillis()

    val logKey = database
        .child("farms")
        .child(user.farmId)
        .child("activityLogs")
        .push()
        .key

    if (logKey == null) {
        onError("Unable to create a resignation record.")
        return
    }

    val logMessage = buildString {
        append("${user.fullName} left the farm.")

        if (message.isNotBlank()) {
            append(" Message: ")
            append(message)
        }
    }

    val activityLog = createActivityLogMap(
        type = "worker_resigned",
        title = "Worker left farm",
        message = logMessage,
        actorUid = user.uid,
        actorName = user.fullName,
        targetUid = user.uid,
        targetName = user.fullName,
        timestamp = resignedAt
    )

    val updates = hashMapOf<String, Any?>(
        "users/${user.uid}/farmId" to "",
        "users/${user.uid}/previousFarmId" to user.farmId,
        "users/${user.uid}/employmentStatus" to EMPLOYMENT_STATUS_RESIGNED,
        "users/${user.uid}/approved" to false,
        "users/${user.uid}/resignedAt" to resignedAt,
        "users/${user.uid}/resignationMessage" to message,
        "farms/${user.farmId}/members/${user.uid}/role" to "worker",
        "farms/${user.farmId}/members/${user.uid}/status" to EMPLOYMENT_STATUS_RESIGNED,
        "farms/${user.farmId}/members/${user.uid}/active" to false,
        "farms/${user.farmId}/members/${user.uid}/fullName" to user.fullName,
        "farms/${user.farmId}/members/${user.uid}/email" to user.email,
        "farms/${user.farmId}/members/${user.uid}/age" to user.age,
        "farms/${user.farmId}/members/${user.uid}/address" to user.address,
        "farms/${user.farmId}/members/${user.uid}/contactNumber" to user.contactNumber,
        "farms/${user.farmId}/members/${user.uid}/birthday" to user.birthday,
        "farms/${user.farmId}/members/${user.uid}/leftAt" to resignedAt,
        "farms/${user.farmId}/members/${user.uid}/resignedAt" to resignedAt,
        "farms/${user.farmId}/members/${user.uid}/resignationMessage" to message,
        "farms/${user.farmId}/activityLogs/$logKey" to activityLog
    )

    database.updateChildren(updates)
        .addOnSuccessListener {
            onSuccess()
        }
        .addOnFailureListener { exception ->
            onError(
                exception.message
                    ?: "Unable to leave the farm."
            )
        }
}

private fun accountAuthErrorMessage(
    rawMessage: String?,
    fallback: String
): String {
    val message = rawMessage.orEmpty()

    return when {
        message.contains(
            "password is invalid",
            ignoreCase = true
        ) ||
                message.contains(
                    "invalid credential",
                    ignoreCase = true
                ) -> {
            "Current password is incorrect."
        }

        message.contains(
            "already in use",
            ignoreCase = true
        ) -> {
            "That email address is already being used by another account."
        }

        message.contains(
            "requires recent login",
            ignoreCase = true
        ) -> {
            "Please log out, log back in, and try again."
        }

        message.contains(
            "network",
            ignoreCase = true
        ) -> {
            "Check your internet connection and try again."
        }

        message.isNotBlank() -> message

        else -> fallback
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
    onMinWaterLevelChanged: (String) -> Unit,
    maxWaterLevel: String,
    onMaxWaterLevelChanged: (String) -> Unit
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

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ThresholdField(
                    modifier = Modifier.weight(1f),
                    label = "Min Water (cm)",
                    value = minWaterLevel,
                    onValueChanged = onMinWaterLevelChanged
                )

                ThresholdField(
                    modifier = Modifier.weight(1f),
                    label = "Max Water (cm)",
                    value = maxWaterLevel,
                    onValueChanged = onMaxWaterLevelChanged
                )
            }
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
    var fullName by remember(user.uid) {
        mutableStateOf(user.fullName)
    }

    var age by remember(user.uid) {
        mutableStateOf(user.age)
    }

    var address by remember(user.uid) {
        mutableStateOf(user.address)
    }

    var contactNumber by remember(user.uid) {
        mutableStateOf(user.contactNumber)
    }

    var birthday by remember(user.uid) {
        mutableStateOf(user.birthday)
    }

    var farmName by remember(user.farmId) {
        mutableStateOf(user.farmId)
    }

    var fieldToEdit by remember {
        mutableStateOf<String?>(null)
    }

    var fieldValue by remember {
        mutableStateOf("")
    }

    var showFieldConfirmation by remember {
        mutableStateOf(false)
    }

    var pendingField by remember {
        mutableStateOf<String?>(null)
    }

    var pendingValue by remember {
        mutableStateOf("")
    }

    var pendingAge by remember {
        mutableStateOf(0)
    }

    var isSaving by remember {
        mutableStateOf(false)
    }

    var message by remember {
        mutableStateOf("")
    }

    var messageIsSuccess by remember {
        mutableStateOf(false)
    }

    val userRef = remember(user.uid) {
        FirebaseDatabase.getInstance()
            .getReference("users")
            .child(user.uid)
    }

    val farmRef = remember(user.farmId) {
        FirebaseDatabase.getInstance()
            .getReference("farms")
            .child(user.farmId)
    }

    DisposableEffect(userRef) {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                fullName = snapshot.child("fullName")
                    .getValue(String::class.java)
                    .orEmpty()
                    .ifBlank { fullName }

                age = (snapshot.child("age").value as? Number)
                    ?.toInt()
                    ?: 0

                address = snapshot.child("address")
                    .getValue(String::class.java)
                    .orEmpty()

                contactNumber = snapshot.child("contactNumber")
                    .getValue(String::class.java)
                    .orEmpty()

                birthday = snapshot.child("birthday")
                    .getValue(String::class.java)
                    .orEmpty()
            }

            override fun onCancelled(error: DatabaseError) {
                messageIsSuccess = false
                message = error.message
            }
        }

        userRef.addValueEventListener(listener)

        onDispose {
            userRef.removeEventListener(listener)
        }
    }

    DisposableEffect(farmRef) {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                farmName = snapshot.child("farmName")
                    .getValue(String::class.java)
                    .orEmpty()
                    .ifBlank {
                        snapshot.child("name")
                            .getValue(String::class.java)
                            .orEmpty()
                    }
                    .ifBlank {
                        user.farmId
                    }
            }

            override fun onCancelled(error: DatabaseError) {
                farmName = user.farmId
            }
        }

        farmRef.addValueEventListener(listener)

        onDispose {
            farmRef.removeEventListener(listener)
        }
    }

    fieldToEdit?.let { field ->
        AlertDialog(
            onDismissRequest = {
                if (!isSaving) {
                    fieldToEdit = null
                }
            },
            title = {
                Text(
                    text = when (field) {
                        "fullName" -> "Change Name"
                        "age" -> "Change Age"
                        "birthday" -> "Change Birthday"
                        "contactNumber" -> "Change Contact Number"
                        "address" -> "Change Address"
                        else -> "Edit Information"
                    },
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                when (field) {
                    "age" -> {
                        OutlinedTextField(
                            value = fieldValue,
                            onValueChange = { value ->
                                fieldValue = value
                                    .filter { it.isDigit() }
                                    .take(3)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = {
                                Text("Age")
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number
                            )
                        )
                    }

                    "birthday" -> {
                        OutlinedTextField(
                            value = fieldValue,
                            onValueChange = {
                                fieldValue = it.take(10)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = {
                                Text("Birthday")
                            },
                            supportingText = {
                                Text("Format: YYYY-MM-DD")
                            },
                            singleLine = true
                        )
                    }

                    "contactNumber" -> {
                        OutlinedTextField(
                            value = fieldValue,
                            onValueChange = { value ->
                                fieldValue = value
                                    .filter { character ->
                                        character.isDigit() ||
                                                character == '+' ||
                                                character == '-' ||
                                                character == ' '
                                    }
                                    .take(20)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = {
                                Text("Contact No.")
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Phone
                            )
                        )
                    }

                    "address" -> {
                        OutlinedTextField(
                            value = fieldValue,
                            onValueChange = {
                                fieldValue = it.take(180)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = {
                                Text("Address")
                            },
                            minLines = 2,
                            maxLines = 4
                        )
                    }

                    else -> {
                        OutlinedTextField(
                            value = fieldValue,
                            onValueChange = {
                                fieldValue = it.take(60)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = {
                                Text("Full name")
                            },
                            singleLine = true
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val cleanValue = fieldValue.trim()

                        when (field) {
                            "fullName" -> {
                                if (cleanValue.length < 2) {
                                    messageIsSuccess = false
                                    message = "Enter a valid full name."
                                    return@TextButton
                                }

                                pendingField = field
                                pendingValue = cleanValue
                            }

                            "age" -> {
                                val ageValue = cleanValue.toIntOrNull()

                                if (ageValue == null || ageValue !in 1..120) {
                                    messageIsSuccess = false
                                    message = "Enter a valid age."
                                    return@TextButton
                                }

                                pendingField = field
                                pendingAge = ageValue
                                pendingValue = cleanValue
                            }

                            "birthday" -> {
                                if (!isValidBirthday(cleanValue)) {
                                    messageIsSuccess = false
                                    message = "Enter birthday as YYYY-MM-DD."
                                    return@TextButton
                                }

                                pendingField = field
                                pendingValue = cleanValue
                            }

                            "contactNumber" -> {
                                if (cleanValue.length < 7) {
                                    messageIsSuccess = false
                                    message = "Enter a valid contact number."
                                    return@TextButton
                                }

                                pendingField = field
                                pendingValue = cleanValue
                            }

                            "address" -> {
                                if (cleanValue.length < 3) {
                                    messageIsSuccess = false
                                    message = "Enter a valid address."
                                    return@TextButton
                                }

                                pendingField = field
                                pendingValue = cleanValue
                            }
                        }

                        fieldToEdit = null
                        showFieldConfirmation = true
                    },
                    enabled = !isSaving
                ) {
                    Text(
                        text = "Continue",
                        color = Blue,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        fieldToEdit = null
                    },
                    enabled = !isSaving
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showFieldConfirmation) {
        AlertDialog(
            onDismissRequest = {
                if (!isSaving) {
                    showFieldConfirmation = false
                }
            },
            title = {
                Text(
                    text = "Confirm Change",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Save this change to your worker profile?"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val field = pendingField
                            ?: return@TextButton

                        isSaving = true
                        message = ""

                        val value: Any = if (field == "age") {
                            pendingAge
                        } else {
                            pendingValue
                        }

                        userRef.child(field)
                            .setValue(value)
                            .addOnSuccessListener {
                                isSaving = false
                                showFieldConfirmation = false
                                messageIsSuccess = true
                                message = "Profile information updated."
                            }
                            .addOnFailureListener { exception ->
                                isSaving = false
                                showFieldConfirmation = false
                                messageIsSuccess = false
                                message = exception.message
                                    ?: "Unable to update profile information."
                            }
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
                    onClick = {
                        showFieldConfirmation = false
                    },
                    enabled = !isSaving
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
            PageHeader(title = "Profile")

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Spacer(modifier = Modifier.height(6.dp))
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
                                text = fullName,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = user.email,
                                fontSize = 14.sp,
                                color = Color.Gray
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = "Worker",
                                color = Blue,
                                fontWeight = FontWeight.Medium
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = "Farm: $farmName",
                                color = Color.Gray
                            )
                        }
                    }
                }

                item {
                    Text(
                        text = "Personal Information",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                item {
                    WorkerEditableProfileField(
                        label = "Name",
                        value = fullName,
                        onClick = {
                            fieldValue = fullName
                            message = ""
                            fieldToEdit = "fullName"
                        }
                    )
                }

                item {
                    WorkerEditableProfileField(
                        label = "Age",
                        value = if (age > 0) {
                            age.toString()
                        } else {
                            "Not provided"
                        },
                        onClick = {
                            fieldValue =
                                if (age > 0) age.toString() else ""
                            message = ""
                            fieldToEdit = "age"
                        }
                    )
                }

                item {
                    WorkerEditableProfileField(
                        label = "Birthday",
                        value = birthday.ifBlank {
                            "Not provided"
                        },
                        onClick = {
                            fieldValue = birthday
                            message = ""
                            fieldToEdit = "birthday"
                        }
                    )
                }

                item {
                    WorkerEditableProfileField(
                        label = "Contact No.",
                        value = contactNumber.ifBlank {
                            "Not provided"
                        },
                        onClick = {
                            fieldValue = contactNumber
                            message = ""
                            fieldToEdit = "contactNumber"
                        }
                    )
                }

                item {
                    WorkerEditableProfileField(
                        label = "Address",
                        value = address.ifBlank {
                            "Not provided"
                        },
                        onClick = {
                            fieldValue = address
                            message = ""
                            fieldToEdit = "address"
                        }
                    )
                }

                if (message.isNotBlank()) {
                    item {
                        Text(
                            text = message,
                            modifier = Modifier.fillMaxWidth(),
                            color = if (messageIsSuccess) {
                                SafeGreen
                            } else {
                                Color(0xFFC62828)
                            },
                            fontSize = 13.sp
                        )
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(18.dp))
                }
            }
        }
    }
}

@Composable
fun WorkerEditableProfileField(
    label: String,
    value: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp
        ),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = label,
                    fontSize = 12.sp,
                    color = Color.Gray,
                    fontWeight = FontWeight.Medium
                )

                Spacer(modifier = Modifier.height(3.dp))

                Text(
                    text = value,
                    fontSize = 15.sp,
                    color = Color(0xFF222222)
                )
            }

            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = Color(0xFF78809A),
                modifier = Modifier.size(21.dp)
            )
        }
    }
}

@Composable
fun WorkerProfileInformationCard(
    age: Int,
    birthday: String,
    contactNumber: String,
    address: String
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
            WorkerInfoRow(
                label = "Age",
                value = if (age > 0) {
                    age.toString()
                } else {
                    "Not provided"
                }
            )

            WorkerInfoRow(
                label = "Birthday",
                value = birthday.ifBlank {
                    "Not provided"
                }
            )

            WorkerInfoRow(
                label = "Contact No.",
                value = contactNumber.ifBlank {
                    "Not provided"
                }
            )

            WorkerInfoRow(
                label = "Address",
                value = address.ifBlank {
                    "Not provided"
                }
            )
        }
    }
}


