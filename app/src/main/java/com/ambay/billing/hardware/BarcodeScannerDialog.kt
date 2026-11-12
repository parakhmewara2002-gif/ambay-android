package com.ambay.billing.hardware

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.util.Size
import android.view.HapticFeedbackConstants
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FlashOff
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ambay.billing.ui.theme.BrandCyan
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

@OptIn(ExperimentalGetImage::class)
@Composable
fun BarcodeScannerView(
    isActive: Boolean,
    modifier: Modifier = Modifier,
    onDecoded: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val view = LocalView.current

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasPermission = granted
    }
    LaunchedEffect(Unit) {
        if (!hasPermission) permLauncher.launch(Manifest.permission.CAMERA)
    }

    var torchOn by remember { mutableStateOf(false) }
    var cameraRef by remember { mutableStateOf<Camera?>(null) }
    var zoomRatio by remember { mutableFloatStateOf(1f) }
    val decodedOnce = remember(isActive) { AtomicBoolean(false) }

    val scanLineProgress by rememberInfiniteTransition(label = "scanline").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scanline_pos"
    )

    val executor = remember { Executors.newSingleThreadExecutor() }
    val scanner = remember { BarcodeScanning.getClient() }
    val toneGen = remember { runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 80) }.getOrNull() }

    DisposableEffect(Unit) {
        onDispose {
            runCatching { scanner.close() }
            runCatching { executor.shutdown() }
            runCatching { toneGen?.release() }
        }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(28.dp))
            .background(Color.Black)
            .border(1.5.dp, Color.White.copy(alpha = 0.28f), RoundedCornerShape(28.dp))
    ) {
        if (!hasPermission) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Camera permission chahiye scan karne ke liye", color = Color.White)
            }
        } else if (isActive) {
            val previewView = remember { PreviewView(context) }

            DisposableEffect(lifecycleOwner, isActive) {
                val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
                var provider: ProcessCameraProvider? = null
                cameraProviderFuture.addListener({
                    provider = cameraProviderFuture.get()
                    val resSelector = ResolutionSelector.Builder()
                        .setResolutionStrategy(
                            ResolutionStrategy(
                                Size(1920, 1080),
                                ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                            )
                        )
                        .build()
                    val preview = Preview.Builder()
                        .setResolutionSelector(resSelector)
                        .build()
                        .also { it.surfaceProvider = previewView.surfaceProvider }

                    val analysis = ImageAnalysis.Builder()
                        .setResolutionSelector(resSelector)
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()

                    analysis.setAnalyzer(executor) { imageProxy ->
                        val mediaImg = imageProxy.image
                        if (mediaImg != null && !decodedOnce.get()) {
                            val image = InputImage.fromMediaImage(mediaImg, imageProxy.imageInfo.rotationDegrees)
                            scanner.process(image)
                                .addOnSuccessListener { barcodes ->
                                    val raw = barcodes.firstOrNull { !it.rawValue.isNullOrBlank() }?.rawValue
                                    if (raw != null && decodedOnce.compareAndSet(false, true)) {
                                        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                        runCatching { toneGen?.startTone(ToneGenerator.TONE_PROP_BEEP, 120) }
                                        provider?.unbindAll()
                                        onDecoded(raw)
                                    }
                                }
                                .addOnCompleteListener { imageProxy.close() }
                        } else {
                            imageProxy.close()
                        }
                    }

                    runCatching {
                        provider?.unbindAll()
                        cameraRef = provider?.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            analysis
                        )
                    }
                }, ContextCompat.getMainExecutor(context))

                onDispose {
                    runCatching { provider?.unbindAll() }
                    cameraRef = null
                }
            }

            AndroidView(
                factory = { previewView },
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures { offset ->
                            val cam = cameraRef ?: return@detectTapGestures
                            val point = previewView.meteringPointFactory.createPoint(offset.x, offset.y)
                            cam.cameraControl.startFocusAndMetering(FocusMeteringAction.Builder(point).build())
                        }
                    }
                    .pointerInput(Unit) {
                        detectTransformGestures { _, _, zoom, _ ->
                            val cam = cameraRef ?: return@detectTransformGestures
                            val maxZoom = cam.cameraInfo.zoomState.value?.maxZoomRatio ?: 4f
                            zoomRatio = (zoomRatio * zoom).coerceIn(1f, maxZoom)
                            cam.cameraControl.setZoomRatio(zoomRatio)
                        }
                    }
            )

            // Animated glass scan frame + scan-line
            Canvas(modifier = Modifier.fillMaxSize()) {
                val padX = size.width * 0.14f
                val padY = size.height * 0.18f
                val frameW = size.width - padX * 2f
                val frameH = size.height - padY * 2f
                drawRoundRect(
                    color = BrandCyan.copy(alpha = 0.85f),
                    topLeft = Offset(padX, padY),
                    size = androidx.compose.ui.geometry.Size(frameW, frameH),
                    cornerRadius = CornerRadius(20.dp.toPx(), 20.dp.toPx()),
                    style = Stroke(width = 2.5.dp.toPx())
                )
                val lineY = padY + frameH * scanLineProgress
                drawLine(
                    color = BrandCyan,
                    start = Offset(padX + 12f, lineY),
                    end = Offset(padX + frameW - 12f, lineY),
                    strokeWidth = 2.dp.toPx()
                )
            }

            IconButton(
                onClick = {
                    torchOn = !torchOn
                    cameraRef?.cameraControl?.enableTorch(torchOn)
                },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp)
                    .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(50))
            ) {
                Icon(
                    imageVector = if (torchOn) Icons.Rounded.FlashOn else Icons.Rounded.FlashOff,
                    contentDescription = "Torch",
                    tint = Color.White
                )
            }
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Camera paused", color = Color.White.copy(alpha = 0.8f))
            }
        }
    }
}

@Composable
fun BarcodeScannerDialog(
    title: String = "QR / Barcode Scan Karo",
    onDismiss: () -> Unit,
    onResult: (String) -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.88f))
                .padding(20.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close", tint = Color.White)
                    }
                }
                Spacer(Modifier.height(16.dp))
                BarcodeScannerView(
                    isActive = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(380.dp),
                    onDecoded = { code ->
                        onResult(code)
                    }
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "Camera ko barcode ya QR ke saamne rakho. Tap karke focus aur pinch karke zoom kar sakte hain.",
                    color = Color.White.copy(alpha = 0.78f),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}
