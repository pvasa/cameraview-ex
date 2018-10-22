/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.cameraview

import android.app.Activity
import android.content.Context
import android.os.Build
import android.os.Parcelable
import android.support.annotation.IntDef
import android.support.v4.view.ViewCompat
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import com.google.android.cameraview.Modes.FACING_BACK
import com.google.android.cameraview.Modes.Flash.FLASH_AUTO
import com.google.android.cameraview.Modes.Flash.FLASH_OFF
import com.google.android.cameraview.Modes.Flash.FLASH_ON
import com.google.android.cameraview.Modes.Flash.FLASH_RED_EYE
import com.google.android.cameraview.Modes.Flash.FLASH_TORCH
import com.google.android.cameraview.Modes.NoiseReduction.NOISE_REDUCTION_FAST
import com.google.android.cameraview.Modes.NoiseReduction.NOISE_REDUCTION_HIGH_QUALITY
import com.google.android.cameraview.Modes.NoiseReduction.NOISE_REDUCTION_MINIMAL
import com.google.android.cameraview.Modes.NoiseReduction.NOISE_REDUCTION_OFF
import com.google.android.cameraview.Modes.NoiseReduction.NOISE_REDUCTION_ZERO_SHUTTER_LAG
import kotlinx.android.parcel.Parcelize
import java.util.ArrayList

