package com.cordobot.scanvault.presentation.scanner

import android.content.Context
import android.net.Uri
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.atomic.AtomicBoolean

data class DetectedCode(val rawValue: String, val format: String)

/**
 * Runs ML Kit on camera frames. Frames arrive with STRATEGY_KEEP_ONLY_LATEST, so a slow device
 * drops frames instead of queuing them; after a detection the analyzer stays paused until
 * [resume] so the same code is not reported dozens of times.
 */
class BarcodeAnalyzer(private val onDetected: (DetectedCode) -> Unit) : ImageAnalysis.Analyzer {

    private val scanner: BarcodeScanner = BarcodeScanning.getClient()
    private val paused = AtomicBoolean(false)

    fun resume() = paused.set(false)

    @androidx.annotation.OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (paused.get() || mediaImage == null) {
            imageProxy.close()
            return
        }
        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        scanner.process(image)
            .addOnSuccessListener { codes ->
                val code = codes.firstOrNull { !it.rawValue.isNullOrEmpty() }
                if (code != null && paused.compareAndSet(false, true)) {
                    onDetected(DetectedCode(code.rawValue!!, formatName(code.format)))
                }
            }
            .addOnCompleteListener { imageProxy.close() }
    }

    fun close() = scanner.close()
}

/** Reads a code from a picture chosen in the gallery. */
fun scanImage(context: Context, uri: Uri, onResult: (DetectedCode?) -> Unit) {
    val scanner = BarcodeScanning.getClient()
    val image = runCatching { InputImage.fromFilePath(context, uri) }.getOrElse {
        onResult(null)
        return
    }
    scanner.process(image)
        .addOnSuccessListener { codes ->
            onResult(codes.firstOrNull { !it.rawValue.isNullOrEmpty() }?.let { DetectedCode(it.rawValue!!, formatName(it.format)) })
        }
        .addOnFailureListener { onResult(null) }
        .addOnCompleteListener { scanner.close() }
}

fun formatName(format: Int): String = when (format) {
    Barcode.FORMAT_QR_CODE -> "QR"
    Barcode.FORMAT_AZTEC -> "Aztec"
    Barcode.FORMAT_DATA_MATRIX -> "Data Matrix"
    Barcode.FORMAT_PDF417 -> "PDF417"
    Barcode.FORMAT_EAN_13 -> "EAN-13"
    Barcode.FORMAT_EAN_8 -> "EAN-8"
    Barcode.FORMAT_UPC_A -> "UPC-A"
    Barcode.FORMAT_UPC_E -> "UPC-E"
    Barcode.FORMAT_CODE_128 -> "Code 128"
    Barcode.FORMAT_CODE_39 -> "Code 39"
    Barcode.FORMAT_CODE_93 -> "Code 93"
    Barcode.FORMAT_CODABAR -> "Codabar"
    Barcode.FORMAT_ITF -> "ITF"
    else -> "Código"
}
