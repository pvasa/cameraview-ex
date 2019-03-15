package com.priyankvasa.android.cameraviewex_sample.camera

import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.util.SparseIntArray
import com.google.firebase.ml.vision.FirebaseVision
import com.google.firebase.ml.vision.barcode.FirebaseVisionBarcode
import com.google.firebase.ml.vision.barcode.FirebaseVisionBarcodeDetector
import com.google.firebase.ml.vision.barcode.FirebaseVisionBarcodeDetectorOptions
import com.google.firebase.ml.vision.common.FirebaseVisionImage
import com.google.firebase.ml.vision.common.FirebaseVisionImageMetadata
import com.priyankvasa.android.cameraviewex.Image
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicBoolean

class CameraPreviewFrameHandler(
    private var barcodeDecodeSuccessCallback: ((MutableList<FirebaseVisionBarcode>) -> Unit)?,
    private var previewAvailableCallback: ((ByteArray, Int) -> Unit)?
) {

    private val rotationToFirebaseOrientationMap: SparseIntArray = SparseIntArray()
        .apply {
            put(0, FirebaseVisionImageMetadata.ROTATION_0)
            put(90, FirebaseVisionImageMetadata.ROTATION_90)
            put(180, FirebaseVisionImageMetadata.ROTATION_180)
            put(270, FirebaseVisionImageMetadata.ROTATION_270)
        }

    private val barcodeDetectorOptions: FirebaseVisionBarcodeDetectorOptions =
        FirebaseVisionBarcodeDetectorOptions.Builder()
            .setBarcodeFormats(FirebaseVisionBarcode.FORMAT_ALL_FORMATS)
            .build()

    private val barcodeDetector: FirebaseVisionBarcodeDetector =
        FirebaseVision.getInstance().getVisionBarcodeDetector(barcodeDetectorOptions)

    /** This boolean makes sure only one frame is processed at a time by Firebase barcode detector */
    private val decoding = AtomicBoolean(false)

    val listener: (Image) -> Unit = { image: Image ->
        // Uncomment to print stats to logcat
        // printStats()
        detectBarcodes(image)
        // Uncomment to show a small preview of continuous frames
        // showPreview(image)
    }

    /**
     * This code is commented because [java.time.LocalTime] is not available on some old devices (mysteriously..)
     * Uncomment it to print preview frame listener stats like
     * time between each frame and max and min times between frames.
     */
    /*private var min: Int = Int.MAX_VALUE
    private var max: Int = Int.MIN_VALUE
    @RequiresApi(Build.VERSION_CODES.O)
    private var last: LocalTime = LocalTime.now().plusMinutes(1)

    private fun printStats() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val now = LocalTime.now()
        if (last.isAfter(now)) {
            last = now
            return
        }
        val diff = ((now.toNanoOfDay() - last.toNanoOfDay()) / 1000000).toInt()
        if (diff > max) max = diff else if (diff < min) min = diff
        last = now
        Timber.i("Preview frames stats: Diff from last frame: ${diff}ms, Min diff: ${min}ms, Max diff: ${max}ms")
    }*/

    private fun detectBarcodes(image: Image) {

        if (!decoding.compareAndSet(false, true)) return

        val firebaseRotation = rotationToFirebaseOrientationMap[image.exifInterface.rotationDegrees]

        val metadata = FirebaseVisionImageMetadata.Builder()
            .setFormat(FirebaseVisionImageMetadata.IMAGE_FORMAT_NV21)
            .setRotation(firebaseRotation)
            .setWidth(image.width)
            .setHeight(image.height)
            .build()

        detectBarcodes(FirebaseVisionImage.fromByteArray(image.data, metadata))
    }

    private fun detectBarcodes(image: FirebaseVisionImage) {
        barcodeDetector.detectInImage(image)
            .addOnCompleteListener { decoding.set(false) }
            .addOnSuccessListener { barcodeDecodeSuccessCallback?.invoke(it) }
            .addOnFailureListener { e -> Timber.e(e) }
    }

    private fun showPreview(image: Image) {

        val rotation = image.exifInterface.rotationDegrees

        val jpegDataStream = ByteArrayOutputStream()

        YuvImage(image.data, ImageFormat.NV21, image.width, image.height, null)
            .compressToJpeg(Rect(0, 0, image.width, image.height), 60, jpegDataStream)

        previewAvailableCallback?.invoke(jpegDataStream.toByteArray(), rotation)
    }

    fun release() {
        barcodeDetector.close()
        barcodeDecodeSuccessCallback = null
        previewAvailableCallback = null
    }
}