package com.cordobot.scanvault.presentation.scanner

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlashlightOff
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.NoPhotography
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size as GeometrySize
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewModelScope
import com.cordobot.scanvault.domain.SaveScanUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ScannerViewModel @Inject constructor(private val saveScan: SaveScanUseCase) : ViewModel() {

    private val _openResult = Channel<Long>(Channel.BUFFERED)
    val openResult = _openResult.receiveAsFlow()

    fun onCodeDetected(code: DetectedCode) {
        viewModelScope.launch { _openResult.send(saveScan(code.rawValue, code.format)) }
    }
}

private enum class CameraPermission { GRANTED, ASK, DENIED_PERMANENTLY }

@Composable
fun ScannerScreen(onResult: (Long) -> Unit, viewModel: ScannerViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var permission by remember { mutableStateOf(context.cameraPermission()) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permission = when {
            granted -> CameraPermission.GRANTED
            // Without a rationale after a denial, Android will not show the dialog again
            context.findActivity()?.shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) == true -> CameraPermission.ASK
            else -> CameraPermission.DENIED_PERMANENTLY
        }
    }
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scanImage(context, uri) { code ->
                if (code != null) viewModel.onCodeDetected(code)
                else scope.launch { snackbar.showSnackbar("No se encontró ningún código en la imagen") }
            }
        }
    }

    LaunchedEffect(Unit) {
        if (permission != CameraPermission.GRANTED) permissionLauncher.launch(Manifest.permission.CAMERA)
        viewModel.openResult.collect(onResult)
    }
    // Re-check when coming back from the system settings
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && context.cameraPermission() == CameraPermission.GRANTED) {
                permission = CameraPermission.GRANTED
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val pickImage = { galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (permission == CameraPermission.GRANTED) {
            CameraScanner(onDetected = viewModel::onCodeDetected, onPickImage = pickImage)
        } else {
            PermissionRationale(
                permanentlyDenied = permission == CameraPermission.DENIED_PERMANENTLY,
                onRequest = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                onOpenSettings = {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                    )
                },
                onPickImage = pickImage
            )
        }
        SnackbarHost(snackbar, modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp))
    }
}

@Composable
private fun CameraScanner(onDetected: (DetectedCode) -> Unit, onPickImage: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var camera by remember { mutableStateOf<Camera?>(null) }
    var torchOn by remember { mutableStateOf(false) }
    val analyzer = remember { BarcodeAnalyzer(onDetected) }
    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }

    // Bound to the screen's lifecycle: CameraX stops the camera in background (battery) and
    // everything is released when the composable leaves, so no Activity is leaked.
    DisposableEffect(lifecycleOwner) {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        val executor = ContextCompat.getMainExecutor(context)
        providerFuture.addListener({
            val provider = providerFuture.get()
            val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
            val analysis = ImageAnalysis.Builder()
                // 1280x720 is plenty for barcodes and much cheaper than full resolution
                .setResolutionSelector(
                    ResolutionSelector.Builder()
                        .setResolutionStrategy(ResolutionStrategy(Size(1280, 720), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER))
                        .build()
                )
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { it.setAnalyzer(executor, analyzer) }
            provider.unbindAll()
            camera = runCatching {
                provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
            }.getOrNull()
        }, executor)

        onDispose {
            runCatching { providerFuture.get().unbindAll() }
            analyzer.close()
            camera = null
        }
    }
    // Coming back to this screen after a result: listen again
    LaunchedEffect(Unit) { analyzer.resume() }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
        ScanFrame()
        Text(
            "Apunta a un código QR o de barras",
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 32.dp)
        )
        Row(
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 40.dp),
            horizontalArrangement = Arrangement.spacedBy(32.dp)
        ) {
            val hasTorch = camera?.cameraInfo?.hasFlashUnit() == true
            RoundButton(onClick = {
                torchOn = !torchOn
                camera?.cameraControl?.enableTorch(torchOn)
            }, enabled = hasTorch) {
                Icon(
                    if (torchOn) Icons.Default.FlashlightOn else Icons.Default.FlashlightOff,
                    contentDescription = if (torchOn) "Apagar linterna" else "Encender linterna",
                    tint = Color.White
                )
            }
            RoundButton(onClick = onPickImage) {
                Icon(Icons.Default.Image, contentDescription = "Escanear desde la galería", tint = Color.White)
            }
        }
    }
}

@Composable
private fun RoundButton(onClick: () -> Unit, enabled: Boolean = true, content: @Composable () -> Unit) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(64.dp).background(Color.Black.copy(alpha = 0.45f), CircleShape)
    ) { content() }
}

/** Dims the preview except for a rounded window with highlighted corners. */
@Composable
private fun ScanFrame() {
    val accent = MaterialTheme.colorScheme.primary
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    ) {
        val side = size.minDimension * 0.7f
        val topLeft = Offset((size.width - side) / 2, (size.height - side) / 2.4f)
        val radius = CornerRadius(32.dp.toPx())
        drawRect(Color.Black.copy(alpha = 0.55f))
        drawRoundRect(Color.Transparent, topLeft, GeometrySize(side, side), radius, blendMode = BlendMode.Clear)
        drawRoundRect(accent, topLeft, GeometrySize(side, side), radius, style = Stroke(width = 4.dp.toPx()))
    }
}

@Composable
private fun PermissionRationale(
    permanentlyDenied: Boolean,
    onRequest: () -> Unit,
    onOpenSettings: () -> Unit,
    onPickImage: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.NoPhotography, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Text("Necesitamos la cámara", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            "ScanVault usa la cámara solo para leer códigos; las imágenes se procesan en el teléfono y nunca se envían a internet.",
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(24.dp))
        if (permanentlyDenied) {
            Button(onClick = onOpenSettings) { Text("Abrir ajustes") }
        } else {
            Button(onClick = onRequest) { Text("Permitir cámara") }
        }
        Spacer(Modifier.height(12.dp))
        FilledTonalButton(onClick = onPickImage) { Text("Escanear una imagen de la galería") }
    }
}

private fun Context.cameraPermission(): CameraPermission =
    if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
        CameraPermission.GRANTED
    } else {
        CameraPermission.ASK
    }

private fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
