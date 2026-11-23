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
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.input.VisualTransformation
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
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
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
private val AuthPrimaryButtonColor = Color(0xFF07106A)
private val LightBlue = Color(0xFFC5CDD9)
private val SafeGreen = Color(0xFF16803B)

@Composable
fun RegistrationRoleSelectionScreen(
    onOwnerSelected: () -> Unit,
    onWorkerSelected: () -> Unit,
    onBackToLogin: () -> Unit
) {
    val backgroundBrush = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF101F65),
            Color(0xFF2839A8),
            Color(0xFF1E2D8E)
        )
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundBrush)
    ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawCircle(
                    color = Color.White.copy(alpha = 0.045f),
                    radius = size.minDimension * 0.46f,
                    center = Offset(size.width * 0.94f, size.height * 0.05f)
                )
                drawCircle(
                    color = Color(0xFF7D8CFF).copy(alpha = 0.10f),
                    radius = size.minDimension * 0.34f,
                    center = Offset(size.width * 0.02f, size.height * 0.92f)
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 22.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                ) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .size(40.dp)
                            .clickable(onClick = onBackToLogin),
                        color = Color.White.copy(alpha = 0.12f),
                        shape = CircleShape
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "Back to login",
                                tint = Color.White,
                                modifier = Modifier.size(21.dp)
                            )
                        }
                    }

                    CrayFarmBrandPill(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                Spacer(modifier = Modifier.height(30.dp))

                Text(
                    text = "Create your account",
                    color = Color.White,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center,
                    letterSpacing = (-0.5).sp
                )

                Spacer(modifier = Modifier.height(22.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFFDFDFF)
                    ),
                    elevation = CardDefaults.cardElevation(
                        defaultElevation = 10.dp
                    ),
                    shape = RoundedCornerShape(28.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp)
                    ) {
                        Text(
                            text = "Choose account type",
                            color = Color(0xFF17204A),
                            fontSize = 21.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        RoleChoiceCard(
                            role = EntryRole.OWNER,
                            title = "Farm Owner",
                            description = "Manage ponds, workers, alerts, reports, and farm settings.",
                            accessLabel = "FULL FARM ACCESS",
                            onClick = onOwnerSelected
                        )

                        Spacer(modifier = Modifier.height(13.dp))

                        RoleChoiceCard(
                            role = EntryRole.WORKER,
                            title = "Farm Worker",
                            description = "Monitor assigned ponds and support daily farm operations.",
                            accessLabel = "TEAM ACCESS",
                            onClick = onWorkerSelected
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                Text(
                    text = "Already registered? Return to login to access your account.",
                    color = Color.White.copy(alpha = 0.72f),
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }

@Composable
private fun CrayFarmBrandPill(
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = Color.White.copy(alpha = 0.13f),
        shape = RoundedCornerShape(50)
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = 13.dp,
                vertical = 8.dp
            ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(25.dp),
                color = Color.White,
                shape = CircleShape
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "C",
                        color = Blue,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = "CRAYFARM",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.4.sp
            )
        }
    }
}

