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
import android.support.annotation.RequiresPermission
import android.support.v4.view.ViewCompat
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import com.priyankvasa.android.cameraviewex.extension.getValue
import com.priyankvasa.android.cameraviewex.extension.setValue
import kotlinx.android.parcel.Parcelize
import timber.log.Timber
import java.io.File

class CameraView @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    init {
        @Suppress("ConstantConditionIf")
        if (BuildConfig.DEBUG) Timber.plant(Timber.DebugTree())
    }

    private val preview = createPreview(context)

    /** Listeners for monitoring events about [CameraView]. */
    private val cameraOpenedListeners = HashSet<() -> Unit>()
    private val pictureTakenListeners = HashSet<(imageData: ByteArray) -> Unit>()
    private var previewFrameListener: ((image: Image) -> Unit)? = null
    private val cameraErrorListeners = HashSet<(t: Throwable) -> Unit>()
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
            cameraErrorListeners.clear()
            cameraClosedListeners.clear()
        }

        override suspend fun onCameraOpened() {
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

        override fun onCameraError(e: Exception, isCritical: Boolean) {
            if (isCritical && cameraErrorListeners.isEmpty()) throw e
            cameraErrorListeners.forEach { it(e) }
        }

        override suspend fun onCameraClosed() {
            cameraClosedListeners.forEach { it.invoke() }
        }
    }

    private var camera: CameraInterface = when {
        Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP -> Camera1(listener, preview)
        Build.VERSION.SDK_INT < Build.VERSION_CODES.M -> Camera2(listener, preview, context)
        Build.VERSION.SDK_INT < Build.VERSION_CODES.N -> Camera2Api23(listener, preview, context)
        else -> Camera2Api24(listener, preview, context)
    }

    internal val isUiTestCompatible: Boolean get() = camera is Camera2

    /** Display orientation detector */
    private val displayOrientationDetector: DisplayOrientationDetector =
            object : DisplayOrientationDetector(context) {
                override fun onDisplayOrientationChanged(displayOrientation: Int) {
                    camera.displayOrientation = displayOrientation
                }
            }

    /** `true` if the camera is opened `false` otherwise. */
    val isCameraOpened: Boolean by camera::isCameraOpened

    /** Set camera mode of operation. Supported values are [Modes.CameraMode]. */
    @get:Modes.CameraMode
    @setparam:Modes.CameraMode
    var cameraMode: Int by camera::cameraMode

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

    /**
     * Set format of the output of image data produced from the camera for [Modes.CameraMode.SINGLE_CAPTURE] mode.
     * Supported values are [Modes.OutputFormat].
     */
    @get:Modes.OutputFormat
    @setparam:Modes.OutputFormat
    var outputFormat: Int by camera::outputFormat

    /**
     * Set image quality of the output image.
     * This property is only applicable for [outputFormat] [Modes.OutputFormat.JPEG]
     * Supported values are [Modes.JpegQuality].
     */
    @get:Modes.JpegQuality
    @setparam:Modes.JpegQuality
    var jpegQuality: Int by camera::jpegQuality

    /** Set which camera to use (like front or back). Supported values are [Modes.Facing]. */
    @get:Modes.Facing
    @setparam:Modes.Facing
    var facing: Int by camera::facing

    /** Gets all the aspect ratios supported by the current camera. */
    val supportedAspectRatios: Set<AspectRatio> by camera::supportedAspectRatios

    /** Set aspect ratio of camera. Valid format is "height:width" eg. "4:3". */
    var aspectRatio: AspectRatio
        get() = camera.aspectRatio
        set(value) {
            if (camera.setAspectRatio(value)) requestLayout()
        }

    /**
     * Set `true` to enable continuous auto-focus mode and `false` to
     * disable it. If it is not supported, this flag is automatically set to false.
     * See [android.hardware.camera2.CaptureRequest.CONTROL_AF_MODE]
     */
    var autoFocus: Boolean by camera::autoFocus

    /** Allow manual focus on an area by tapping on camera view. True is on and false is off. */
    var touchToFocus: Boolean by camera::touchToFocus

    /** Allow pinch gesture on camera view for digital zooming. True is on and false is off. */
    var pinchToZoom: Boolean by camera::pinchToZoom

    /** Maximum digital zoom supported by selected camera device. */
    val maxDigitalZoom: Float by camera::maxDigitalZoom

    /** Set digital zoom value. Must be between 1.0f and [maxDigitalZoom] inclusive. */
    var currentDigitalZoom: Float by camera::currentDigitalZoom

    /**
     * Set auto white balance mode for preview and still captures. Supported values are [Modes.AutoWhiteBalance].
     * See [android.hardware.camera2.CaptureRequest.CONTROL_AWB_MODE]
     */
    @get:Modes.AutoWhiteBalance
    @setparam:Modes.AutoWhiteBalance
    var awb: Int by camera::awb

    /**
     * Set flash mode. Supported values are [Modes.Flash].
     * See [android.hardware.camera2.CaptureRequest.FLASH_MODE]
     */
    @get:Modes.Flash
    @setparam:Modes.Flash
    var flash: Int by camera::flash

    /**
     * Turn on or off optical stabilization for preview and still captures.
     * See [android.hardware.camera2.CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE]
     */
    var opticalStabilization: Boolean by camera::opticalStabilization

    /**
     * Set noise reduction mode. Supported values are [Modes.NoiseReduction].
     * See [android.hardware.camera2.CaptureRequest.NOISE_REDUCTION_MODE]
     */
    @get:Modes.NoiseReduction
    @setparam:Modes.NoiseReduction
    var noiseReduction: Int by camera::noiseReduction

    /** Current shutter time in milliseconds. Supported values are [Modes.Shutter]. */
    @get:Modes.Shutter
    @setparam:Modes.Shutter
    var shutter: Int by preview.shutterView::shutterTime

    /**
     * Set zero shutter lag mode capture.
     * See [android.hardware.camera2.CameraDevice.TEMPLATE_ZERO_SHUTTER_LAG]
     */
    var zsl: Boolean by camera::zsl

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
                cameraMode = getInt(R.styleable.CameraView_cameraMode, Modes.DEFAULT_CAMERA_MODE)
                outputFormat = getInt(R.styleable.CameraView_outputFormat, Modes.DEFAULT_OUTPUT_FORMAT)
                jpegQuality = getInt(R.styleable.CameraView_jpegQuality, Modes.DEFAULT_JPEG_QUALITY)
                facing = getInt(R.styleable.CameraView_facing, Modes.DEFAULT_FACING)
                aspectRatio = getString(R.styleable.CameraView_aspectRatio)
                        .run ar@{
                            if (this@ar.isNullOrBlank()) Modes.DEFAULT_ASPECT_RATIO
                            else AspectRatio.parse(this@ar)
                        }

                autoFocus = getBoolean(R.styleable.CameraView_autoFocus, Modes.DEFAULT_AUTO_FOCUS)
                touchToFocus = getBoolean(R.styleable.CameraView_touchToFocus, Modes.DEFAULT_TOUCH_TO_FOCUS)
                pinchToZoom = getBoolean(R.styleable.CameraView_pinchToZoom, Modes.DEFAULT_PINCH_TO_ZOOM)
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

    private fun createPreview(context: Context): PreviewImpl = TextureViewPreview(context, this)

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
                super.onMeasure(
                        widthMeasureSpec,
                        View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
                )
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

        if (displayOrientationDetector.lastKnownDisplayOrientation % 180 == 0) ratio = ratio.inverse()

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
                    jpegQuality,
                    facing,
                    aspectRatio,
                    autoFocus,
                    touchToFocus,
                    pinchToZoom,
                    currentDigitalZoom,
                    awb,
                    flash,
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
            jpegQuality = it.jpegQuality
            facing = it.facing
            aspectRatio = it.ratio
            autoFocus = it.autoFocus
            touchToFocus = it.touchToFocus
            pinchToZoom = it.pinchToZoom
            currentDigitalZoom = it.currentDigitalZoom
            awb = it.awb
            flash = it.flash
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
     * Ideally, next frame should only be processed once current frame is done processing.
     * Continuously launching background tasks for each frame is is not memory efficient,
     * the device will run out of memory very quickly and force close the app.
     *
     * @param listener lambda with image of type [Image] as its argument which is the preview frame.
     * It is always of type [android.graphics.ImageFormat.YUV_420_888]
     * @return instance of [CameraView] it is called on
     * @sample setupCameraSample
     */
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    fun setPreviewFrameListener(listener: (image: Image) -> Unit): CameraView {
        if (this.listener.isEnabled) previewFrameListener = listener
        return this
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
     * @param listener lambda with imageData of type [ByteArray] as argument
     * which is image data of the captured image, of format set with [CameraView.outputFormat]
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
    fun removePictureTakenListener(listener: (ByteArray) -> Unit): CameraView {
        pictureTakenListeners.remove(listener)
        return this
    }

    /**
     * Add a new camera error [listener].
     * If no error listeners are added, then "critical" errors will be thrown to system exception handler (ie. hard crash)
     * The only critical error thrown for now is for invalid aspect ratio.
     *
     * @param listener lambda with t of type [Throwable] as argument
     * @return instance of [CameraView] it is called on
     */
    fun addCameraErrorListener(listener: (t: Throwable) -> Unit): CameraView {
        cameraErrorListeners.add(listener)
        return this
    }

    /**
     * Remove camera error [listener].
     * @param listener that was previously added.
     * @return instance of [CameraView] it is called on
     */
    fun removeCameraErrorListener(listener: (Throwable) -> Unit): CameraView {
        cameraErrorListeners.remove(listener)
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
        else listener.onCameraError(Exception("Cannot capture still picture in camera mode $cameraMode"))
    }

    /**
     * Start capturing video.
     * @param outputFile where video will be saved
     * @param config lambda on [VideoConfiguration] (optional) (if not provided, it uses default configuration)
     */
    @RequiresPermission(allOf = [
        Manifest.permission.CAMERA,
        Manifest.permission.WRITE_EXTERNAL_STORAGE,
        Manifest.permission.RECORD_AUDIO
    ])
    fun startVideoRecording(outputFile: File, config: VideoConfiguration.() -> Unit = {}) {
        if (cameraMode == Modes.CameraMode.VIDEO_CAPTURE) {
            camera.startVideoRecording(outputFile, VideoConfiguration().apply(config))
        } else {
            listener.onCameraError(Exception("Cannot start video recording in camera mode $cameraMode"))
        }
    }

    /**
     * Pause video recording
     * @return true if the video was paused false otherwise
     */
    @TargetApi(Build.VERSION_CODES.N)
    fun pauseVideoRecording(): Boolean = camera.pauseVideoRecording()

    /**
     * Resume video recording
     * @return true if the video was resumed false otherwise
     */
    @TargetApi(Build.VERSION_CODES.N)
    fun resumeVideoRecording(): Boolean = camera.resumeVideoRecording()

    /**
     * Stop video recording
     * @return true if video was stopped and saved to given outputFile, false otherwise
     */
    fun stopVideoRecording(): Boolean = camera.stopVideoRecording()

    @Parcelize
    internal data class SavedState(
            val parcelable: Parcelable,
            val cameraMode: Int,
            val outputFormat: Int,
            val jpegQuality: Int,
            val facing: Int,
            val ratio: AspectRatio,
            val autoFocus: Boolean,
            val touchToFocus: Boolean,
            val pinchToZoom: Boolean,
            val currentDigitalZoom: Float,
            val awb: Int,
            val flash: Int,
            val opticalStabilization: Boolean,
            val noiseReduction: Int,
            val shutter: Int,
            val zsl: Boolean
    ) : View.BaseSavedState(parcelable), Parcelable
}