class CameraView @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    /** Direction the camera faces relative to device screen. */
    @IntDef(Modes.FACING_BACK, Modes.FACING_FRONT)
    @Retention(AnnotationRetention.SOURCE)
    annotation class Facing

    /** The mode for for the camera device's flash control */
    @IntDef(FLASH_OFF, FLASH_ON, FLASH_TORCH, FLASH_AUTO, FLASH_RED_EYE)
    annotation class Flash

    /** The mode for for the camera device's flash control */
    @IntDef(NOISE_REDUCTION_OFF,
            NOISE_REDUCTION_FAST,
            NOISE_REDUCTION_HIGH_QUALITY,
            NOISE_REDUCTION_MINIMAL,
            NOISE_REDUCTION_ZERO_SHUTTER_LAG)
    annotation class NoiseReduction

    private val preview = createPreviewImpl(context)

    private val callbacks: CallbackBridge = CallbackBridge()

    internal var cameraViewImpl: CameraViewImpl = when {
        Build.VERSION.SDK_INT < 21 -> Camera1(callbacks, preview)
        Build.VERSION.SDK_INT < 23 -> Camera2(callbacks, preview, context)
        else -> Camera2Api23(callbacks, preview, context)
    }

    /** Display orientation detector */
    private val displayOrientationDetector: DisplayOrientationDetector =
            object : DisplayOrientationDetector(context) {
                override fun onDisplayOrientationChanged(displayOrientation: Int) {
                    cameraViewImpl.displayOrientation = displayOrientation
                }
            }

    /** `true` if the camera is opened. */
    val isCameraOpened: Boolean get() = cameraViewImpl.isCameraOpened

    /**
     * True when this CameraView is adjusting its bounds to preserve the aspect ratio of
     * camera.
     */
    var adjustViewBounds: Boolean = false
        set(adjustViewBounds) {
            if (field != adjustViewBounds) {
                field = adjustViewBounds
                requestLayout()
            }
        }

    /** Direction that the current camera faces. */
    var facing: Int
        @Facing
        get() = cameraViewImpl.facing
        set(@Facing facing) {
            cameraViewImpl.facing = facing
        }

    /** Gets all the aspect ratios supported by the current camera. */
    val supportedAspectRatios: Set<AspectRatio> get() = cameraViewImpl.supportedAspectRatios

    /** Current aspect ratio of camera. */
    var aspectRatio: AspectRatio
        get() = cameraViewImpl.aspectRatio
        set(ratio) {
            if (cameraViewImpl.setAspectRatio(ratio)) {
                requestLayout()
            }
        }

    /**
     * `true` if the continuous auto-focus mode is enabled. `false` if it is
     * disabled, or if it is not supported by the current camera.
     */
    var autoFocus: Boolean
        get() = cameraViewImpl.autoFocus
        set(autoFocus) {
            cameraViewImpl.autoFocus = autoFocus
        }

    var touchToFocus: Boolean
        get() = cameraViewImpl.touchToFocus
        set(touchToFocus) {
            cameraViewImpl.touchToFocus = touchToFocus
        }

    var awb: Boolean
        get() = cameraViewImpl.awb
        set(awb) {
            cameraViewImpl.awb = awb
        }

    /** Current flash mode */
    var flash: Int
        @Flash
        get() = cameraViewImpl.flash
        set(@Flash flash) {
            cameraViewImpl.flash = flash
        }

    var ae: Boolean
        get() = cameraViewImpl.ae
        set(ae) {
            cameraViewImpl.ae = ae
        }

    var opticalStabilization: Boolean
        get() = cameraViewImpl.opticalStabilization
        set(opticalStabilization) {
            cameraViewImpl.opticalStabilization = opticalStabilization
        }

    var noiseReduction: Int
        @NoiseReduction
        get() = cameraViewImpl.noiseReduction
        set(@NoiseReduction noiseReduction) {
            cameraViewImpl.noiseReduction = noiseReduction
        }

    init {
        if (isInEditMode) {
            callbacks.disable()
            displayOrientationDetector.disable()
        } else {
            // Attributes
            val attr = context.obtainStyledAttributes(
                    attrs,
                    R.styleable.CameraView,
                    defStyleAttr,
                    R.style.Widget_CameraView
            )

            adjustViewBounds = attr.getBoolean(R.styleable.CameraView_android_adjustViewBounds, false)
            facing = attr.getInt(R.styleable.CameraView_facing, FACING_BACK)
            aspectRatio = attr.getString(R.styleable.CameraView_aspectRatio)
                    ?.let { AspectRatio.parse(it) }
                    ?: Modes.DEFAULT_ASPECT_RATIO
            autoFocus = attr.getBoolean(R.styleable.CameraView_autoFocus, false)
            touchToFocus = attr.getBoolean(R.styleable.CameraView_touchToFocus, false)
            awb = attr.getBoolean(R.styleable.CameraView_awb, false)
            flash = attr.getInt(R.styleable.CameraView_flash, FLASH_OFF)
            ae = attr.getBoolean(R.styleable.CameraView_ae, false)
            opticalStabilization = attr.getBoolean(R.styleable.CameraView_opticalStabilization, false)
            noiseReduction = attr.getInt(R.styleable.CameraView_noiseReduction, NOISE_REDUCTION_OFF)

            attr.recycle()
        }
    }

    private fun createPreviewImpl(context: Context): PreviewImpl = TextureViewPreview(context, this)

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!isInEditMode) displayOrientationDetector.enable(ViewCompat.getDisplay(this))
    }

    override fun onDetachedFromWindow() {
        if (!isInEditMode) displayOrientationDetector.disable()
        super.onDetachedFromWindow()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        if (isInEditMode) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            return
        }
        // Handle android:adjustViewBounds
        if (adjustViewBounds) {
            if (!isCameraOpened) {
                callbacks.reserveRequestLayoutOnOpen()
                super.onMeasure(widthMeasureSpec, heightMeasureSpec)
                return
            }
            val widthMode = View.MeasureSpec.getMode(widthMeasureSpec)
            val heightMode = View.MeasureSpec.getMode(heightMeasureSpec)
            if (widthMode == View.MeasureSpec.EXACTLY && heightMode != View.MeasureSpec.EXACTLY) {
                val ratio = aspectRatio
                var height = (View.MeasureSpec.getSize(widthMeasureSpec) * ratio.toFloat()).toInt()
                if (heightMode == View.MeasureSpec.AT_MOST) {
                    height = Math.min(height, View.MeasureSpec.getSize(heightMeasureSpec))
                }
                super.onMeasure(widthMeasureSpec,
                        View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
            } else if (widthMode != View.MeasureSpec.EXACTLY && heightMode == View.MeasureSpec.EXACTLY) {
                val ratio = aspectRatio
                var width = (View.MeasureSpec.getSize(heightMeasureSpec) * ratio.toFloat()).toInt()
                if (widthMode == View.MeasureSpec.AT_MOST) {
                    width = Math.min(width, View.MeasureSpec.getSize(widthMeasureSpec))
                }
                super.onMeasure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                        heightMeasureSpec)
            } else {
                super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            }
        } else {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        }
        // Measure the TextureView
        val width = measuredWidth
        val height = measuredHeight
        var ratio = aspectRatio
        if (displayOrientationDetector.lastKnownDisplayOrientation % 180 == 0) {
            ratio = ratio.inverse()
        }
        if (height < width * ratio.y / ratio.x) {
            cameraViewImpl.view.measure(
                    View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(width * ratio.y / ratio.x,
                            View.MeasureSpec.EXACTLY))
        } else {
            cameraViewImpl.view.measure(
                    View.MeasureSpec.makeMeasureSpec(height * ratio.x / ratio.y,
                            View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
        }
    }

    override fun onSaveInstanceState(): Parcelable? =
            SavedState(
                    super.onSaveInstanceState(),
                    facing,
                    aspectRatio,
                    autoFocus,
                    touchToFocus,
                    awb,
                    flash,
                    ae,
                    opticalStabilization,
                    noiseReduction
            )

    override fun onRestoreInstanceState(state: Parcelable?) {
        if (state !is SavedState) {
            super.onRestoreInstanceState(state)
            return
        }
        val ss = state as SavedState?
        super.onRestoreInstanceState(ss?.superState)
        ss?.let {
            facing = it.facing
            aspectRatio = it.ratio
            autoFocus = it.autoFocus
            awb = it.awb
            flash = it.flash
            ae = it.ae
            opticalStabilization = it.opticalStabilization
            noiseReduction = it.noiseReduction
        }
    }

    /**
     * Open a camera device and start showing camera preview. This is typically called from
     * [Activity.onResume].
     */
    fun start() {
        if (!cameraViewImpl.start()) {
            //store the state ,and restore this state after fall back o Camera1
            val state = onSaveInstanceState()
            // Camera2 uses legacy hardware layer; fall back to Camera1
            cameraViewImpl = Camera1(callbacks, createPreviewImpl(context))
            onRestoreInstanceState(state)
            cameraViewImpl.start()
        }
    }

    /**
     * Stop camera preview and close the device. This is typically called from
     * [Activity.onPause].
     */
    fun stop() {
        cameraViewImpl.stop()
    }

    /**
     * Add a new callback.
     *
     * @param callback The [Callback] to add.
     * @see .removeCallback
     */
    fun addCallback(callback: Callback) {
        callbacks.add(callback)
    }

    /**
     * Remove a callback.
     *
     * @param callback The [Callback] to remove.
     * @see .addCallback
     */
    fun removeCallback(callback: Callback) {
        callbacks.remove(callback)
    }

    /**
     * Take a picture. The result will be returned to
     * [Callback.onPictureTaken].
     */
    fun capture() {
        cameraViewImpl.takePicture()
    }

    private inner class CallbackBridge internal constructor() : CameraViewImpl.Callback {

        private val callbacks = ArrayList<Callback>()

        private var enabled = true

        private var requestLayoutOnOpen: Boolean = false

        fun add(callback: Callback) {
            if (enabled) callbacks.add(callback)
        }

        fun remove(callback: Callback) {
            callbacks.remove(callback)
        }

        fun disable() {
            callbacks.clear()
            enabled = false
        }

        override fun onCameraOpened() {
            if (requestLayoutOnOpen) {
                requestLayoutOnOpen = false
                requestLayout()
            }
            callbacks.forEach { it.onCameraOpened(this@CameraView) }
        }

        override fun onCameraClosed() {
            callbacks.forEach { it.onCameraClosed(this@CameraView) }
        }

        override fun onPictureTaken(data: ByteArray) {
            callbacks.forEach { it.onPictureTaken(this@CameraView, data) }
        }

        fun reserveRequestLayoutOnOpen() {
            requestLayoutOnOpen = true
        }
    }

    @Parcelize
    data class SavedState(
            val parcelable: Parcelable,
            @Facing val facing: Int,
            val ratio: AspectRatio,
            val autoFocus: Boolean,
            val touchToFocus: Boolean,
            val awb: Boolean,
            @Flash val flash: Int,
            val ae: Boolean,
            val opticalStabilization: Boolean,
            @NoiseReduction val noiseReduction: Int
    ) : View.BaseSavedState(parcelable), Parcelable

    /**
     * Callback for monitoring events about [CameraView].
     */
    abstract class Callback {

        /**
         * Called when camera is opened.
         *
         * @param cameraView The associated [CameraView].
         */
        open fun onCameraOpened(cameraView: CameraView) {}

        /**
         * Called when camera is closed.
         *
         * @param cameraView The associated [CameraView].
         */
        open fun onCameraClosed(cameraView: CameraView) {}

        /**
         * Called when a picture is taken.
         *
         * @param cameraView The associated [CameraView].
         * @param data       JPEG data.
         */
        open fun onPictureTaken(cameraView: CameraView, data: ByteArray) {}
    }

    companion object
}
