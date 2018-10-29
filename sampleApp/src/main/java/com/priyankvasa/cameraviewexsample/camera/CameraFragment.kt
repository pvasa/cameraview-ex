package com.priyankvasa.cameraviewexsample.camera

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
import com.priyankvasa.android.cameraviewex.CameraView
import com.priyankvasa.android.cameraviewex.Modes
import com.priyankvasa.cameraviewexsample.OnSwipeListener
import com.priyankvasa.cameraviewexsample.R
import kotlinx.android.synthetic.main.fragment_camera.camera
import kotlinx.android.synthetic.main.fragment_camera.ivCaptureButton
import kotlinx.android.synthetic.main.fragment_camera.ivFlashSwitch
import kotlinx.android.synthetic.main.fragment_camera.ivPhoto

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

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.fragment_camera, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        orientationEventListener.run { if (canDetectOrientation()) enable() }

        setupCamera()

        ivCaptureButton.setOnClickListener { camera.capture() }

        ivFlashSwitch.setOnClickListener { _ ->

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

        camera.addCallback(object : CameraView.Callback() {

            override fun onPictureTaken(cameraView: CameraView, data: ByteArray) {
                super.onPictureTaken(cameraView, data)

                ivPhoto.visibility = View.VISIBLE

                Glide.with(this@CameraFragment)
                        .asBitmap()
                        .load(data)
                        .into(ivPhoto)
            }
        })

        camera.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            return@setOnTouchListener true
        }
    }

    override fun onDestroyView() {
        orientationEventListener.disable()
        super.onDestroyView()
    }

    override fun onResume() {
        super.onResume()
        camera.run { if (!isCameraOpened) start() }
    }

    override fun onPause() {
        camera.run { if (isCameraOpened) stop() }
        super.onPause()
    }
}
