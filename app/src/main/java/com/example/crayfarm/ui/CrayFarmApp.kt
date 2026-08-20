package com.example.crayfarm

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.google.firebase.auth.FirebaseAuth

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
        mutableStateOf(AuthPage.LOGIN)
    }

    var pendingWorkerUid by remember {
        mutableStateOf("")
    }

    var pendingRequestCode by remember {
        mutableStateOf("")
    }

    var authErrorMessage by remember {
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
                    pendingWorkerUid = user.uid
                    pendingRequestCode = user.requestCode
                    authPage = AuthPage.WAITING
                    isCheckingSession = false
                },
                onError = { message ->
                    auth.signOut()
                    currentUser = null
                    isCheckingSession = false
                    authErrorMessage = message
                    authPage = AuthPage.LOGIN
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
                AuthPage.LOGIN -> {
                    LoginScreen(
                        initialErrorMessage = authErrorMessage,
                        onLoginSuccess = { user ->
                            authErrorMessage = ""
                            currentUser = user
                        },
                        onWaitingForApproval = { user ->
                            authErrorMessage = ""
                            pendingWorkerUid = user.uid
                            pendingRequestCode = user.requestCode
                            authPage = AuthPage.WAITING
                        },
                        onRegisterClick = {
                            authErrorMessage = ""
                            authPage = AuthPage.REGISTER
                        }
                    )
                }

                AuthPage.REGISTER -> {
                    UnifiedRegistrationScreen(
                        onBackToLogin = {
                            authPage = AuthPage.LOGIN
                        },
                        onOwnerRegistrationSuccess = { owner ->
                            currentUser = owner
                        },
                        onWorkerRegistrationSuccess = { workerUid, requestCode ->
                            pendingWorkerUid = workerUid
                            pendingRequestCode = requestCode
                            authPage = AuthPage.WAITING
                        }
                    )
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
                    pendingWorkerUid = ""
                    pendingRequestCode = ""
                    authErrorMessage = ""
                    authPage = AuthPage.LOGIN
                }
            )
        }
    }
}














