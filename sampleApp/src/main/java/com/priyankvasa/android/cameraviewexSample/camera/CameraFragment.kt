package com.priyankvasa.android.cameraviewexSample.camera

import android.Manifest
import android.content.pm.PackageManager
import android.media.Image
import android.os.Bundle
import android.os.Environment
import android.support.annotation.DrawableRes
import android.support.v4.app.ActivityCompat
import android.support.v4.app.Fragment
import android.support.v4.view.GestureDetectorCompat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.bumptech.glide.Glide
import com.google.firebase.ml.vision.FirebaseVision
import com.google.firebase.ml.vision.barcode.FirebaseVisionBarcode
import com.google.firebase.ml.vision.barcode.FirebaseVisionBarcodeDetectorOptions
import com.google.firebase.ml.vision.common.FirebaseVisionImage
import com.priyankvasa.android.cameraviewex.Modes
import com.priyankvasa.android.cameraviewexSample.Direction
import com.priyankvasa.android.cameraviewexSample.OnSwipeListener
import com.priyankvasa.android.cameraviewexSample.R
import kotlinx.android.synthetic.main.fragment_camera.*
import timber.log.Timber
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

open class CameraFragment : Fragment() {

    private var isVideoRecording = false

    private val imageCaptureListener = View.OnClickListener { camera.capture() }

    private val videoOutputDirectory =
            "${Environment.getExternalStorageDirectory().absolutePath}/CameraViewEx/".also { File(it).mkdirs() }

    private val videoCaptureListener = View.OnClickListener {
        camera.run {
            if (isVideoRecording) {
                stopVideoRecording()
                ivPlayPause.visibility = View.GONE
                ivPlayPause.isActivated = false
                ivCaptureButton.isActivated = false
            } else {
                startVideoRecording(File(videoOutputDirectory, "video_${System.currentTimeMillis()}.mp4"))
                ivPlayPause.visibility = View.VISIBLE
                ivPlayPause.isActivated = true
                ivCaptureButton.isActivated = true
            }
        }
        isVideoRecording = !isVideoRecording
    }

    private val gestureDetector: GestureDetectorCompat by lazy {
        GestureDetectorCompat(
                context,
                object : OnSwipeListener() {
                    override fun onSwipe(direction: Direction): Boolean {
                        camera.facing = when (direction) {
                            Direction.down,
                            Direction.up ->
                                if (camera.facing == Modes.Facing.FACING_BACK) Modes.Facing.FACING_FRONT
                                else Modes.Facing.FACING_BACK
                            else -> return false
                        }
                        return true
                    }
                }
        )
    }

    private val options = FirebaseVisionBarcodeDetectorOptions.Builder()
            .setBarcodeFormats(FirebaseVisionBarcode.FORMAT_ALL_FORMATS)
            .build()

    private val detector = FirebaseVision.getInstance().getVisionBarcodeDetector(options)

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
                    detector.detectInImage(visionImage)
                            .addOnCompleteListener { decoding.set(false) }
                            .addOnSuccessListener(decodeSuccessListener)
                            .addOnFailureListener { e -> Timber.e(e) }
                }
            }

            addPictureTakenListener { imageData: ByteArray ->
                ivPhoto.visibility = View.VISIBLE
                Glide.with(this@CameraFragment)
                        .load(imageData)
                        .into(ivPhoto)
            }

            addCameraErrorListener { t -> Timber.e(t) }

            addCameraClosedListener { Timber.i("Camera closed.") }

            setOnTouchListener { _, event ->
                gestureDetector.onTouchEvent(event)
                return@setOnTouchListener true
            }
        }
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

            context?.let { ivFlashSwitch.setImageDrawable(ActivityCompat.getDrawable(it, flashDrawableId)) }
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

        ivPhoto.setOnClickListener { it.visibility = View.GONE }
    }

    private fun updateViewState() {

        when (camera.cameraMode) {
            Modes.CameraMode.SINGLE_CAPTURE -> {
                tvBarcodes.visibility = View.GONE
                ivCaptureButton.visibility = View.VISIBLE
                ivPlayPause.visibility = View.GONE
                context?.let { ivCaptureButton.setImageDrawable(ActivityCompat.getDrawable(it, R.drawable.ic_camera_capture)) }
                ivCaptureButton.setOnClickListener(imageCaptureListener)
            }
            Modes.CameraMode.VIDEO_CAPTURE -> {
                tvBarcodes.visibility = View.GONE
                ivCaptureButton.visibility = View.VISIBLE
                context?.let { ivCaptureButton.setImageDrawable(ActivityCompat.getDrawable(it, R.drawable.ic_camera_video_capture)) }
                ivCaptureButton.setOnClickListener(videoCaptureListener)
            }
            Modes.CameraMode.CONTINUOUS_FRAME -> {
                tvBarcodes.visibility = View.VISIBLE
                ivCaptureButton.visibility = View.GONE
                ivPlayPause.visibility = View.GONE
                ivCaptureButton.setOnClickListener(null)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateViewState()
        camera.run {
            if (!isCameraOpened
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
                start()
            }
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