package com.priyankvasa.android.cameraviewexSample.camera

import android.Manifest
import android.content.pm.PackageManager
import android.media.Image
import android.os.Bundle
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
import java.util.concurrent.atomic.AtomicBoolean

open class CameraFragment : Fragment() {

    private val gestureDetector: GestureDetectorCompat by lazy {
        GestureDetectorCompat(context, object : OnSwipeListener() {

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
        })
    }

    private val options = FirebaseVisionBarcodeDetectorOptions.Builder()
            .setBarcodeFormats(FirebaseVisionBarcode.FORMAT_ALL_FORMATS)
            .build()

    private val detector = FirebaseVision.getInstance().getVisionBarcodeDetector(options)

    open fun cameraErrorListener(t: Throwable) {
        Timber.e(t)
    }

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.fragment_camera, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupCamera()

        ivCaptureButton.setOnClickListener { camera.capture() }

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
            tvBarcodes.visibility = View.GONE
            camera.cameraMode = Modes.CameraMode.SINGLE_CAPTURE
        }

        ivVideoMode.setOnClickListener {
            tvBarcodes.visibility = View.GONE
        }

        ivBarcodeScanner.setOnClickListener {
            tvBarcodes.visibility = View.VISIBLE
            camera.cameraMode = Modes.CameraMode.CONTINUOUS_FRAME
        }

        ivPhoto.setOnClickListener { it.visibility = View.GONE }
    }

    private fun setupCamera() {

        with(camera) {

            val decoding = AtomicBoolean(false)

            addCameraOpenedListener { Timber.i("Camera opened.") }

            setPreviewFrameListener { image: Image ->
                if (!decoding.get()) {
                    decoding.set(true)
                    val visionImage = FirebaseVisionImage.fromMediaImage(image, 0)
                    detector.detectInImage(visionImage)
                            .addOnCompleteListener { decoding.set(false) }
                            .addOnSuccessListener { barcodes ->
                                val barcodesStr = "Barcodes\n${barcodes.joinToString(
                                        "\n",
                                        transform = { it.rawValue as? CharSequence ?: "" }
                                )}"
                                Timber.i("Barcodes: $barcodesStr")
                                tvBarcodes.text = barcodesStr
                            }
                            .addOnFailureListener { e -> Timber.e(e) }
                }
            }

            addPictureTakenListener { imageData: ByteArray ->
                ivPhoto.visibility = View.VISIBLE
                Glide.with(this@CameraFragment)
                        .load(imageData)
                        .into(ivPhoto)
            }

            addCameraClosedListener { Timber.i("Camera closed.") }

            setOnTouchListener { _, event ->
                gestureDetector.onTouchEvent(event)
                return@setOnTouchListener true
            }
        }
    }

    override fun onResume() {
        super.onResume()
        camera.run {
            if (!isCameraOpened
                    && ActivityCompat.checkSelfPermission(
                            requireContext(),
                            Manifest.permission.CAMERA
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