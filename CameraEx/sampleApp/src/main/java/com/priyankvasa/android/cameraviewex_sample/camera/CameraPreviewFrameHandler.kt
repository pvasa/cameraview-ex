package com.priyankvasa.android.cameraviewex_sample.camera

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.os.SystemClock
import android.util.SparseIntArray
import com.google.android.gms.tasks.Task
import com.google.firebase.ml.vision.FirebaseVision
import com.google.firebase.ml.vision.barcode.FirebaseVisionBarcode
import com.google.firebase.ml.vision.barcode.FirebaseVisionBarcodeDetector
import com.google.firebase.ml.vision.barcode.FirebaseVisionBarcodeDetectorOptions
import com.google.firebase.ml.vision.common.FirebaseVisionImage
import com.google.firebase.ml.vision.common.FirebaseVisionImageMetadata
import com.priyankvasa.android.cameraviewex.Image
import com.priyankvasa.android.cameraviewex_sample.extensions.rotate
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicBoolean

class CameraPreviewFrameHandler(
    private var barcodeDecodeSuccessCallback: ((MutableList<FirebaseVisionBarcode>) -> Unit)?,
    private var previewAvailableCallback: ((Bitmap) -> Unit)?
) {

    val frameRate: Float = 10f

    val listener: (Image) -> Unit = { image: Image ->
        printStats()
        detectBarcodes(image)
        // Comment to stop showing the small preview of continuous frames
        // This is very heavy on memory. Consumes almost 300mbs on a Samsung S8.
        showPreview(image)
    }

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

    private val detectionCompleteListener: (Task<MutableList<FirebaseVisionBarcode>>) -> Unit =
        { task: Task<MutableList<FirebaseVisionBarcode>> ->
            if (task.isSuccessful) task.result?.let { barcodeDecodeSuccessCallback?.invoke(it) }
            else Timber.e(task.exception)
            decoding.set(false)
        }

    private val rect: Rect = Rect()

    private val jpegDataStream = ByteArrayOutputStream()

    private val options: BitmapFactory.Options =
        BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }

    /** This boolean makes sure only one frame is processed at a time by Firebase barcode detector */
    private val decoding = AtomicBoolean(false)

    /**
     * It prints preview frame listener stats like
     * time between each frame and max and min times between frames.
     */
    private var min: Long = Long.MAX_VALUE
    private var max: Long = Long.MIN_VALUE
    private var last: Long = 0L
    private var frameCount: Long = 0L
    private var totalDiff: Long = 0L

    private fun printStats() {
        ++frameCount
        val now: Long = SystemClock.elapsedRealtime()
        if (last == 0L) {
            last = now
            return
        }
        val diff: Long = now - last
        totalDiff += diff
        if (diff > max) max = diff else if (diff < min) min = diff
        last = now
        Timber.i(
            """
            Preview frame stats:
                Frame count: $frameCount
                Time from last frame: ${diff}ms
                Min diff: ${min}ms
                Max diff: ${max}ms
                Avg diff: ${totalDiff / frameCount}
                """
        )
    }

    fun resetStats() {
        min = Long.MAX_VALUE
        max = Long.MIN_VALUE
        last = 0L
        frameCount = 0L
        totalDiff = 0L
    }

    private fun detectBarcodes(image: Image) {

        if (!decoding.compareAndSet(false, true)) return

        val metadata: FirebaseVisionImageMetadata = FirebaseVisionImageMetadata.Builder()
            .setFormat(FirebaseVisionImageMetadata.IMAGE_FORMAT_NV21)
            .setRotation(rotationToFirebaseOrientationMap[image.exifInterface.rotation])
            .setWidth(image.width)
            .setHeight(image.height)
            .build()

        val visionImage: FirebaseVisionImage = FirebaseVisionImage.fromByteArray(image.data, metadata)

        barcodeDetector.detectInImage(visionImage).addOnCompleteListener(detectionCompleteListener)
    }

    /** This is very heavy on memory. Consumes almost 300mbs on a Samsung S8. */
    private fun showPreview(image: Image) {

        jpegDataStream.reset()

        YuvImage(image.data, ImageFormat.NV21, image.width, image.height, null)
            .compressToJpeg(rect.apply { set(0, 0, image.width, image.height) }, 40, jpegDataStream)

        val jpegData: ByteArray = jpegDataStream.toByteArray()

        val bm: Bitmap = BitmapFactory.decodeByteArray(jpegData, 0, jpegData.size, options)
            ?: return

        previewAvailableCallback?.invoke(bm.rotate(image.exifInterface.rotation))
    }

    fun release() {
        barcodeDetector.close()
        barcodeDecodeSuccessCallback = null
        previewAvailableCallback = null
    }
}