package com.priyankvasa.android.cameraviewexSample.camera

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.media.Image
import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.DrawableRes
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import com.google.firebase.ml.vision.FirebaseVision
import com.google.firebase.ml.vision.barcode.FirebaseVisionBarcode
import com.google.firebase.ml.vision.barcode.FirebaseVisionBarcodeDetectorOptions
import com.google.firebase.ml.vision.common.FirebaseVisionImage
import com.priyankvasa.android.cameraviewex.AudioEncoder
import com.priyankvasa.android.cameraviewex.ErrorLevel
import com.priyankvasa.android.cameraviewex.Modes
import com.priyankvasa.android.cameraviewex.VideoSize
import com.priyankvasa.android.cameraviewexSample.R
import com.priyankvasa.android.cameraviewexSample.extensions.toast
import kotlinx.android.synthetic.main.fragment_camera.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.BufferedOutputStream
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

open class CameraFragment : Fragment() {

    private var isVideoRecording = false

    private val imageCaptureListener = View.OnClickListener { camera.capture() }

    private val imageOutputDirectory =
            "${Environment.getExternalStorageDirectory().absolutePath}/CameraViewEx/images".also { File(it).mkdirs() }

    private val videoOutputDirectory =
            "${Environment.getExternalStorageDirectory().absolutePath}/CameraViewEx/videos".also { File(it).mkdirs() }

    private var videoFile: File? = null

    private val nextImageFile: File
        get() = File(imageOutputDirectory, "image_${System.currentTimeMillis()}.jpg")

    private val nextVideoFile: File
        get() = File(videoOutputDirectory, "video_${System.currentTimeMillis()}.mp4")

    @SuppressLint("MissingPermission")
    private val videoCaptureListener = View.OnClickListener {
        if (isVideoRecording) {
            if (camera.stopVideoRecording()) context?.toast("Video saved to ${videoFile?.absolutePath}")
            else context?.toast("Failed to save video!")
            ivPlayPause.visibility = View.GONE
            ivPlayPause.isActivated = false
            ivCaptureButton.isActivated = false
        } else {
            videoFile = nextVideoFile.also { outputFile ->
                camera.startVideoRecording(outputFile) {
                    audioEncoder = AudioEncoder.Aac
                    videoFrameRate = 60
                    videoStabilization = true
                    videoSize = VideoSize.Max
                }
            }
            ivPlayPause.visibility = View.VISIBLE
            ivPlayPause.isActivated = true
            ivCaptureButton.isActivated = true
        }
        isVideoRecording = !isVideoRecording
    }

    private val barcodeDetectorOptions = FirebaseVisionBarcodeDetectorOptions.Builder()
            .setBarcodeFormats(FirebaseVisionBarcode.FORMAT_ALL_FORMATS)
            .build()