@Composable
fun RoleChoiceCard(
    role: EntryRole,
    title: String,
    description: String,
    accessLabel: String,
    onClick: () -> Unit
) {
    val accent = if (role == EntryRole.OWNER) {
        Color(0xFF3147C7)
    } else {
        Color(0xFF4D64E8)
    }

    val cardShape = RoundedCornerShape(18.dp)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = accent.copy(alpha = 0.18f),
                shape = cardShape
            )
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = accent.copy(alpha = 0.045f)
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 0.dp,
            pressedElevation = 2.dp
        ),
        shape = cardShape
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(54.dp),
                color = accent,
                shape = RoundedCornerShape(15.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    RoleSelectionIcon(
                        role = role,
                        color = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = accessLabel,
                    color = accent,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp
                )

                Spacer(modifier = Modifier.height(3.dp))

                Text(
                    text = title,
                    color = Color(0xFF17204A),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(5.dp))

                Text(
                    text = description,
                    color = Color(0xFF68708A),
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                )
            }

            Spacer(modifier = Modifier.width(7.dp))

            Surface(
                modifier = Modifier.size(30.dp),
                color = accent.copy(alpha = 0.11f),
                shape = CircleShape
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.ChevronRight,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun RoleSelectionIcon(
    role: EntryRole,
    color: Color
) {
    Canvas(modifier = Modifier.size(28.dp)) {
        val stroke = 2.2.dp.toPx()
        val outline = Stroke(
            width = stroke,
            cap = StrokeCap.Round
        )

        if (role == EntryRole.OWNER) {
            drawCircle(
                color = color,
                radius = size.minDimension * 0.17f,
                center = Offset(size.width * 0.5f, size.height * 0.31f),
                style = outline
            )
            drawArc(
                color = color,
                startAngle = 200f,
                sweepAngle = 140f,
                useCenter = false,
                topLeft = Offset(size.width * 0.22f, size.height * 0.48f),
                size = androidx.compose.ui.geometry.Size(
                    size.width * 0.56f,
                    size.height * 0.42f
                ),
                style = outline
            )
            drawLine(
                color = color,
                start = Offset(size.width * 0.31f, size.height * 0.12f),
                end = Offset(size.width * 0.69f, size.height * 0.12f),
                strokeWidth = stroke,
                cap = StrokeCap.Round
            )
        } else {
            drawCircle(
                color = color,
                radius = size.minDimension * 0.14f,
                center = Offset(size.width * 0.38f, size.height * 0.32f),
                style = outline
            )
            drawCircle(
                color = color,
                radius = size.minDimension * 0.12f,
                center = Offset(size.width * 0.68f, size.height * 0.39f),
                style = outline
            )
            drawArc(
                color = color,
                startAngle = 198f,
                sweepAngle = 144f,
                useCenter = false,
                topLeft = Offset(size.width * 0.12f, size.height * 0.5f),
                size = androidx.compose.ui.geometry.Size(
                    size.width * 0.52f,
                    size.height * 0.37f
                ),
                style = outline
            )
            drawArc(
                color = color,
                startAngle = 205f,
                sweepAngle = 130f,
                useCenter = false,
                topLeft = Offset(size.width * 0.48f, size.height * 0.56f),
                size = androidx.compose.ui.geometry.Size(
                    size.width * 0.38f,
                    size.height * 0.28f
                ),
                style = outline
            )
        }
    }
}

@Composable
fun LoginScreen(
    initialErrorMessage: String = "",
    onLoginSuccess: (CrayFarmUser) -> Unit,
    onWaitingForApproval: (CrayFarmUser) -> Unit,
    onRegisterClick: () -> Unit,
    onForgotPasswordClick: () -> Unit
) {
    val auth = remember { FirebaseAuth.getInstance() }

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var errorMessage by remember(initialErrorMessage) {
        mutableStateOf(initialErrorMessage)
    }
    var isLoading by remember { mutableStateOf(false) }

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
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(
                    start = 22.dp,
                    top = 28.dp,
                    end = 22.dp,
                    bottom = 18.dp
                ),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CrayFarmBrandPill()

            Spacer(modifier = Modifier.height(36.dp))

            Text(
                text = "Welcome",
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold
            )

            Spacer(modifier = Modifier.height(5.dp))

            Text(
                text = "Sign in to your CrayFarm account",
                color = Color(0xFFDCE2FF),
                fontSize = 14.sp
            )

            Spacer(modifier = Modifier.height(23.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFFFDFDFF)
                ),
                elevation = CardDefaults.cardElevation(
                    defaultElevation = 10.dp
                ),
                shape = RoundedCornerShape(26.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "CrayFarm Login",
                        color = Color(0xFF17204A),
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(16.dp))

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
                colors = authenticationFieldColors(),
                shape = RoundedCornerShape(13.dp)
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
                visualTransformation = if (passwordVisible) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                trailingIcon = {
                    PasswordVisibilityToggle(
                        passwordVisible = passwordVisible,
                        onToggle = {
                            passwordVisible = !passwordVisible
                        }
                    )
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password
                ),
                colors = authenticationFieldColors(),
                shape = RoundedCornerShape(13.dp)
            )

            TextButton(
                onClick = onForgotPasswordClick,
                enabled = !isLoading,
                modifier = Modifier.align(Alignment.End)
            ) {
                Text(
                    text = "Forgot Password?",
                    color = Blue,
                    fontWeight = FontWeight.SemiBold
                )
            }

            if (errorMessage.isNotBlank()) {
                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = errorMessage,
                    color = Color(0xFFBA1A1A),
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
                                        isLoading = false
                                        onWaitingForApproval(user)
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
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AuthPrimaryButtonColor,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(14.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        text = "Sign in",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            TextButton(
                onClick = onRegisterClick,
                enabled = !isLoading
            ) {
                Text(
                    text = "New to CrayFarm? Create an account",
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold
                )
            }

        }
    }
}

