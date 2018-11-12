/*
 * Copyright 2018 Priyank Vasa
 *
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

package com.priyankvasa.android.cameraviewex

import android.annotation.TargetApi
import android.app.Activity
import android.content.Context
import android.media.Image
import android.media.ImageReader
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.support.annotation.IntDef
import android.support.v4.view.ViewCompat
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import com.priyankvasa.android.cameraviewex.Modes.AutoWhiteBalance.AWB_AUTO
import com.priyankvasa.android.cameraviewex.Modes.AutoWhiteBalance.AWB_CLOUDY_DAYLIGHT
import com.priyankvasa.android.cameraviewex.Modes.AutoWhiteBalance.AWB_DAYLIGHT
import com.priyankvasa.android.cameraviewex.Modes.AutoWhiteBalance.AWB_FLUORESCENT
import com.priyankvasa.android.cameraviewex.Modes.AutoWhiteBalance.AWB_INCANDESCENT
import com.priyankvasa.android.cameraviewex.Modes.AutoWhiteBalance.AWB_OFF
import com.priyankvasa.android.cameraviewex.Modes.AutoWhiteBalance.AWB_SHADE
import com.priyankvasa.android.cameraviewex.Modes.AutoWhiteBalance.AWB_TWILIGHT
import com.priyankvasa.android.cameraviewex.Modes.AutoWhiteBalance.AWB_WARM_FLUORESCENT
import com.priyankvasa.android.cameraviewex.Modes.FACING_BACK
import com.priyankvasa.android.cameraviewex.Modes.Flash.FLASH_AUTO
import com.priyankvasa.android.cameraviewex.Modes.Flash.FLASH_OFF
import com.priyankvasa.android.cameraviewex.Modes.Flash.FLASH_ON
import com.priyankvasa.android.cameraviewex.Modes.Flash.FLASH_RED_EYE
import com.priyankvasa.android.cameraviewex.Modes.Flash.FLASH_TORCH
import com.priyankvasa.android.cameraviewex.Modes.NoiseReduction.NOISE_REDUCTION_FAST
import com.priyankvasa.android.cameraviewex.Modes.NoiseReduction.NOISE_REDUCTION_HIGH_QUALITY
import com.priyankvasa.android.cameraviewex.Modes.NoiseReduction.NOISE_REDUCTION_MINIMAL
import com.priyankvasa.android.cameraviewex.Modes.NoiseReduction.NOISE_REDUCTION_OFF
import com.priyankvasa.android.cameraviewex.Modes.NoiseReduction.NOISE_REDUCTION_ZERO_SHUTTER_LAG
import com.priyankvasa.android.cameraviewex.Modes.OutputFormat.JPEG
import com.priyankvasa.android.cameraviewex.Modes.OutputFormat.RGBA_8888
import com.priyankvasa.android.cameraviewex.Modes.OutputFormat.YUV_420_888
import com.priyankvasa.android.cameraviewex.Modes.Shutter.SHUTTER_LONG
import com.priyankvasa.android.cameraviewex.Modes.Shutter.SHUTTER_OFF
import com.priyankvasa.android.cameraviewex.Modes.Shutter.SHUTTER_SHORT
import kotlinx.android.parcel.Parcelize

class CameraView @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    /** Direction the camera faces relative to device screen. */
    @IntDef(JPEG, YUV_420_888, RGBA_8888)
    @Retention(AnnotationRetention.RUNTIME)
    @Target(AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.PROPERTY, AnnotationTarget.PROPERTY_GETTER)
    annotation class OutputFormat

    /** Direction the camera faces relative to device screen. */
    @IntDef(Modes.FACING_BACK, Modes.FACING_FRONT)
    @Retention(AnnotationRetention.RUNTIME)
    @Target(AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.PROPERTY, AnnotationTarget.PROPERTY_GETTER)
    annotation class Facing

    /** The mode for the camera device's flash control */
    @IntDef(FLASH_OFF, FLASH_ON, FLASH_TORCH, FLASH_AUTO, FLASH_RED_EYE)
    @Retention(AnnotationRetention.RUNTIME)
    @Target(AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.PROPERTY, AnnotationTarget.PROPERTY_GETTER)
    annotation class Flash

    /** The mode for the camera device's noise reduction control */
    @IntDef(NOISE_REDUCTION_OFF,
            NOISE_REDUCTION_FAST,
            NOISE_REDUCTION_HIGH_QUALITY,
            NOISE_REDUCTION_MINIMAL,
            NOISE_REDUCTION_ZERO_SHUTTER_LAG)
    @Retention(AnnotationRetention.RUNTIME)
    @Target(AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.PROPERTY, AnnotationTarget.PROPERTY_GETTER)
    annotation class NoiseReduction

    /** The mode for the camera device's auto white balance control */
    @IntDef(AWB_OFF,
            AWB_AUTO,
            AWB_INCANDESCENT,
            AWB_FLUORESCENT,
            AWB_WARM_FLUORESCENT,
            AWB_DAYLIGHT,
            AWB_CLOUDY_DAYLIGHT,
            AWB_TWILIGHT,
            AWB_SHADE)
    @Retention(AnnotationRetention.RUNTIME)
    @Target(AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.PROPERTY, AnnotationTarget.PROPERTY_GETTER)
    annotation class Awb

    /** Shutter time in milliseconds */
    @IntDef(SHUTTER_OFF,
            SHUTTER_SHORT,
            SHUTTER_LONG)
    @Retention(AnnotationRetention.RUNTIME)
    @Target(AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.PROPERTY, AnnotationTarget.PROPERTY_GETTER)
    annotation class Shutter

    private val preview = createPreviewImpl(context)

    private val listener = object : CameraInterface.Listener {

        private var requestLayoutOnOpen: Boolean = false

        var isEnabled: Boolean = true
            private set

        fun reserveRequestLayoutOnOpen() {
            requestLayoutOnOpen = true
        }

        fun disable() {
            isEnabled = false
            clear()
        }

        fun clear() {
            cameraOpenedListeners.clear()
            previewFrameListener = null
            pictureTakenListeners.clear()
            cameraClosedListeners.clear()
        }

        override fun onCameraOpened() {
            if (requestLayoutOnOpen) {
                requestLayoutOnOpen = false
                requestLayout()
            }
            cameraOpenedListeners.forEach { it() }
        }

        @TargetApi(Build.VERSION_CODES.KITKAT)
        override fun onPreviewFrame(reader: ImageReader) {
            previewFrameListener?.run { reader.acquireNextImage().use { invoke(it) } }
        }

        override fun onPictureTaken(imageData: ByteArray) {
            pictureTakenListeners.forEach { it(imageData) }
        }

        override fun onCameraClosed() {
            cameraClosedListeners.forEach { it() }
        }
    }

    internal var camera: CameraInterface = when {
        Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP -> Camera1(listener, preview)
        Build.VERSION.SDK_INT < Build.VERSION_CODES.M -> Camera2(listener, preview, context)
        else -> Camera2Api23(listener, preview, context)
    }

    /** Display orientation detector */
    private val displayOrientationDetector: DisplayOrientationDetector =
            object : DisplayOrientationDetector(context) {
                override fun onDisplayOrientationChanged(displayOrientation: Int) {
                    camera.displayOrientation = displayOrientation
                }
            }

    /** `true` if the camera is opened `false` otherwise. */
    val isCameraOpened: Boolean get() = camera.isCameraOpened

    /**
     * True when this CameraView is adjusting its bounds to preserve the aspect ratio of
     * camera.
     */
    var adjustViewBounds: Boolean = false
        set(value) {
            if (value == field) return
            field = value
            requestLayout()
        }

    /** Format of the output of image data produced from the camera. Supported values [Modes.OutputFormat]. */
    @get:OutputFormat
    @setparam:OutputFormat
    var outputFormat: Int
        get() = camera.outputFormat
        private set(value) {
            camera.outputFormat = value
        }

    /**
     * Direction that the current camera faces.
     * Supported values are [Modes.FACING_BACK] and [Modes.FACING_FRONT].
     */
    @get:Facing
    @setparam:Facing
    var facing: Int
        get() = camera.facing
        set(value) {
            camera.facing = value
        }

    /** Gets all the aspect ratios supported by the current camera. */
    val supportedAspectRatios: Set<AspectRatio> get() = camera.supportedAspectRatios

    /** Current aspect ratio of camera. Valid format is "height:width" eg. "4:3". */
    var aspectRatio: AspectRatio
        get() = camera.aspectRatio
        set(value) {
            if (camera.setAspectRatio(value)) requestLayout()
        }

    /**
     * `true` if the continuous auto-focus mode is enabled. `false` if it is
     * disabled, or if it is not supported by the current camera.
     */
    var autoFocus: Boolean
        get() = camera.autoFocus
        set(value) {
            camera.autoFocus = value
        }

    /** Current touch to focus mode. True is on and false if off. */
    private var touchToFocus: Boolean
        get() = camera.touchToFocus
        set(value) {
            camera.touchToFocus = value
        }

    /** Current auto white balance mode. Supported values [Modes.AutoWhiteBalance]. */
    @get:Awb
    @setparam:Awb
    var awb: Int
        get() = camera.awb
        set(value) {
            camera.awb = value
        }

    /** Current flash mode. Supported values [Modes.Flash]. */
    @get:Flash
    @setparam:Flash
    var flash: Int
        get() = camera.flash
        set(value) {
            camera.flash = value
        }

    /** Current auto exposure mode */
    private var ae: Boolean
        get() = camera.ae
        set(value) {
            camera.ae = value
        }

    /** Current optical stabilization mode */
    var opticalStabilization: Boolean
        get() = camera.opticalStabilization
        set(value) {
            camera.opticalStabilization = value
        }

    /** Current noise reduction mode. Supported values [Modes.NoiseReduction]. */
    @get:NoiseReduction
    @setparam:NoiseReduction
    var noiseReduction: Int
        get() = camera.noiseReduction
        set(value) {
            camera.noiseReduction = value
        }

    /** Current shutter time in milliseconds. Supported values [Modes.Shutter]. */
    @get:Shutter
    @setparam:Shutter
    var shutter: Int
        get() = preview.shutterView.shutterTime
        set(value) {
            preview.shutterView.shutterTime = value
        }

    init {
        if (isInEditMode) {
            listener.disable()
            displayOrientationDetector.disable()
        } else {
            // Attributes
            val attr = context.obtainStyledAttributes(
                    attrs,
                    R.styleable.CameraView,
                    defStyleAttr,
                    R.style.Widget_CameraView
            )

            adjustViewBounds = attr.getBoolean(R.styleable.CameraView_android_adjustViewBounds, Modes.DEFAULT_ADJUST_VIEW_BOUNDS)
            outputFormat = attr.getInt(R.styleable.CameraView_outputFormat, JPEG)
            facing = attr.getInt(R.styleable.CameraView_facing, FACING_BACK)
            aspectRatio = attr.getString(R.styleable.CameraView_aspectRatio)
                    ?.let { AspectRatio.parse(it) }
                    ?: Modes.DEFAULT_ASPECT_RATIO
            autoFocus = attr.getBoolean(R.styleable.CameraView_autoFocus, Modes.DEFAULT_AUTO_FOCUS)
//            touchToFocus = attr.getBoolean(R.styleable.CameraView_touchToFocus, Modes.DEFAULT_TOUCH_TO_FOCUS)
            awb = attr.getInt(R.styleable.CameraView_awb, Modes.DEFAULT_AWB)
            flash = attr.getInt(R.styleable.CameraView_flash, Modes.DEFAULT_FLASH)
//            ae = attr.getBoolean(R.styleable.CameraView_ae, Modes.DEFAULT_AUTO_EXPOSURE)
            opticalStabilization = attr.getBoolean(R.styleable.CameraView_opticalStabilization, Modes.DEFAULT_OPTICAL_STABILIZATION)
            noiseReduction = attr.getInt(R.styleable.CameraView_noiseReduction, Modes.DEFAULT_NOISE_REDUCTION)
            shutter = attr.getInt(R.styleable.CameraView_shutter, Modes.DEFAULT_SHUTTER)

            attr.recycle()

            // Add shutter view
            addView(preview.shutterView)
            preview.shutterView.layoutParams = preview.view.layoutParams
        }
    }

    private fun createPreviewImpl(context: Context): PreviewImpl = TextureViewPreview(context, this)

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!isInEditMode) ViewCompat.getDisplay(this)?.let { displayOrientationDetector.enable(it) }
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
                listener.reserveRequestLayoutOnOpen()
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
        if (height < width * ratio.y / ratio.x) camera.view.measure(
                View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(
                        width * ratio.y / ratio.x,
                        View.MeasureSpec.EXACTLY
                )
        ) else camera.view.measure(
                View.MeasureSpec.makeMeasureSpec(
                        height * ratio.x / ratio.y,
                        View.MeasureSpec.EXACTLY
                ),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
        )

        preview.shutterView.layoutParams = preview.view.layoutParams
    }

    override fun onSaveInstanceState(): Parcelable? =
            SavedState(
                    super.onSaveInstanceState() ?: Bundle(),
                    outputFormat,
                    facing,
                    aspectRatio,
                    autoFocus,
                    touchToFocus,
                    awb,
                    flash,
                    ae,
                    opticalStabilization,
                    noiseReduction,
                    shutter
            )

    override fun onRestoreInstanceState(state: Parcelable?) {
        if (state !is SavedState) {
            super.onRestoreInstanceState(state)
            return
        }
        val ss = state as? SavedState?
        super.onRestoreInstanceState(ss?.superState)
        ss?.let {
            outputFormat = it.outputFormat
            facing = it.facing
            aspectRatio = it.ratio
            autoFocus = it.autoFocus
            awb = it.awb
            flash = it.flash
            ae = it.ae
            opticalStabilization = it.opticalStabilization
            noiseReduction = it.noiseReduction
            shutter = it.shutter
        }
    }

    /**
     * Open a camera device and start showing camera preview. This is typically called from
     * [Activity.onResume].
     */
    fun start() {
        if (!camera.start()) {
            //store the state ,and restore this state after fall back o Camera1
            val state = onSaveInstanceState()
            // Camera2 uses legacy hardware layer; fall back to Camera1
            camera = Camera1(listener, preview)
            onRestoreInstanceState(state)
            camera.start()
        }
    }

    /**
     * Stop camera preview and close the device. This is typically called from
     * [Activity.onPause].
     */
    fun stop(removeAllListeners: Boolean = false) {
        if (removeAllListeners) listener.clear()
        camera.stop()
    }

    /**
     * Called when camera is opened.
     */
    fun addCameraOpenedListener(listener: () -> Unit): CameraView {
        if (this.listener.isEnabled) cameraOpenedListeners.add(listener)
        return this
    }

    fun removeCameraOpenedListener(listener: () -> Unit): CameraView {
        cameraOpenedListeners.remove(listener)
        return this
    }

    fun setPreviewFrameListener(listener: (image: Image) -> Unit): CameraView {
        if (this.listener.isEnabled) previewFrameListener = listener
        return this
    }

    fun removePreviewFrameListener(): CameraView {
        previewFrameListener = null
        return this
    }

    /**
     * Called when a picture is taken.
     *
     * @param imageData Image data.
     */
    fun addPictureTakenListener(listener: (imageData: ByteArray) -> Unit): CameraView {
        if (this.listener.isEnabled) pictureTakenListeners.add(listener)
        return this
    }

    fun removePictureTakenListener(listener: (imageData: ByteArray) -> Unit): CameraView {
        pictureTakenListeners.remove(listener)
        return this
    }

    /**
     * Called when camera is closed.
     */
    fun addCameraClosedListener(listener: () -> Unit): CameraView {
        if (this.listener.isEnabled) cameraClosedListeners.add(listener)
        return this
    }

    fun removeCameraClosedListener(listener: () -> Unit): CameraView {
        cameraClosedListeners.remove(listener)
        return this
    }

    fun removeAllListeners() {
        listener.clear()
    }

    /** Take a picture. The result will be returned to listeners added by [addPictureTakenListener]. */
    fun capture() {
        camera.takePicture()
    }

    private companion object {
        /** Listeners for monitoring events about [CameraView]. */
        val cameraOpenedListeners = HashSet<() -> Unit>()
        val pictureTakenListeners = HashSet<(imageData: ByteArray) -> Unit>()
        var previewFrameListener: ((image: Image) -> Unit)? = null
        val cameraClosedListeners = HashSet<() -> Unit>()
    }

    @Parcelize
    data class SavedState(
            val parcelable: Parcelable,
            @OutputFormat val outputFormat: Int,
            @Facing val facing: Int,
            val ratio: AspectRatio,
            val autoFocus: Boolean,
            val touchToFocus: Boolean,
            @Awb val awb: Int,
            @Flash val flash: Int,
            val ae: Boolean,
            val opticalStabilization: Boolean,
            @NoiseReduction val noiseReduction: Int,
            @Shutter val shutter: Int
    ) : View.BaseSavedState(parcelable), Parcelable
}