    private val barcodeDetector = FirebaseVision.getInstance().getVisionBarcodeDetector(barcodeDetectorOptions)

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.fragment_camera, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupCamera()
        setupView()
    }

    @SuppressLint("SetTextI18n")
    private fun setupCamera() {

        with(camera) {

            val decoding = AtomicBoolean(false)

            addCameraOpenedListener { Timber.i("Camera opened.") }

            val decodeSuccessListener = listener@{ barcodes: MutableList<FirebaseVisionBarcode> ->
                if (barcodes.isEmpty()) {
                    tvBarcodes.text = "Barcodes"
                    return@listener
                }
                val barcodesStr = "Barcodes\n${barcodes.joinToString(
                        "\n",
                        transform = { it.rawValue as? CharSequence ?: "" }
                )}"
                Timber.i("Barcodes: $barcodesStr")
                tvBarcodes.text = barcodesStr
            }

            setPreviewFrameListener { image: Image ->
                if (!decoding.get()) {
                    decoding.set(true)
                    val visionImage = FirebaseVisionImage.fromMediaImage(image, 0)
                    barcodeDetector.detectInImage(visionImage)
                            .addOnCompleteListener { decoding.set(false) }
                            .addOnSuccessListener(decodeSuccessListener)
                            .addOnFailureListener { e -> Timber.e(e) }
                }
            }

            addPictureTakenListener { imageData: ByteArray ->
                GlobalScope.launch(Dispatchers.IO) { saveDataToFile(imageData) }
            }

            addCameraErrorListener { t, errorLevel ->
                when (errorLevel) {
                    ErrorLevel.Error -> Timber.e(t)
                    ErrorLevel.Warning -> Timber.w(t)
                }
            }

            addCameraClosedListener { Timber.i("Camera closed.") }
        }
    }

    private fun saveDataToFile(data: ByteArray): File = nextImageFile.apply {
        createNewFile()
        runCatching { BufferedOutputStream(outputStream()).use { it.write(data) } }
                .onFailure {
                    context?.toast("Unable to save image to file.")
                    Timber.e(it)
                }
                .onSuccess { context?.toast("Saved image to file $absolutePath") }
    }

    private fun setupView() {

        ivFlashSwitch.setOnClickListener {

            @DrawableRes val flashDrawableId: Int

            camera.flash = when (camera.flash) {
                Modes.Flash.FLASH_OFF -> {
                    flashDrawableId = R.drawable.ic_flash_auto
                    Modes.Flash.FLASH_AUTO
                }
                Modes.Flash.FLASH_AUTO -> {
                    flashDrawableId = R.drawable.ic_flash_on
                    Modes.Flash.FLASH_ON
                }
                Modes.Flash.FLASH_ON -> {
                    flashDrawableId = R.drawable.ic_flash_off
                    Modes.Flash.FLASH_OFF
                }
                else -> return@setOnClickListener
            }

            context?.let { c -> ivFlashSwitch.setImageDrawable(ActivityCompat.getDrawable(c, flashDrawableId)) }
        }

        ivCameraMode.setOnClickListener {
            camera.cameraMode = Modes.CameraMode.SINGLE_CAPTURE
            updateViewState()
        }

        ivVideoMode.setOnClickListener {
            camera.cameraMode = Modes.CameraMode.VIDEO_CAPTURE
            updateViewState()
        }

        ivBarcodeScanner.setOnClickListener {
            camera.cameraMode = Modes.CameraMode.CONTINUOUS_FRAME
            camera.facing = Modes.Facing.FACING_BACK
            updateViewState()
        }

        ivPlayPause.setOnClickListener {
            if (isVideoRecording) {
                camera.pauseVideoRecording()
                ivPlayPause.isActivated = false
            } else {
                camera.resumeVideoRecording()
                ivPlayPause.isActivated = true
            }
            isVideoRecording = !isVideoRecording
        }

        ivCameraSwitch.setOnClickListener {
            camera.facing = when (camera.facing) {
                Modes.Facing.FACING_BACK -> Modes.Facing.FACING_FRONT
                else -> Modes.Facing.FACING_BACK
            }
            updateViewState()
        }

        ivPhoto.setOnClickListener { it.visibility = View.GONE }
    }

    private fun updateViewState() {

        when (camera.cameraMode) {
            Modes.CameraMode.SINGLE_CAPTURE -> {
                tvBarcodes.visibility = View.GONE
                ivCaptureButton.visibility = View.VISIBLE
                ivPlayPause.visibility = View.GONE
                ivCameraSwitch.visibility = View.VISIBLE
                context?.let { ivCaptureButton.setImageDrawable(ActivityCompat.getDrawable(it, R.drawable.ic_camera_capture)) }
                ivCaptureButton.setOnClickListener(imageCaptureListener)
            }
            Modes.CameraMode.VIDEO_CAPTURE -> {
                tvBarcodes.visibility = View.GONE
                ivCaptureButton.visibility = View.VISIBLE
                ivCameraSwitch.visibility = View.VISIBLE
                context?.let { ivCaptureButton.setImageDrawable(ActivityCompat.getDrawable(it, R.drawable.ic_camera_video_capture)) }
                ivCaptureButton.setOnClickListener(videoCaptureListener)
            }
            Modes.CameraMode.CONTINUOUS_FRAME -> {
                tvBarcodes.visibility = View.VISIBLE
                ivCaptureButton.visibility = View.GONE
                ivPlayPause.visibility = View.GONE
                ivCameraSwitch.visibility = View.GONE
                ivCaptureButton.setOnClickListener(null)
            }
        }

        ivCameraSwitch.isActivated = camera.facing != Modes.Facing.FACING_BACK
    }

    override fun onResume() {
        super.onResume()
        updateViewState()
        if (!camera.isCameraOpened
                && ActivityCompat.checkSelfPermission(
                        requireContext(),
                        Manifest.permission.CAMERA
                ) == PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(
                        requireContext(),
                        Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(
                        requireContext(),
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED) {
            camera.start()
        }
    }

    override fun onPause() {
        camera.run { if (isCameraOpened) stop(removeAllListeners = false) }
        super.onPause()
    }

    override fun onDestroyView() {
        camera.run { if (isCameraOpened) stop(removeAllListeners = true) }
        super.onDestroyView()
    }
}