@Composable
fun ForgotPasswordScreen(
    onBackToLogin: () -> Unit
) {
    val auth = remember { FirebaseAuth.getInstance() }
    var email by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var isSuccess by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF101F65), Blue, Color(0xFF1E2D8E))
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
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(start = 22.dp, top = 20.dp, end = 22.dp, bottom = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBackToLogin) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back to login",
                        tint = Color.White
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                CrayFarmBrandPill()
                Spacer(modifier = Modifier.weight(1f))
                Spacer(modifier = Modifier.size(48.dp))
            }

            Spacer(modifier = Modifier.height(34.dp))

            Text(
                text = "Forgot Password",
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Spacer(modifier = Modifier.height(7.dp))
            Text(
                text = "We'll send a reset link to your CrayFarm email.",
                color = Color(0xFFDCE2FF),
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFDFDFF)),
                elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
                shape = RoundedCornerShape(26.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = if (isSuccess) "Check your email" else "Reset your password",
                        color = Color(0xFF17204A),
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (isSuccess) {
                            "Password reset email sent. Please check your email for instructions."
                        } else {
                            "Enter the email address associated with your account."
                        },
                        color = Color(0xFF5A6384),
                        fontSize = 14.sp
                    )

                    if (!isSuccess) {
                        Spacer(modifier = Modifier.height(18.dp))
                        OutlinedTextField(
                            value = email,
                            onValueChange = {
                                email = it
                                message = ""
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Email") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            colors = authenticationFieldColors(),
                            shape = RoundedCornerShape(13.dp)
                        )
                    }

                    if (message.isNotBlank()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = message,
                            color = if (isSuccess) SafeGreen else Color(0xFFBA1A1A),
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Spacer(modifier = Modifier.height(22.dp))
                    if (isSuccess) {
                        Button(
                            onClick = onBackToLogin,
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AuthPrimaryButtonColor,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text("Back to Login", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Button(
                            onClick = {
                                val cleanEmail = email.trim()
                                when {
                                    cleanEmail.isBlank() -> message = "Please enter your email address."
                                    !Patterns.EMAIL_ADDRESS.matcher(cleanEmail).matches() ->
                                        message = "Please enter a valid email address."
                                    else -> {
                                        isLoading = true
                                        message = ""
                                        auth.sendPasswordResetEmail(cleanEmail)
                                            .addOnSuccessListener {
                                                isLoading = false
                                                isSuccess = true
                                            }
                                            .addOnFailureListener { exception ->
                                                isLoading = false
                                                message = passwordResetErrorMessage(exception.message)
                                            }
                                    }
                                }
                            },
                            enabled = !isLoading,
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AuthPrimaryButtonColor,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(22.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text("Send Reset Link", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            TextButton(onClick = onBackToLogin, enabled = !isLoading) {
                Text(
                    text = "← Back to Login",
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

private fun passwordResetErrorMessage(rawMessage: String?): String {
    val message = rawMessage.orEmpty()
    return when {
        message.contains("network", ignoreCase = true) -> "Check your internet connection and try again."
        message.contains("too many", ignoreCase = true) -> "Too many attempts. Please try again later."
        else -> "Unable to send a reset link right now. Please try again."
    }
}

@Composable
private fun PasswordVisibilityToggle(
    passwordVisible: Boolean,
    onToggle: () -> Unit
) {
    IconButton(onClick = onToggle) {
        Icon(
            imageVector = if (passwordVisible) {
                Icons.Rounded.VisibilityOff
            } else {
                Icons.Rounded.Visibility
            },
            contentDescription = if (passwordVisible) {
                "Hide password"
            } else {
                "Show password"
            }
        )
    }
}

@Composable
private fun RegistrationBackButton(
    onBack: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
    ) {
        Surface(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .size(40.dp)
                .clickable(onClick = onBack),
            color = Color.White.copy(alpha = 0.12f),
            shape = CircleShape
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White,
                    modifier = Modifier.size(21.dp)
                )
            }
        }

        CrayFarmBrandPill(
            modifier = Modifier.align(Alignment.Center)
        )
    }
}

@Composable
private fun RegistrationFieldLabel(
    text: String
) {
    Text(
        text = text,
        modifier = Modifier
            .background(Color.White)
            .padding(horizontal = 4.dp),
        color = Color(0xFF17204A)
    )
}

@Composable
fun UnifiedRegistrationScreen(
    onBackToLogin: () -> Unit,
    onOwnerRegistrationSuccess: (CrayFarmUser) -> Unit,
    onWorkerRegistrationSuccess: (String, String) -> Unit
) {
    val auth = remember { FirebaseAuth.getInstance() }

    var fullName by remember { mutableStateOf("") }
    var accountType by remember { mutableStateOf<EntryRole?>(null) }
    var farmName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var confirmPasswordVisible by remember { mutableStateOf(false) }
    var validationErrors by remember {
        mutableStateOf(UnifiedRegistrationErrors())
    }
    var submitError by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

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
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(
                    start = 22.dp,
                    top = 26.dp,
                    end = 22.dp,
                    bottom = 14.dp
                ),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            RegistrationBackButton(onBack = onBackToLogin)

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Create your account",
                color = Color.White,
                fontSize = 26.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(20.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFFFDFDFF)
                ),
                elevation = CardDefaults.cardElevation(
                    defaultElevation = 10.dp
                ),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
            OutlinedTextField(
                value = fullName,
                onValueChange = { value ->
                    fullName = value
                    validationErrors = validationErrors.copy(fullName = null)
                    submitError = ""
                },
                modifier = Modifier.fillMaxWidth(),
                label = { RegistrationFieldLabel("Full name") },
                singleLine = true,
                colors = registrationFieldColors(),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Account type",
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFF17204A),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(9.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                RegistrationAccountTypeCard(
                    modifier = Modifier.weight(1f),
                    role = EntryRole.OWNER,
                    title = "Farm Owner",
                    subtitle = "Manage ponds, workers, alerts, reports, and farm settings.",
                    selected = accountType == EntryRole.OWNER,
                    onClick = {
                        accountType = EntryRole.OWNER
                        validationErrors = validationErrors.copy(
                            accountType = null,
                            farmName = null
                        )
                        submitError = ""
                    }
                )

                RegistrationAccountTypeCard(
                    modifier = Modifier.weight(1f),
                    role = EntryRole.WORKER,
                    title = "Farm Worker",
                    subtitle = "Monitor assigned ponds and support daily farm operations.",
                    selected = accountType == EntryRole.WORKER,
                    onClick = {
                        accountType = EntryRole.WORKER
                        validationErrors = validationErrors.copy(
                            accountType = null,
                            farmName = null
                        )
                        submitError = ""
                    }
                )
            }
            if (accountType == EntryRole.OWNER) {
                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = farmName,
                    onValueChange = { value ->
                        farmName = value
                        validationErrors = validationErrors.copy(
                            farmName = null
                        )
                        submitError = ""
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { RegistrationFieldLabel("Farm name") },
                    singleLine = true,
                    colors = registrationFieldColors(),
                    shape = RoundedCornerShape(12.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = email,
                onValueChange = { value ->
                    email = value
                    validationErrors = validationErrors.copy(email = null)
                    submitError = ""
                },
                modifier = Modifier.fillMaxWidth(),
                label = { RegistrationFieldLabel("Email") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email
                ),
                colors = registrationFieldColors(),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { value ->
                    password = value
                    validationErrors = validationErrors.copy(
                        password = null,
                        confirmPassword = null
                    )
                    submitError = ""
                },
                modifier = Modifier.fillMaxWidth(),
                label = { RegistrationFieldLabel("Password") },
                singleLine = true,
                visualTransformation = if (passwordVisible) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                trailingIcon = {
                    PasswordVisibilityToggle(
                        passwordVisible = passwordVisible,
                        onToggle = { passwordVisible = !passwordVisible }
                    )
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password
                ),
                colors = registrationFieldColors(),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = confirmPassword,
                onValueChange = { value ->
                    confirmPassword = value
                    validationErrors = validationErrors.copy(
                        confirmPassword = null
                    )
                    submitError = ""
                },
                modifier = Modifier.fillMaxWidth(),
                label = { RegistrationFieldLabel("Confirm password") },
                singleLine = true,
                visualTransformation = if (confirmPasswordVisible) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                trailingIcon = {
                    PasswordVisibilityToggle(
                        passwordVisible = confirmPasswordVisible,
                        onToggle = {
                            confirmPasswordVisible = !confirmPasswordVisible
                        }
                    )
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password
                ),
                colors = registrationFieldColors(),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(10.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp),
                contentAlignment = Alignment.Center
            ) {
                if (submitError.isNotBlank()) {
                    Text(
                        text = submitError,
                        modifier = Modifier.fillMaxWidth(),
                        color = Color(0xFFBA1A1A),
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    if (isLoading) {
                        return@Button
                    }

                    val errors = validateUnifiedRegistration(
                        fullName = fullName,
                        accountType = accountType,
                        farmName = farmName,
                        email = email,
                        password = password,
                        confirmPassword = confirmPassword
                    )
                    validationErrors = errors
                    submitError = listOfNotNull(
                        errors.fullName,
                        errors.accountType,
                        errors.farmName,
                        errors.email,
                        errors.password,
                        errors.confirmPassword
                    ).firstOrNull().orEmpty()

                    if (!errors.isValid) {
                        return@Button
                    }

                    isLoading = true

                    when (accountType) {
                        EntryRole.OWNER -> createOwnerRegistration(
                            auth = auth,
                            fullName = fullName.trim(),
                            farmName = farmName.trim(),
                            email = email.trim(),
                            password = password,
                            onSuccess = { owner ->
                                isLoading = false
                                onOwnerRegistrationSuccess(owner)
                            },
                            onError = { message ->
                                isLoading = false
                                submitError = message
                            }
                        )

                        EntryRole.WORKER -> createWorkerRegistration(
                            auth = auth,
                            fullName = fullName.trim(),
                            email = email.trim(),
                            password = password,
                            onSuccess = { workerUid, requestCode ->
                                isLoading = false
                                onWorkerRegistrationSuccess(
                                    workerUid,
                                    requestCode
                                )
                            },
                            onError = { message ->
                                isLoading = false
                                submitError = message
                            }
                        )

                        null -> isLoading = false
                    }
                },
                enabled = !isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AuthPrimaryButtonColor,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )

                    Spacer(modifier = Modifier.width(10.dp))

                    Text("Creating account...")
                } else {
                    Text(
                        text = "Create account",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            TextButton(
                onClick = onBackToLogin,
                enabled = !isLoading
            ) {
                Text(
                    text = "Already have an account? Log in",
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun RegistrationAccountTypeCard(
    modifier: Modifier,
    role: EntryRole,
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(14.dp)
    val contentColor = if (selected) {
        Color.White
    } else {
        Color(0xFF17204A)
    }

    Card(
        modifier = modifier
            .height(132.dp)
            .border(
                width = if (selected) 3.dp else 1.5.dp,
                color = if (selected) {
                    Color(0xFF17266F)
                } else {
                    Color(0xFF7D88A8)
                },
                shape = shape
            )
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                Blue
            } else {
                Color(0xFFF3F5FF)
            }
        ),
        shape = shape
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RoleSelectionIcon(role = role, color = contentColor)

                if (selected) {
                    Spacer(modifier = Modifier.width(7.dp))
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = "Selected",
                        tint = Color(0xFFB9F6CA),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(7.dp))

            Text(
                text = title,
                color = contentColor,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Text(
                text = subtitle,
                color = contentColor.copy(alpha = 0.72f),
                fontSize = 9.sp,
                lineHeight = 12.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

private fun createOwnerRegistration(
    auth: FirebaseAuth,
    fullName: String,
    farmName: String,
    email: String,
    password: String,
    onSuccess: (CrayFarmUser) -> Unit,
    onError: (String) -> Unit
) {
    auth.createUserWithEmailAndPassword(email, password)
        .addOnSuccessListener { result ->
            val firebaseUser = result.user

            if (firebaseUser == null) {
                onError("Unable to create the owner account.")
                return@addOnSuccessListener
            }

            val database = FirebaseDatabase.getInstance().reference
            val farmId = database.child("farms").push().key

            if (farmId == null) {
                firebaseUser.delete()
                onError("Unable to create a farm identifier.")
                return@addOnSuccessListener
            }

            val createdAt = System.currentTimeMillis()
            val ownerProfile = mapOf(
                "fullName" to fullName,
                "email" to email,
                "role" to "admin",
                "farmId" to farmId,
                "approved" to true,
                "active" to true,
                "employmentStatus" to "active"
            )
            val updates = mapOf<String, Any>(
                "users/${firebaseUser.uid}" to ownerProfile,
                "farms/$farmId/farmName" to farmName,
                "farms/$farmId/ownerUid" to firebaseUser.uid,
                "farms/$farmId/createdAt" to createdAt,
                "farms/$farmId/members/${firebaseUser.uid}/role" to "admin",
                "farms/$farmId/members/${firebaseUser.uid}/joinedAt" to createdAt
            )

            database.updateChildren(updates)
                .addOnSuccessListener {
                    val owner = CrayFarmUser(
                        uid = firebaseUser.uid,
                        fullName = fullName,
                        email = email,
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
                        message = "$fullName created $farmName.",
                        actorUid = firebaseUser.uid,
                        actorName = fullName
                    )

                    onSuccess(owner)
                }
                .addOnFailureListener { exception ->
                    firebaseUser.delete()
                    onError(
                        exception.message
                            ?: "Unable to save the farm owner account."
                    )
                }
        }
        .addOnFailureListener { exception ->
            onError(registrationFirebaseError(exception, "owner"))
        }
}

private fun createWorkerRegistration(
    auth: FirebaseAuth,
    fullName: String,
    email: String,
    password: String,
    onSuccess: (String, String) -> Unit,
    onError: (String) -> Unit
) {
    auth.createUserWithEmailAndPassword(email, password)
        .addOnSuccessListener { result ->
            val firebaseUser = result.user

            if (firebaseUser == null) {
                onError("Unable to create the worker account.")
                return@addOnSuccessListener
            }

            val requestCode = generateEmployeeRequestCode()
            val workerProfile = mapOf(
                "fullName" to fullName,
                "email" to email,
                "role" to "worker",
                "farmId" to "",
                "approved" to false,
                "active" to true,
                "requestCode" to requestCode
            )
            val employeeRequest = mapOf(
                "workerUid" to firebaseUser.uid,
                "workerName" to fullName,
                "workerEmail" to email,
                "code" to requestCode,
                "status" to "pending",
                "farmId" to "",
                "createdAt" to System.currentTimeMillis()
            )
            val database = FirebaseDatabase.getInstance().reference
            val updates = mapOf<String, Any>(
                "users/${firebaseUser.uid}" to workerProfile,
                "employeeRequests/$requestCode" to employeeRequest
            )

            database.updateChildren(updates)
                .addOnSuccessListener {
                    onSuccess(firebaseUser.uid, requestCode)
                }
                .addOnFailureListener { exception ->
                    firebaseUser.delete()
                    onError(
                        exception.message
                            ?: "Unable to save worker registration."
                    )
                }
        }
        .addOnFailureListener { exception ->
            onError(registrationFirebaseError(exception, "worker"))
        }
}

private fun registrationFirebaseError(
    exception: Exception,
    accountLabel: String
): String {
    return when {
        exception.message?.contains(
            "already in use",
            ignoreCase = true
        ) == true -> "This email address is already registered."

        exception.message?.contains(
            "network",
            ignoreCase = true
        ) == true -> "Check your internet connection."

        else -> exception.message
            ?: "Unable to create the $accountLabel account."
    }
}

@Composable
fun OwnerRegistrationScreen(
    onBackToAccountType: () -> Unit,
    onBackToLogin: () -> Unit,
    onRegistrationSuccess: (CrayFarmUser) -> Unit
) {
    val auth = remember { FirebaseAuth.getInstance() }

    var fullName by remember { mutableStateOf("") }
    var farmName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var confirmPasswordVisible by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

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
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            RegistrationBackButton(
                onBack = onBackToAccountType
            )

            Spacer(modifier = Modifier.height(32.dp))

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
                visualTransformation = if (passwordVisible) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                trailingIcon = {
                    PasswordVisibilityToggle(
                        passwordVisible = passwordVisible,
                        onToggle = {
                            passwordVisible = !passwordVisible
                        }
                    )
                },
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
                visualTransformation = if (confirmPasswordVisible) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                trailingIcon = {
                    PasswordVisibilityToggle(
                        passwordVisible = confirmPasswordVisible,
                        onToggle = {
                            confirmPasswordVisible = !confirmPasswordVisible
                        }
                    )
                },
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
    onBackToAccountType: () -> Unit,
    onBackToLogin: () -> Unit,
    onRegistrationSuccess: (String, String) -> Unit
) {
    val auth = remember { FirebaseAuth.getInstance() }

    var fullName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var confirmPasswordVisible by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

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
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            RegistrationBackButton(
                onBack = onBackToAccountType
            )

            Spacer(modifier = Modifier.height(32.dp))

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
                visualTransformation = if (passwordVisible) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                trailingIcon = {
                    PasswordVisibilityToggle(
                        passwordVisible = passwordVisible,
                        onToggle = {
                            passwordVisible = !passwordVisible
                        }
                    )
                },
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
                visualTransformation = if (confirmPasswordVisible) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                trailingIcon = {
                    PasswordVisibilityToggle(
                        passwordVisible = confirmPasswordVisible,
                        onToggle = {
                            confirmPasswordVisible = !confirmPasswordVisible
                        }
                    )
                },
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
fun authenticationFieldColors() =
    OutlinedTextFieldDefaults.colors(
        focusedContainerColor = Color(0xFFF7F8FC),
        unfocusedContainerColor = Color(0xFFF7F8FC),
        focusedBorderColor = Color(0xFF17266F),
        unfocusedBorderColor = Color(0xFF7D88A8),
        focusedLabelColor = Color(0xFF17266F),
        unfocusedLabelColor = Color(0xFF68708A),
        cursorColor = Blue,
        focusedTextColor = Color(0xFF17204A),
        unfocusedTextColor = Color(0xFF17204A)
    )

@Composable
fun registrationFieldColors() =
    OutlinedTextFieldDefaults.colors(
        focusedContainerColor = Color.White,
        unfocusedContainerColor = Color.White,
        disabledContainerColor = Color(0xFFF3F4F8),
        focusedBorderColor = Color(0xFF17266F),
        unfocusedBorderColor = Color(0xFF7D88A8),
        errorBorderColor = Color(0xFFBA1A1A),
        focusedTextColor = Color(0xFF17204A),
        unfocusedTextColor = Color(0xFF17204A),
        disabledTextColor = Color(0xFF68708A),
        focusedLabelColor = Color(0xFF17266F),
        unfocusedLabelColor = Color(0xFF68708A),
        focusedPlaceholderColor = Color(0xFF68708A),
        unfocusedPlaceholderColor = Color(0xFF68708A),
        cursorColor = Blue,
        errorCursorColor = Color(0xFFBA1A1A),
        focusedTrailingIconColor = Blue,
        unfocusedTrailingIconColor = Color(0xFF68708A),
        errorTrailingIconColor = Color(0xFFBA1A1A)
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
