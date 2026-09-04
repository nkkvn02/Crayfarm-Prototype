package com.example.crayfarm

import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.FlashlightOff
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

private val ScannerBlue = Color(0xFF2639A6)

@androidx.annotation.OptIn(ExperimentalGetImage::class)
@Composable
fun DeviceQrScannerScreen(
    onDeviceIdScanned: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findComponentActivity() }
    val currentOnDeviceIdScanned by rememberUpdatedState(onDeviceIdScanned)
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    val mainExecutor = remember(context) { ContextCompat.getMainExecutor(context) }
    val scanner = remember {
        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build()
        BarcodeScanning.getClient(options)
    }
    val acceptedScan = remember { AtomicBoolean(false) }

    var camera by remember { mutableStateOf<Camera?>(null) }
    var torchEnabled by remember { mutableStateOf(false) }
    var scannerMessage by remember {
        mutableStateOf("Place the device QR code inside the frame")
    }
    var scannerError by remember { mutableStateOf("") }

    BackHandler(onBack = currentOnDismiss)

    DisposableEffect(activity, previewView) {
        if (activity == null) {
            scannerError = "Unable to start the camera on this screen."
            onDispose {
                scanner.close()
                cameraExecutor.shutdown()
            }
        } else {
            val providerFuture = ProcessCameraProvider.getInstance(context)
            var cameraProvider: ProcessCameraProvider? = null
            var disposed = false

            providerFuture.addListener(
                {
                    if (disposed) return@addListener

                    runCatching {
                        val provider = providerFuture.get()
                        cameraProvider = provider

                        val preview = Preview.Builder()
                            .build()
                            .also { useCase ->
                                useCase.setSurfaceProvider(previewView.surfaceProvider)
                            }

                        val imageAnalysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(
                                ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
                            )
                            .build()

                        imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                            val mediaImage = imageProxy.image
                            if (mediaImage == null || acceptedScan.get()) {
                                imageProxy.close()
                                return@setAnalyzer
                            }

                            val inputImage = InputImage.fromMediaImage(
                                mediaImage,
                                imageProxy.imageInfo.rotationDegrees
                            )

                            scanner.process(inputImage)
                                .addOnSuccessListener(mainExecutor) { barcodes ->
                                    if (acceptedScan.get()) return@addOnSuccessListener

                                    val rawValue = barcodes
                                        .firstOrNull()
                                        ?.rawValue
                                        ?: return@addOnSuccessListener

                                    when (val result = parseDeviceQrPayload(rawValue)) {
                                        is DeviceQrParseResult.Success -> {
                                            if (acceptedScan.compareAndSet(false, true)) {
                                                imageAnalysis.clearAnalyzer()
                                                scannerError = ""
                                                scannerMessage = "Device found"
                                                currentOnDeviceIdScanned(result.deviceId)
                                            }
                                        }

                                        is DeviceQrParseResult.Error -> {
                                            scannerError = result.message
                                        }
                                    }
                                }
                                .addOnFailureListener(mainExecutor) {
                                    scannerError =
                                        "The QR code could not be read. Please try again."
                                }
                                .addOnCompleteListener {
                                    imageProxy.close()
                                }
                        }

                        provider.unbindAll()
                        camera = provider.bindToLifecycle(
                            activity,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            imageAnalysis
                        )
                        scannerError = ""
                    }.onFailure {
                        scannerError =
                            "Unable to open the camera. You can enter the Device ID manually."
                    }
                },
                mainExecutor
            )

            onDispose {
                disposed = true
                cameraProvider?.unbindAll()
                scanner.close()
                cameraExecutor.shutdown()
            }
        }
    }

    Dialog(
        onDismissRequest = currentOnDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            AndroidView(
                factory = { previewView },
                modifier = Modifier.fillMaxSize()
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.28f))
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 36.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.48f)
                ) {
                    IconButton(onClick = currentOnDismiss) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back to Add Pond",
                            tint = Color.White
                        )
                    }
                }

                Text(
                    text = "Scan Device QR",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 19.sp
                )

                val hasFlash = camera?.cameraInfo?.hasFlashUnit() == true
                Surface(
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.48f)
                ) {
                    IconButton(
                        enabled = hasFlash,
                        onClick = {
                            torchEnabled = !torchEnabled
                            camera?.cameraControl?.enableTorch(torchEnabled)
                        }
                    ) {
                        Icon(
                            imageVector = if (torchEnabled) {
                                Icons.Rounded.FlashlightOn
                            } else {
                                Icons.Rounded.FlashlightOff
                            },
                            contentDescription = "Toggle flashlight",
                            tint = if (hasFlash) Color.White else Color.Gray
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 34.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(252.dp)
                        .border(
                            BorderStroke(3.dp, Color.White),
                            RoundedCornerShape(28.dp)
                        )
                )

                Spacer(modifier = Modifier.height(24.dp))

                Surface(
                    color = Color.Black.copy(alpha = 0.62f),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(
                            horizontal = 18.dp,
                            vertical = 13.dp
                        ),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = scannerMessage,
                            color = Color.White,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center
                        )

                        if (scannerError.isNotBlank()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = scannerError,
                                color = Color(0xFFFFCDD2),
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 24.dp, vertical = 34.dp)
                    .fillMaxWidth(),
                color = ScannerBlue.copy(alpha = 0.94f),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(
                    text = "Only scan labels beginning with CRAYFARM_DEVICE:",
                    modifier = Modifier.padding(14.dp),
                    color = Color.White,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

internal tailrec fun Context.findComponentActivity(): ComponentActivity? {
    return when (this) {
        is ComponentActivity -> this
        is ContextWrapper -> baseContext.findComponentActivity()
        else -> null
    }
}
