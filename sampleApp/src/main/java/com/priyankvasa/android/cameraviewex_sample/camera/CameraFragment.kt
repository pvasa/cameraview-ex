package com.priyankvasa.android.cameraviewex_sample.camera

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.media.Image
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.support.annotation.DrawableRes
import android.support.v4.app.ActivityCompat
import android.support.v4.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.firebase.ml.vision.FirebaseVision
import com.google.firebase.ml.vision.barcode.FirebaseVisionBarcode
import com.google.firebase.ml.vision.barcode.FirebaseVisionBarcodeDetectorOptions
import com.google.firebase.ml.vision.common.FirebaseVisionImage
import com.priyankvasa.android.cameraviewex.AudioEncoder
import com.priyankvasa.android.cameraviewex.ErrorLevel
import com.priyankvasa.android.cameraviewex.Modes
import com.priyankvasa.android.cameraviewex.VideoSize
import com.priyankvasa.android.cameraviewex_sample.R
import com.priyankvasa.android.cameraviewex_sample.extensions.hide
import com.priyankvasa.android.cameraviewex_sample.extensions.hideSystemUI
import com.priyankvasa.android.cameraviewex_sample.extensions.show
import com.priyankvasa.android.cameraviewex_sample.extensions.showSystemUI
import com.priyankvasa.android.cameraviewex_sample.extensions.toast
import kotlinx.android.synthetic.main.fragment_camera.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.BufferedOutputStream
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.CoroutineContext

open class CameraFragment : Fragment(), CoroutineScope {

    private val job: Job = SupervisorJob()

    override val coroutineContext: CoroutineContext get() = Dispatchers.Main + job

    private val imageOutputDirectory by lazy {
        "${Environment.getExternalStorageDirectory().absolutePath}/CameraViewEx/images".also { File(it).mkdirs() }
    }

    private val videoOutputDirectory by lazy {
        "${Environment.getExternalStorageDirectory().absolutePath}/CameraViewEx/videos".also { File(it).mkdirs() }
    }

    private var videoFile: File? = null

    private val nextImageFile: File
        get() = File(imageOutputDirectory, "image_${System.currentTimeMillis()}.jpg")

    private val nextVideoFile: File
        get() = File(videoOutputDirectory, "video_${System.currentTimeMillis()}.mp4")

    @SuppressLint("MissingPermission")
    private val imageCaptureListener = View.OnClickListener { camera.capture() }

    private var isVideoRecording = false

    @SuppressLint("MissingPermission")
    private val videoCaptureListener = View.OnClickListener {
        if (isVideoRecording) {
            camera.stopVideoRecording()
            ivPlayPause.hide()
            ivPlayPause.isActivated = false
            ivVideoCaptureButton.isActivated = false
        } else {
            videoFile = nextVideoFile.also { outputFile ->
                camera.startVideoRecording(outputFile) {
                    audioEncoder = AudioEncoder.Aac
                    videoFrameRate = 60
                    videoStabilization = true
                    videoSize = VideoSize.Max
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                ivPlayPause.show()
                ivPlayPause.isActivated = true
            }
            ivVideoCaptureButton.isActivated = true
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

    @SuppressLint("MissingPermission")
    override fun onResume() {
        super.onResume()
        activity?.hideSystemUI()
        updateViewState()
        checkPermissions().let { if (it.isEmpty()) camera.start() else requestPermissions(it, 1) }
    }

    private fun checkPermissions(): Array<String> {

        val context = context ?: return permissions

        return permissions
            .filter { ActivityCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }
            .toTypedArray()
    }

    override fun onPause() {
        camera.stop()
        super.onPause()
    }

    override fun onDestroyView() {
        camera.destroy()
        job.cancel()
        activity?.showSystemUI()
        super.onDestroyView()
    }

    @SuppressLint("SetTextI18n")
    private fun setupCamera() {

        with(camera) {

            val decoding = AtomicBoolean(false)

            addCameraOpenedListener { Timber.i("Camera opened.") }

            val decodeSuccessListener =
                listener@{ barcodes: MutableList<FirebaseVisionBarcode> ->
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
                launch(Dispatchers.IO) { saveDataToFile(imageData) }
            }

            addCameraErrorListener { t, errorLevel ->
                when (errorLevel) {
                    ErrorLevel.Error -> Timber.e(t)
                    ErrorLevel.Warning -> Timber.w(t)
                }
            }

            addVideoRecordStoppedListener { isSuccess ->
                if (isSuccess) context?.toast("Video saved to ${videoFile?.absolutePath}")
                else context?.toast("Failed to save video!")
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
            camera.setCameraMode(Modes.CameraMode.SINGLE_CAPTURE)
            updateViewState()
        }

        ivVideoMode.setOnClickListener {
            camera.setCameraMode(Modes.CameraMode.VIDEO_CAPTURE)
            updateViewState()
        }

        ivBarcodeScanner.setOnClickListener {
            camera.setCameraMode(Modes.CameraMode.CONTINUOUS_FRAME or Modes.CameraMode.VIDEO_CAPTURE)
            camera.facing = Modes.Facing.FACING_BACK
            updateViewState()
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
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
        }

        ivCameraSwitch.setOnClickListener {
            camera.facing = when (camera.facing) {
                Modes.Facing.FACING_BACK -> Modes.Facing.FACING_FRONT
                else -> Modes.Facing.FACING_BACK
            }
            updateViewState()
        }
    }

    private fun updateViewState() {

        if (camera.isSingleCaptureModeEnabled) {
            ivCaptureButton.show()
            ivCaptureButton.setOnClickListener(imageCaptureListener)
        } else {
            ivCaptureButton.setOnClickListener(null)
            ivCaptureButton.hide()
        }

        if (camera.isContinuousFrameModeEnabled) tvBarcodes.show() else tvBarcodes.hide()

        if (camera.isVideoCaptureModeEnabled) {
            ivVideoCaptureButton.show()
            ivVideoCaptureButton.setOnClickListener(videoCaptureListener)
        } else {
            ivVideoCaptureButton.setOnClickListener(null)
            ivPlayPause.hide()
            ivVideoCaptureButton.hide()
        }

        ivCameraSwitch.isActivated = camera.facing != Modes.Facing.FACING_BACK
    }

    companion object {

        private val permissions: Array<String> = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )

        val newInstance: CameraFragment get() = CameraFragment()
    }
}