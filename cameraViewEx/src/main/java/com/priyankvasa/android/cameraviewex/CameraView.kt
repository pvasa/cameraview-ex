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

import android.Manifest
import android.annotation.TargetApi
import android.app.Activity
import android.content.Context
import android.media.Image
import android.media.ImageReader
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.support.annotation.IntDef
import android.support.annotation.RequiresPermission
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
import com.priyankvasa.android.cameraviewex.Modes.CameraMode.CONTINUOUS_FRAME
import com.priyankvasa.android.cameraviewex.Modes.CameraMode.SINGLE_CAPTURE
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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean

class CameraView @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    init {
        @Suppress("ConstantConditionIf")
        if (BuildConfig.DEBUG) Timber.plant(Timber.DebugTree())
    }

    @IntDef(SINGLE_CAPTURE, /*BURST_CAPTURE,*/ CONTINUOUS_FRAME/*, VIDEO*/)
    @Retention(AnnotationRetention.RUNTIME)
    @Target(AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.PROPERTY, AnnotationTarget.PROPERTY_GETTER)
    annotation class CameraMode

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

    private val preview = createPreview(context)

    /** Listeners for monitoring events about [CameraView]. */
    private val cameraOpenedListeners = HashSet<() -> Unit>()
    private val pictureTakenListeners = HashSet<(imageData: ByteArray) -> Unit>()
    private var previewFrameListener: ((image: Image) -> Unit)? = null
    private val cameraClosedListeners = HashSet<() -> Unit>()

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

    private var camera: CameraInterface = when {
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

    /** The mode in which camera starts. Supported values [Modes.CameraMode]. */
    @get:CameraMode
    @setparam:CameraMode
    var cameraMode: Int
        get() = camera.cameraMode
        private set(value) {
            camera.cameraMode = value
        }

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

    /** Zero shutter lag mode capture. */
    var zsl: Boolean
        get() = camera.zsl
        set(value) {
            camera.zsl = value
        }

    init {
        if (isInEditMode) {
            listener.disable()
            displayOrientationDetector.disable()
        } else {
            // Attributes
            context.obtainStyledAttributes(
                    attrs,
                    R.styleable.CameraView,
                    defStyleAttr,
                    R.style.Widget_CameraView
            ).run {
                adjustViewBounds = getBoolean(R.styleable.CameraView_android_adjustViewBounds, Modes.DEFAULT_ADJUST_VIEW_BOUNDS)
                cameraMode = getInt(R.styleable.CameraView_camera_mode, Modes.DEFAULT_CAMERA_MODE)
                outputFormat = getInt(R.styleable.CameraView_outputFormat, JPEG)
                facing = getInt(R.styleable.CameraView_facing, FACING_BACK)
                aspectRatio = getString(R.styleable.CameraView_aspectRatio)
                        ?.let { AspectRatio.parse(it) }
                        ?: Modes.DEFAULT_ASPECT_RATIO
                autoFocus = getBoolean(R.styleable.CameraView_autoFocus, Modes.DEFAULT_AUTO_FOCUS)
//            touchToFocus = getBoolean(R.styleable.CameraView_touchToFocus, Modes.DEFAULT_TOUCH_TO_FOCUS)
                awb = getInt(R.styleable.CameraView_awb, Modes.DEFAULT_AWB)
                flash = getInt(R.styleable.CameraView_flash, Modes.DEFAULT_FLASH)
//            ae = getBoolean(R.styleable.CameraView_ae, Modes.DEFAULT_AUTO_EXPOSURE)
                opticalStabilization = getBoolean(R.styleable.CameraView_opticalStabilization, Modes.DEFAULT_OPTICAL_STABILIZATION)
                noiseReduction = getInt(R.styleable.CameraView_noiseReduction, Modes.DEFAULT_NOISE_REDUCTION)
                shutter = getInt(R.styleable.CameraView_shutter, Modes.DEFAULT_SHUTTER)
                zsl = getBoolean(R.styleable.CameraView_zsl, Modes.DEFAULT_ZSL)

                recycle()
            }

            // Add shutter view
            addView(preview.shutterView)
            preview.shutterView.layoutParams = preview.view.layoutParams
        }
    }

    private fun createPreview(context: Context): PreviewImpl = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.N -> SurfaceViewPreview(context, this)
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH -> TextureViewPreview(context, this)
        else -> TextureViewPreview(context, this)
    }

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
                    cameraMode,
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
                    shutter,
                    zsl
            )

    override fun onRestoreInstanceState(state: Parcelable?) {
        if (state !is SavedState) {
            super.onRestoreInstanceState(state)
            return
        }
        val ss = state as? SavedState?
        super.onRestoreInstanceState(ss?.superState)
        ss?.let {
            cameraMode = it.cameraMode
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
            zsl = it.zsl
        }
    }

    /**
     * Open a camera device and start showing camera preview. This is typically called from
     * [Activity.onResume].
     */
    @RequiresPermission(Manifest.permission.CAMERA)
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
     * @param removeAllListeners if `true`, removes all listeners previously set. See [CameraView.removeAllListeners]
     */
    fun stop(removeAllListeners: Boolean = false) {
        if (removeAllListeners) listener.clear()
        camera.stop()
    }

    /**
     * Add a new camera opened [listener].
     * @param listener lambda
     * @return instance of [CameraView] it is called on
     */
    fun addCameraOpenedListener(listener: () -> Unit): CameraView {
        if (this.listener.isEnabled) cameraOpenedListeners.add(listener)
        return this
    }

    /**
     * Remove camera opened [listener].
     * @param listener that was previously added.
     * @return instance of [CameraView] it is called on
     */
    fun removeCameraOpenedListener(listener: () -> Unit): CameraView {
        cameraOpenedListeners.remove(listener)
        return this
    }

    /**
     * Set preview frame [listener]. Be careful while using this listener as it is invoked on each frame,
     * which could be 60 times per second if frame rate is 60 fps.
     * Ideally you should only process next frame once you are done processing previous frame.
     * Don't continuously launch background tasks for each frame,
     * it is not memory efficient, the device will run out of memory very quickly and force close the app.
     *
     * @param listener lambda with image of type [Image] as its argument which is the preview frame.
     *        It is always of type [android.graphics.ImageFormat.YUV_420_888]
     * @return instance of [CameraView] it is called on
     * @sample setupCameraSample
     */
    fun setPreviewFrameListener(listener: (image: Image) -> Unit): CameraView {
        if (this.listener.isEnabled) previewFrameListener = listener
        return this
    }

    /**
     * This is a sample setup method to show appropriate and safe usage of [setPreviewFrameListener]
     */
    @ExperimentalCoroutinesApi
    @Suppress("unused", "UNUSED_ANONYMOUS_PARAMETER", "UNUSED_VARIABLE")
    private fun setupCameraSample() {

        CameraView(context).apply {

            val processing = AtomicBoolean(false)

            addCameraOpenedListener { Timber.i("Camera opened.") }

            setPreviewFrameListener { image: Image ->

                if (!processing.get()) {

                    processing.set(true)

                    val result = GlobalScope.async { /* Some background image processing task */ }

                    result.invokeOnCompletion { t ->
                        val output = result.getCompleted()
                        /* ...  use the output ... */
                        // Set processing flag to false
                        processing.set(false)
                    }
                }
            }

            addPictureTakenListener { imageData: ByteArray -> Timber.i("Picture taken successfully.") }

            addCameraClosedListener { Timber.i("Camera closed.") }
        }
    }

    /**
     * Remove preview frame [listener].
     * @return instance of [CameraView] it is called on
     */
    fun removePreviewFrameListener(): CameraView {
        previewFrameListener = null
        return this
    }

    /**
     * Add a new picture taken [listener].
     * @param listener lambda
     * @return instance of [CameraView] it is called on
     */
    fun addPictureTakenListener(listener: (imageData: ByteArray) -> Unit): CameraView {
        if (this.listener.isEnabled) pictureTakenListeners.add(listener)
        return this
    }

    /**
     * Remove picture taken [listener].
     * @return instance of [CameraView] it is called on
     */
    fun removePictureTakenListener(listener: (imageData: ByteArray) -> Unit): CameraView {
        pictureTakenListeners.remove(listener)
        return this
    }

    /**
     * Add a new camera closed [listener].
     * @param listener lambda
     * @return instance of [CameraView] it is called on
     */
    fun addCameraClosedListener(listener: () -> Unit): CameraView {
        if (this.listener.isEnabled) cameraClosedListeners.add(listener)
        return this
    }

    /**
     * Remove camera closed [listener].
     * @param listener that was previously added.
     * @return instance of [CameraView] it is called on
     */
    fun removeCameraClosedListener(listener: () -> Unit): CameraView {
        cameraClosedListeners.remove(listener)
        return this
    }

    /** Remove all listeners previously set. */
    fun removeAllListeners() {
        listener.clear()
    }

    /** Take a picture. The result will be returned to listeners added by [addPictureTakenListener]. */
    fun capture() {
        if (cameraMode == Modes.CameraMode.SINGLE_CAPTURE) camera.takePicture()
        else Timber.e("Cannot capture still picture in camera mode $cameraMode")
    }

    @Parcelize
    data class SavedState(
            val parcelable: Parcelable,
            @CameraMode val cameraMode: Int,
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
            @Shutter val shutter: Int,
            val zsl: Boolean
    ) : View.BaseSavedState(parcelable), Parcelable
}