package com.priyankvasa.android.cameraviewex.camera

import android.media.Image
import android.os.Bundle
import android.support.annotation.DrawableRes
import android.support.v4.app.ActivityCompat
import android.support.v4.app.Fragment
import android.support.v4.view.GestureDetectorCompat
import android.view.LayoutInflater
import android.view.OrientationEventListener
import android.view.View
import android.view.ViewGroup
import com.bumptech.glide.Glide
import com.google.firebase.ml.vision.FirebaseVision
import com.google.firebase.ml.vision.barcode.FirebaseVisionBarcode
import com.google.firebase.ml.vision.barcode.FirebaseVisionBarcodeDetectorOptions
import com.google.firebase.ml.vision.common.FirebaseVisionImage
import com.priyankvasa.android.cameraviewex.Modes
import com.priyankvasa.android.cameraviewex.OnSwipeListener
import com.priyankvasa.android.cameraviewex.R
import kotlinx.android.synthetic.main.fragment_camera.camera
import kotlinx.android.synthetic.main.fragment_camera.ivCaptureButton
import kotlinx.android.synthetic.main.fragment_camera.ivFlashSwitch
import kotlinx.android.synthetic.main.fragment_camera.ivPhoto
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean

class CameraFragment : Fragment() {

    private val orientationEventListener: OrientationEventListener by lazy {
        object : OrientationEventListener(context) {
            override fun onOrientationChanged(orientation: Int) {
                ivFlashSwitch.rotation = -orientation.toFloat()
            }
        }
    }

    private val gestureDetector: GestureDetectorCompat by lazy {
        GestureDetectorCompat(context, object : OnSwipeListener() {

            override fun onSwipe(direction: Direction): Boolean {
                camera.facing = when (direction) {
                    Direction.Down,
                    Direction.Up ->
                        if (camera.facing == Modes.FACING_BACK) Modes.FACING_FRONT else Modes.FACING_BACK
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

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.fragment_camera, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        orientationEventListener.run { if (canDetectOrientation()) enable() }

        setupCamera()

        ivCaptureButton.setOnClickListener {
            camera.capture()
        }

        ivFlashSwitch.setOnClickListener {

            camera.flash = when (camera.flash) {
                Modes.Flash.FLASH_OFF -> Modes.Flash.FLASH_AUTO
                Modes.Flash.FLASH_AUTO -> Modes.Flash.FLASH_ON
                Modes.Flash.FLASH_ON -> Modes.Flash.FLASH_OFF
                else -> return@setOnClickListener
            }

            @DrawableRes val flashDrawableId: Int = when (camera.flash) {
                Modes.Flash.FLASH_OFF -> R.drawable.ic_flash_off
                Modes.Flash.FLASH_AUTO -> R.drawable.ic_flash_auto
                Modes.Flash.FLASH_ON -> R.drawable.ic_flash_on
                else -> return@setOnClickListener
            }
            context?.let { ivFlashSwitch.setImageDrawable(ActivityCompat.getDrawable(it, flashDrawableId)) }
        }

        ivPhoto.setOnClickListener { ivPhoto.visibility = View.GONE }
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
                                barcodes.forEachIndexed { i, barcode -> Timber.i("Barcode $i: ${barcode.rawValue}") }
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
        camera.run { if (!isCameraOpened) start() }
    }

    override fun onPause() {
        camera.run { if (isCameraOpened) stop(removeAllListeners = true) }
        super.onPause()
    }

    override fun onStop() {
        camera.run { if (isCameraOpened) stop(removeAllListeners = true) }
        super.onStop()
    }

    override fun onDestroyView() {
        camera.run { if (isCameraOpened) stop(removeAllListeners = true) }
        orientationEventListener.disable()
        super.onDestroyView()
    }
}