package com.priyankvasa.android.cameraviewex_sample.camera

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.Image
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.support.annotation.DrawableRes
import android.support.v4.app.ActivityCompat
import android.support.v4.app.Fragment
import android.support.v4.app.FragmentManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.firebase.ml.vision.FirebaseVision
import com.google.firebase.ml.vision.barcode.FirebaseVisionBarcode
import com.google.firebase.ml.vision.barcode.FirebaseVisionBarcodeDetector
import com.google.firebase.ml.vision.barcode.FirebaseVisionBarcodeDetectorOptions
import com.google.firebase.ml.vision.common.FirebaseVisionImage
import com.google.firebase.ml.vision.common.FirebaseVisionImageMetadata
import com.priyankvasa.android.cameraviewex.AspectRatio
import com.priyankvasa.android.cameraviewex.ErrorLevel
import com.priyankvasa.android.cameraviewex.LegacyImage
import com.priyankvasa.android.cameraviewex.Modes
import com.priyankvasa.android.cameraviewex.VideoSize
import com.priyankvasa.android.cameraviewex_sample.R
import com.priyankvasa.android.cameraviewex_sample.extensions.hide
import com.priyankvasa.android.cameraviewex_sample.extensions.hideSystemUi
import com.priyankvasa.android.cameraviewex_sample.extensions.show
import com.priyankvasa.android.cameraviewex_sample.extensions.showSystemUi
import com.priyankvasa.android.cameraviewex_sample.extensions.toast
import kotlinx.android.synthetic.main.fragment_camera.*
import timber.log.Timber
import java.io.BufferedOutputStream
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

open class CameraFragment : Fragment(), SettingsDialogFragment.ConfigListener {

    private val imageOutputDirectory by lazy {
        "${Environment.getExternalStorageDirectory().absolutePath}/CameraViewEx/images".also { File(it).mkdirs() }
    }

    private val videoOutputDirectory by lazy {
        "${Environment.getExternalStorageDirectory().absolutePath}/CameraViewEx/videos".also { File(it).mkdirs() }
    }

    private lateinit var videoFile: File

    private val nextImageFile: File
        get() = File(imageOutputDirectory, "image_${System.currentTimeMillis()}.jpg")

    private val nextVideoFile: File
        get() = File(videoOutputDirectory, "video_${System.currentTimeMillis()}.mp4")

    @SuppressLint("MissingPermission")
    private val imageCaptureListener = View.OnClickListener { camera.capture() }

    private val decoding = AtomicBoolean(false)

    @SuppressLint("MissingPermission")
    private val videoCaptureListener = View.OnClickListener {
        if (camera.isVideoRecording) {
            camera.stopVideoRecording()
        } else {
            videoFile = nextVideoFile
            camera.startVideoRecording(videoFile) {
                videoFrameRate = 60
                videoStabilization = true
                videoSize = VideoSize.Max
            }
        }
    }

    private val barcodeDetectorOptions: FirebaseVisionBarcodeDetectorOptions =
        FirebaseVisionBarcodeDetectorOptions.Builder()
            .setBarcodeFormats(FirebaseVisionBarcode.FORMAT_ALL_FORMATS)
            .build()

    private val barcodeDetector: FirebaseVisionBarcodeDetector =
        FirebaseVision.getInstance().getVisionBarcodeDetector(barcodeDetectorOptions)

    @SuppressLint("SetTextI18n")
    private val decodeSuccessListener: (MutableList<FirebaseVisionBarcode>) -> Unit =
        listener@{ barcodes: MutableList<FirebaseVisionBarcode> ->
            if (barcodes.isEmpty()) {
                tvBarcodes?.text = "Barcodes"
                return@listener
            }
            val barcodesStr = "Barcodes\n${barcodes.joinToString(
                "\n",
                transform = { it.rawValue as? CharSequence ?: "" }
            )}"
            Timber.i("Barcodes: $barcodesStr")
            tvBarcodes?.text = barcodesStr
        }

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
        activity?.hideSystemUi()
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
        super.onPause()
        camera.stop()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        barcodeDetector.close()
        camera.destroy()
        activity?.showSystemUi()
    }

    @SuppressLint("SetTextI18n")
    private fun setupCamera() {

        with(camera) {

            addCameraOpenedListener { Timber.i("Camera opened.") }

            setPreviewFrameListener { image: Image ->
                if (decoding.compareAndSet(false, true))
                    FirebaseVisionImage.fromMediaImage(image, 0).detectBarcodes()
            }

            setLegacyPreviewFrameListener { image: LegacyImage ->

                if (decoding.compareAndSet(false, true)) {

                    val metadata = FirebaseVisionImageMetadata.Builder()
                        .setFormat(image.format)
                        .setWidth(image.width)
                        .setHeight(image.height)
                        .build()

                    FirebaseVisionImage.fromByteArray(image.data, metadata).detectBarcodes()
                }
            }

            addPictureTakenListener { imageData: ByteArray -> saveDataToFile(imageData) }

            addCameraErrorListener { t, errorLevel ->
                when (errorLevel) {
                    ErrorLevel.Error -> Timber.e(t)
                    ErrorLevel.Warning -> Timber.w(t)
                }
            }

            addVideoRecordStartedListener {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    ivPlayPause.show()
                    ivPlayPause.isActivated = true
                }
                ivVideoCaptureButton.isActivated = true
            }

            addVideoRecordStoppedListener { isSuccess: Boolean ->
                ivPlayPause.hide()
                ivPlayPause.isActivated = false
                ivVideoCaptureButton.isActivated = false
                if (isSuccess) context?.toast("Video saved to ${videoFile.absolutePath}")
                else context?.toast("Failed to save video!")
            }

            addCameraClosedListener { Timber.i("Camera closed.") }
        }
    }

    private fun FirebaseVisionImage.detectBarcodes() {
        barcodeDetector.detectInImage(this)
            .addOnCompleteListener { decoding.set(false) }
            .addOnSuccessListener(decodeSuccessListener)
            .addOnFailureListener { e -> Timber.e(e) }
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

        val context: Context = context ?: return

        ivSettings.setOnClickListener {
            val fm: FragmentManager = fragmentManager ?: return@setOnClickListener
            SettingsDialogFragment.newInstance().apply {
                setTargetFragment(this@CameraFragment, 0)
                show(fm, SettingsDialogFragment.TAG)
            }
        }

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

            ivFlashSwitch.setImageDrawable(ActivityCompat.getDrawable(context, flashDrawableId))
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
                if (camera.isVideoRecording) {
                    camera.pauseVideoRecording()
                    ivPlayPause.isActivated = false
                } else {
                    camera.resumeVideoRecording()
                    ivPlayPause.isActivated = true
                }
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

    override fun onNewAspectRatio(aspectRatio: AspectRatio) {
        camera.aspectRatio = aspectRatio
    }

    companion object {

        private val permissions: Array<String> = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )

        fun newInstance(): CameraFragment = CameraFragment()
    }
}