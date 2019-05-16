/*
 * Copyright 2019 Priyank Vasa
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
import android.app.Activity
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.support.annotation.RequiresApi
import android.support.annotation.RequiresPermission
import android.support.v4.view.ViewCompat
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import com.priyankvasa.android.cameraviewex.R.attr.outputFormat
import com.priyankvasa.android.cameraviewex.extension.getValue
import com.priyankvasa.android.cameraviewex.extension.isUiThread
import com.priyankvasa.android.cameraviewex.extension.setValue
import kotlinx.android.parcel.Parcelize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.android.BuildConfig
import timber.log.Timber
import java.io.File
import java.util.SortedSet

class CameraView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    init {
        if (!isInEditMode && BuildConfig.DEBUG) {
            Timber.forest().find { it is Timber.DebugTree } ?: Timber.plant(Timber.DebugTree())
            System.setProperty("kotlinx.coroutines.debug", "on")
        }
    }

    private val parentJob: Job by lazy { SupervisorJob() }

    private val coroutineScope: CoroutineScope by lazy { CoroutineScope(parentJob + Dispatchers.Main) }

    private val listenerManager: CameraListenerManager by lazy {
        CameraListenerManager(SupervisorJob(parentJob))
            .apply { cameraOpenedListeners.add { requestLayout() } }
            .also { if (isInEditMode) it.disable() }
    }

    private val config: CameraConfiguration =
        CameraConfiguration.newInstance(
            context,
            attrs,
            defStyleAttr,
            { adjustViewBounds = it },
            { message: String, cause: Throwable ->
                listenerManager.onCameraError(CameraViewException(message, cause), ErrorLevel.Warning)
            }
        )

    private val preview: PreviewImpl by lazy {
        createPreview(context)
            .also {
                // Add shutter view to CameraView
                addView(it.shutterView)
            }
    }

    /** Display orientation detector */
    private val orientationDetector: OrientationDetector by lazy {

        object : OrientationDetector(context) {

            override fun onDisplayOrientationChanged(displayOrientation: Int) {
                preview.setDisplayOrientation(displayOrientation)
                camera.deviceRotation = displayOrientation
                camera.screenRotation = displayOrientation
            }

            override fun onSensorOrientationChanged(sensorOrientation: Int) {
                val rotation: Int = when (val orientation: Orientation = Orientation.parse(sensorOrientation)) {
                    Orientation.Portrait, Orientation.PortraitInverted -> orientation.value
                    Orientation.Landscape -> Orientation.LandscapeInverted.value
                    Orientation.LandscapeInverted -> Orientation.Landscape.value
                    Orientation.Unknown -> return
                }
                if (camera.deviceRotation != rotation) camera.deviceRotation = rotation
            }
        }
    }

    private lateinit var camera: CameraInterface

    init {
        if (!isInEditMode) {

            // Based on OS version select the best camera implementation
            camera = when {
                Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP ->
                    Camera1(listenerManager, preview, config, SupervisorJob(parentJob))
                Build.VERSION.SDK_INT < Build.VERSION_CODES.M ->
                    Camera2(listenerManager, preview, config, SupervisorJob(parentJob), context)
                Build.VERSION.SDK_INT < Build.VERSION_CODES.N ->
                    Camera2Api23(listenerManager, preview, config, SupervisorJob(parentJob), context)
                else -> Camera2Api24(listenerManager, preview, config, SupervisorJob(parentJob), context)
            }


            config.aspectRatio.observe(camera) {
                camera.setAspectRatio(it)
                coroutineScope.launch { requestLayout() }
            }
            config.shutter.observe(camera) { preview.shutterView.shutterTime = it }
        }
    }

    internal val isUiTestCompatible: Boolean get() = camera is Camera2

    /**
     * Returns `true` if this [CameraView] instance is active and usable.
     * It will `return` false after [destroy] is called.
     * A new instance should be created if [isActive] is false.
     */
    val isActive: Boolean get() = camera.isActive && parentJob.isActive

    /** `true` if the camera is opened `false` otherwise. */
    val isCameraOpened: Boolean get() = camera.isCameraOpened

    /** Id of currently opened camera device */
    val cameraId: String get() = camera.cameraId

    /**
     * List of ids of camera devices for selected [facing]
     */
    val cameraIdsForFacing: SortedSet<String> get() = camera.cameraIdsForFacing

    /** `true` if there is a video recording in progress, `false` otherwise. */
    val isVideoRecording: Boolean get() = camera.isVideoRecording

    /** Check if [Modes.CameraMode.SINGLE_CAPTURE] is enabled */
    val isSingleCaptureModeEnabled: Boolean get() = config.isSingleCaptureModeEnabled

    /** Check if [Modes.CameraMode.CONTINUOUS_FRAME] is enabled */
    val isContinuousFrameModeEnabled: Boolean get() = config.isContinuousFrameModeEnabled

    /** Check if [Modes.CameraMode.VIDEO_CAPTURE] is enabled */
    val isVideoCaptureModeEnabled: Boolean get() = config.isVideoCaptureModeEnabled

    /**
     * True when this CameraView is adjusting its bounds to preserve the aspect ratio of
     * camera.
     */
    var adjustViewBounds: Boolean = false
        set(value) {
            if (value == field || !requireInUiThread()) return
            field = value
            requestLayout()
        }

    /** Current aspect ratio of camera. Valid format is "height:width" eg. "4:3". */
    var aspectRatio: AspectRatio
        get() = config.aspectRatio.value
        set(ratio) {
            // Check if new aspect ratio is supported
            if (!supportedAspectRatios.contains(ratio)) {
                listenerManager.onCameraError(
                    CameraViewException("Aspect ratio $this is not supported by this device." +
                        " Valid ratios are CameraView.supportedAspectRatios $supportedAspectRatios."),
                    ErrorLevel.ErrorCritical
                )
                return
            }
            config.aspectRatio.value = ratio
        }

    /**
     * Set desired size for continuous frames. This only affects dimensions of frame, the orientation is decided by [aspectRatio].
     *
     * Valid formats are [W1440,H1080], [H1440,W1080], [W1440,1080], [1440,W1080], [H1440,1080], [1440,H1080]
     * The output is not guaranteed to be of this size.
     * If it is not supported by camera device or in wrong format,
     * it will silently fallback to using best size based on aspect ratio.
     *
     * NOTE: Behaviour for camera1 (API < 21) is different.
     * On setting this value, the actual preview will also be of this size but will be squeezed into [aspectRatio].
     * This is due to how camera1 api does not allow setting different sizes for preview surface and output.
     */
    var continuousFrameSize: Size by config.continuousFrameSize::value

    /**
     * Set desired size for single capture images. This only affects dimensions of image, the orientation is decided by [aspectRatio].
     *
     * Valid formats are [W1440,H1080], [H1440,W1080], [W1440,1080], [1440,W1080], [H1440,1080], [1440,H1080]
     * The output is not guaranteed to be of this size.
     * If it is not supported by camera device or in wrong format,
     * it will silently fallback to using best size based on aspect ratio.
     */
    var singleCaptureSize: Size by config.singleCaptureSize::value

    /**
     * Set format of the output of image data produced from the camera for [Modes.CameraMode.SINGLE_CAPTURE] mode.
     * Supported values are [Modes.OutputFormat].
     */
    @get:Modes.OutputFormat
    @setparam:Modes.OutputFormat
    var outputFormat: Int by config.outputFormat::value

    /**
     * Set image quality of the output image.
     * This property is only applicable for [outputFormat] [Modes.OutputFormat.JPEG]
     * Supported values are [Modes.JpegQuality].
     */
    @get:Modes.JpegQuality
    @setparam:Modes.JpegQuality
    var jpegQuality: Int by config.jpegQuality::value

    /** Set which camera to use (like front or back). Supported values are [Modes.Facing]. */
    @get:Modes.Facing
    @setparam:Modes.Facing
    var facing: Int
        get() = config.facing.value
        set(value) {
            if (!requireInUiThread()) return
            config.facing.value = value
        }

    /** Gets all the aspect ratios supported by the current camera. */
    val supportedAspectRatios: Set<AspectRatio> get() = camera.supportedAspectRatios

    /**
     * Set auto focus mode for selected camera. Supported modes are [Modes.AutoFocus].
     * See [android.hardware.camera2.CaptureRequest.CONTROL_AF_MODE]
     */
    @get:Modes.AutoFocus
    @setparam:Modes.AutoFocus
    var autoFocus: Int
        get() = config.autoFocus.value
        set(value) {
            if (!requireInUiThread()) return
            config.autoFocus.value = value
        }

    /** Allow manual focus on an area by tapping on camera view. True is on and false is off. */
    var touchToFocus: Boolean by config.touchToFocus::value

    /** Allow pinch gesture on camera view for digital zooming. True is on and false is off. */
    var pinchToZoom: Boolean by config.pinchToZoom::value

    /** Maximum digital zoom supported by selected camera device. */
    val maxDigitalZoom: Float get() = camera.maxDigitalZoom

    /** Set digital zoom value. Must be between 1.0f and [maxDigitalZoom] inclusive. */
    var currentDigitalZoom: Float
        get() = config.currentDigitalZoom.value
        set(value) {
            if (!requireInUiThread()) return
            config.currentDigitalZoom.value = value
        }

    /**
     * Set auto white balance mode for preview and still captures. Supported values are [Modes.AutoWhiteBalance].
     * See [android.hardware.camera2.CaptureRequest.CONTROL_AWB_MODE]
     */
    @get:Modes.AutoWhiteBalance
    @setparam:Modes.AutoWhiteBalance
    var awb: Int
        get() = config.awb.value
        set(value) {
            if (!requireInUiThread()) return
            config.awb.value = value
        }

    /**
     * Set flash mode. Supported values are [Modes.Flash].
     * See [android.hardware.camera2.CaptureRequest.FLASH_MODE]
     */
    @get:Modes.Flash
    @setparam:Modes.Flash
    var flash: Int
        get() = config.flash.value
        set(value) {
            if (!requireInUiThread()) return
            config.flash.value = value
        }

    /**
     * Turn on or off optical stabilization for preview and still captures.
     * See [android.hardware.camera2.CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE]
     */
    var opticalStabilization: Boolean
        get() = config.opticalStabilization.value
        set(value) {
            if (!requireInUiThread()) return
            config.opticalStabilization.value = value
        }

    /**
     * Set noise reduction mode. Supported values are [Modes.NoiseReduction].
     * See [android.hardware.camera2.CaptureRequest.NOISE_REDUCTION_MODE]
     */
    @get:Modes.NoiseReduction
    @setparam:Modes.NoiseReduction
    var noiseReduction: Int
        get() = config.noiseReduction.value
        set(value) {
            if (!requireInUiThread()) return
            config.noiseReduction.value = value
        }

    /** Current shutter time in milliseconds. Supported values are [Modes.Shutter]. */
    @get:Modes.Shutter
    @setparam:Modes.Shutter
    var shutter: Int by config.shutter::value

    /**
     * Set zero shutter lag capture mode.
     * See [android.hardware.camera2.CameraDevice.TEMPLATE_ZERO_SHUTTER_LAG]
     */
    var zsl: Boolean
        get() = config.zsl.value
        set(value) {
            if (!requireInUiThread()) return
            config.zsl.value = value
        }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!isInEditMode) ViewCompat.getDisplay(this)?.let { orientationDetector.enable(it) }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        if (!isInEditMode) orientationDetector.disable()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {

        if (!isInEditMode && !isCameraOpened) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            return
        }

        // Handle android:adjustViewBounds
        if (adjustViewBounds) {

            val widthMode: Int = MeasureSpec.getMode(widthMeasureSpec)
            val heightMode: Int = MeasureSpec.getMode(heightMeasureSpec)

            if (widthMode == MeasureSpec.EXACTLY && heightMode != MeasureSpec.EXACTLY) {
                val ratio: AspectRatio = config.aspectRatio.value.inverse()
                var height: Int = (MeasureSpec.getSize(widthMeasureSpec) * ratio.toFloat()).toInt()
                if (heightMode == MeasureSpec.AT_MOST) {
                    height = Math.min(height, MeasureSpec.getSize(heightMeasureSpec))
                }
                super.onMeasure(
                    widthMeasureSpec,
                    MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY)
                )
            } else if (widthMode != MeasureSpec.EXACTLY && heightMode == MeasureSpec.EXACTLY) {
                val ratio: AspectRatio = config.aspectRatio.value
                var width: Int = (MeasureSpec.getSize(heightMeasureSpec) * ratio.toFloat()).toInt()
                if (widthMode == MeasureSpec.AT_MOST) {
                    width = Math.min(width, MeasureSpec.getSize(widthMeasureSpec))
                }
                super.onMeasure(
                    MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                    heightMeasureSpec
                )
            } else {
                super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            }
        } else {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        }

        if (isInEditMode) return // Don't measure texture view and shutter view in edit mode

        val wMeasureSpec: Int = MeasureSpec.makeMeasureSpec(measuredWidth, MeasureSpec.EXACTLY)
        val hMeasureSpec: Int = MeasureSpec.makeMeasureSpec(measuredHeight, MeasureSpec.EXACTLY)

        // Measure texture view and shutter view
        preview.measure(wMeasureSpec, hMeasureSpec)
    }

    override fun onSaveInstanceState(): Parcelable =
        SavedState(
            super.onSaveInstanceState() ?: Bundle(),
            adjustViewBounds,
            config.cameraMode.value,
            outputFormat,
            jpegQuality,
            facing,
            config.aspectRatio.value,
            continuousFrameSize,
            singleCaptureSize,
            autoFocus,
            touchToFocus,
            pinchToZoom,
            currentDigitalZoom,
            awb,
            flash,
            opticalStabilization,
            noiseReduction,
            config.shutter.value,
            zsl
        )

    override fun onRestoreInstanceState(state: Parcelable?) {
        when (state) {
            is SavedState -> {
                super.onRestoreInstanceState(state.superState)
                adjustViewBounds = state.adjustViewBounds
                config.apply {
                    facing.value = state.facing
                    cameraMode.value = state.cameraMode
                    outputFormat.value = state.outputFormat
                    jpegQuality.value = state.jpegQuality
                    aspectRatio.value = state.ratio
                    continuousFrameSize.value = state.continuousFrameSize
                    singleCaptureSize.value = state.singleCaptureSize
                    autoFocus.value = state.autoFocus
                    touchToFocus.value = state.touchToFocus
                    pinchToZoom.value = state.pinchToZoom
                    currentDigitalZoom.value = state.currentDigitalZoom
                    awb.value = state.awb
                    flash.value = state.flash
                    opticalStabilization.value = state.opticalStabilization
                    noiseReduction.value = state.noiseReduction
                    shutter.value = state.shutter
                    zsl.value = state.zsl
                }
            }
            else -> super.onRestoreInstanceState(state)
        }
    }

    private fun createPreview(context: Context): PreviewImpl = TextureViewPreview(context, this)

    private fun requireActive(): Boolean = isActive.also {
        if (!it) listenerManager.onCameraError(
            CameraViewException("CameraView instance is destroyed and cannot be used further. Please create a new instance."),
            ErrorLevel.ErrorCritical
        )
    }

    private fun requireCameraOpened(): Boolean = isCameraOpened.also {
        if (!it) listenerManager.onCameraError(
            CameraViewException("Camera is not open. Call start() first."),
            errorLevel = ErrorLevel.Warning
        )
    }

    /** Set camera mode of operation. Supported values are [Modes.CameraMode]. */
    fun setCameraMode(@Modes.CameraMode mode: Int) {
        if (!requireInUiThread()) return
        config.cameraMode.value = mode
    }

    /**
     * Enable any single camera mode or multiple camera modes by passing [Modes.CameraMode].
     * Multiple modes can be enabled by passing bitwise or'ed value of multiple [Modes.CameraMode].
     * @sample enableCameraMode(Modes.CameraMode.SINGLE_CAPTURE or Modes.CameraMode.VIDEO_CAPTURE)
     */
    fun enableCameraMode(@Modes.CameraMode mode: Int) {
        if (!requireInUiThread()) return
        config.cameraMode.value = config.cameraMode.value or mode
    }

    /** Disable any camera mode from [Modes.CameraMode], one at a time */
    fun disableCameraMode(@Modes.CameraMode mode: Int) {
        if (!requireInUiThread()) return
        var newMode = 0
        when (mode) {
            Modes.CameraMode.CONTINUOUS_FRAME -> {
                if (isSingleCaptureModeEnabled) newMode = newMode or Modes.CameraMode.SINGLE_CAPTURE
                if (isVideoCaptureModeEnabled) newMode = newMode or Modes.CameraMode.VIDEO_CAPTURE
            }
            Modes.CameraMode.SINGLE_CAPTURE -> {
                if (isContinuousFrameModeEnabled) newMode = newMode or Modes.CameraMode.CONTINUOUS_FRAME
                if (isVideoCaptureModeEnabled) newMode = newMode or Modes.CameraMode.VIDEO_CAPTURE
            }
            Modes.CameraMode.VIDEO_CAPTURE -> {
                if (isContinuousFrameModeEnabled) newMode = newMode or Modes.CameraMode.CONTINUOUS_FRAME
                if (isSingleCaptureModeEnabled) newMode = newMode or Modes.CameraMode.SINGLE_CAPTURE
            }
            else -> {
                listenerManager.onCameraError(
                    CameraViewException("Invalid camera mode $mode"),
                    ErrorLevel.Warning
                )
                return
            }
        }
        config.cameraMode.value = newMode
    }

    /**
     * Open a camera device by camera ID and start showing camera preview. This is typically called from
     * [Activity.onResume].
     * @throws [CameraViewException] if [destroy] is already called and this [CameraView] instance is no longer active.
     */
    @JvmOverloads
    @RequiresPermission(Manifest.permission.CAMERA)
    fun start(cameraId: String = Modes.DEFAULT_CAMERA_ID) {

        if (!requireActive()) return

        if (isCameraOpened) {
            listenerManager.onCameraError(
                CameraViewException("Camera is already open. Call stop() first."),
                errorLevel = ErrorLevel.Warning
            )
            return
        }

        // Save original state and restore later if camera falls back to using Camera1
        val state: Parcelable = onSaveInstanceState()

        if (camera.start(cameraId)) return // Camera started successfully, return.

        // This camera instance is no longer useful, destroy it.
        camera.destroy()

        // Already tried using Camera1 api, return.
        // Errors leading to this situation are already posted from Camera1 api
        if (camera is Camera1) return

        // Device uses legacy hardware; fall back to Camera1
        fallback(cameraId, state)
    }

    private fun fallback(cameraId: String, savedState: Parcelable) {

        camera = Camera1(listenerManager, preview, config, SupervisorJob(parentJob))

        // Restore original state
        onRestoreInstanceState(savedState)

        // Try to start camera again using Camera1 api
        // Return if successful
        if (camera.start(cameraId)) return

        // Unable to start camera using any api. Post a critical error.
        listenerManager.onCameraError(
            CameraViewException("Unable to use camera or camera2 api." +
                " Please check if the camera hardware is usable and CameraView is correctly configured."),
            ErrorLevel.ErrorCritical
        )
    }

    /**
     * Open next camera in sequence of sorted camera ids for current [facing]
     *
     * If current open camera has a different facing then what is set currently
     * then this method will open the first camera for set [facing].
     */
    @RequiresPermission(Manifest.permission.CAMERA)
    fun nextCamera() {
        stop()
        start(camera.getNextCameraId())
    }

    /** Take a picture. The result will be returned to listeners added by [addPictureTakenListener]. */
    @RequiresPermission(Manifest.permission.CAMERA)
    fun capture(): Unit = when {

        !requireActive() || !requireCameraOpened() -> Unit

        !config.isSingleCaptureModeEnabled -> listenerManager.onCameraError(
            CameraViewException("Single capture mode is disabled." +
                " Update camera mode by" +
                " `CameraView.cameraMode = Modes.CameraMode.SINGLE_CAPTURE`" +
                " to enable and capture images."),
            ErrorLevel.ErrorCritical
        )

        else -> camera.takePicture()
    }

    /**
     * Start capturing video.
     * @param outputFile where video will be saved
     * @param videoConfig lambda on [VideoConfiguration] (optional) (if not provided, it uses default configuration)
     */
    @RequiresPermission(allOf = [
        Manifest.permission.CAMERA,
        Manifest.permission.WRITE_EXTERNAL_STORAGE,
        Manifest.permission.RECORD_AUDIO
    ])
    @JvmOverloads
    fun startVideoRecording(outputFile: File, videoConfig: VideoConfiguration.() -> Unit = {}): Unit = when {

        !requireActive() || !requireCameraOpened() -> Unit

        !config.isVideoCaptureModeEnabled -> listenerManager.onCameraError(
            CameraViewException("Video capture mode is disabled." +
                " Update camera mode by" +
                " `CameraView.cameraMode = Modes.CameraMode.VIDEO_CAPTURE`" +
                " to enable and capture videos."),
            ErrorLevel.ErrorCritical
        )

        isVideoRecording -> listenerManager.onCameraError(
            CameraViewException("Video recording already in progress." +
                " Call CameraView.stopVideoRecording() before calling start."),
            ErrorLevel.Warning
        )

        else -> runCatching { camera.startVideoRecording(outputFile, VideoConfiguration().apply(videoConfig)) }
            .getOrElse { listenerManager.onCameraError(CameraViewException("Unable to start video recording.", it)) }
    }

    /**
     * Pause video recording
     * @return true if the video was paused false otherwise
     * Note: Always returns false on API < 24
     */
    @RequiresApi(Build.VERSION_CODES.N)
    fun pauseVideoRecording(): Boolean = camera.pauseVideoRecording()

    /**
     * Resume video recording
     * @return true if the video was resumed false otherwise
     * Note: Always returns false on API < 24
     */
    @RequiresApi(Build.VERSION_CODES.N)
    fun resumeVideoRecording(): Boolean = camera.resumeVideoRecording()

    /**
     * Stop video recording
     * @return true if video was stopped and saved to given outputFile, false otherwise
     */
    fun stopVideoRecording(): Boolean = camera.stopVideoRecording()

    /**
     * Stop camera preview and close the device.
     * This is typically called from fragment's onPause callback.
     */
    fun stop(): Unit = camera.stop()

    /**
     * Clear all listeners, [stop] camera, and kill background threads.
     * Once [destroy] is called, camera cannot be started.
     * A new [CameraView] instance must be created to use camera again.
     * This is typically called from fragment's onDestroyView callback.
     */
    fun destroy() {
        if (!isActive) {
            listenerManager.onCameraError(
                CameraViewException("CameraView instance already destroyed."),
                ErrorLevel.Warning
            )
            return
        }
        listenerManager.destroy()
        camera.destroy()
        parentJob.cancel()
    }

    /**
     * Add a new camera opened [listener].
     * @param listener lambda
     * @return instance of [CameraView] it is called on
     */
    fun addCameraOpenedListener(listener: () -> Unit): CameraView {
        if (listenerManager.isEnabled) listenerManager.cameraOpenedListeners.add(listener)
        return this
    }

    /**
     * Remove camera opened [listener].
     * @param listener that was previously added.
     * @return instance of [CameraView] it is called on
     */
    fun removeCameraOpenedListener(listener: () -> Unit): CameraView {
        listenerManager.cameraOpenedListeners.remove(listener)
        return this
    }

    /**
     * Set preview frame [listener].
     *
     * @param listener lambda with image of type [Image] as its argument
     * which contains the frame data from camera and its metadata
     * in form of [com.priyankvasa.android.cameraviewex.exif.ExifInterface].
     * @param maxFrameRate is maximum number of frames per second.
     *   Actual frame rate might be less based on device capabilities but will not be more than this value.
     *   A float can be set for eg., max frame rate of 0.5f will produce one frame every 2 seconds and so on.
     *   Any value less than or equal to zero (<= 0f) will produce maximum frames per second supported by device.
     *   Not providing this value also defaults to maximum possible frame rate.
     *
     * @return instance of [CameraView] it is called on
     *
     * @sample com.priyankvasa.android.cameraviewex_sample.camera.CameraPreviewFrameHandler.listener
     */
    @JvmOverloads
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    fun setContinuousFrameListener(maxFrameRate: Float = 0f, listener: (image: Image) -> Unit): CameraView {
        if (listenerManager.isEnabled) {
            camera.maxPreviewFrameRate = maxFrameRate
            listenerManager.continuousFrameListener = listener
        }
        return this
    }

    /**
     * Remove preview frame listener.
     * @return instance of [CameraView] it is called on
     */
    fun removeContinuousFrameListener(): CameraView {
        listenerManager.continuousFrameListener = null
        return this
    }

    /**
     * Add a new picture taken [listener].
     * @param listener lambda with imageData of type [ByteArray] as argument
     * which is image data of the captured image, of format set with [CameraView.outputFormat]
     * @return instance of [CameraView] it is called on
     */
    fun addPictureTakenListener(listener: (image: Image) -> Unit): CameraView {
        if (listenerManager.isEnabled) listenerManager.pictureTakenListeners.add(listener)
        return this
    }

    /**
     * Remove picture taken [listener].
     * @return instance of [CameraView] it is called on
     */
    fun removePictureTakenListener(listener: (Image) -> Unit): CameraView {
        listenerManager.pictureTakenListeners.remove(listener)
        return this
    }

    /**
     * Add a new camera error [listener].
     * If no error listeners are added, then "critical" errors will be thrown to system exception handler (ie. hard crash)
     * The only critical error thrown for now is for invalid aspect ratio.
     *
     * @param listener lambda with t of type [Throwable] and errorLevel of type [ErrorLevel] as arguments
     * @return instance of [CameraView] it is called on
     */
    fun addCameraErrorListener(listener: (t: Throwable, errorLevel: ErrorLevel) -> Unit): CameraView {
        listenerManager.cameraErrorListeners.add(listener)
        return this
    }

    /**
     * Remove camera error [listener].
     * @param listener that was previously added.
     * @return instance of [CameraView] it is called on
     */
    fun removeCameraErrorListener(listener: (Throwable, ErrorLevel) -> Unit): CameraView {
        listenerManager.cameraErrorListeners.remove(listener)
        return this
    }

    /**
     * Add a new camera closed [listener].
     * @param listener lambda
     * @return instance of [CameraView] it is called on
     */
    fun addCameraClosedListener(listener: () -> Unit): CameraView {
        if (listenerManager.isEnabled) listenerManager.cameraClosedListeners.add(listener)
        return this
    }

    /**
     * Remove camera closed [listener].
     * @param listener that was previously added.
     * @return instance of [CameraView] it is called on
     */
    fun removeCameraClosedListener(listener: () -> Unit): CameraView {
        listenerManager.cameraClosedListeners.remove(listener)
        return this
    }

    /**
     * Add a new video record started [listener].
     * @param listener lambda
     * @return instance of [CameraView] it was called on
     */
    fun addVideoRecordStartedListener(listener: () -> Unit): CameraView {
        listenerManager.videoRecordStartedListeners.add(listener)
        return this
    }

    /**
     * Remove video record started [listener].
     * @param listener that was previously added.
     * @return instance of [CameraView] it is called on
     */
    fun removeVideoRecordStartedListener(listener: () -> Unit): CameraView {
        listenerManager.videoRecordStartedListeners.remove(listener)
        return this
    }

    /**
     * Add a new video record stopped [listener].
     * @param listener lambda
     * @return instance of [CameraView] it was called on
     */
    fun addVideoRecordStoppedListener(listener: (isSuccess: Boolean) -> Unit): CameraView {
        listenerManager.videoRecordStoppedListeners.add(listener)
        return this
    }

    /**
     * Remove video record stopped [listener].
     * @param listener that was previously added.
     * @return instance of [CameraView] it is called on
     */
    fun removeVideoRecordStoppedListener(listener: (isSuccess: Boolean) -> Unit): CameraView {
        listenerManager.videoRecordStoppedListeners.remove(listener)
        return this
    }

    /** Remove all listeners previously set. */
    fun removeAllListeners() {
        listenerManager.clear()
    }

    @Parcelize
    internal data class SavedState(
        val parcelable: Parcelable,
        val adjustViewBounds: Boolean,
        val cameraMode: Int,
        val outputFormat: Int,
        val jpegQuality: Int,
        val facing: Int,
        val ratio: AspectRatio,
        val continuousFrameSize: Size,
        val singleCaptureSize: Size,
        val autoFocus: Int,
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

@JvmSynthetic
internal fun requireInUiThread(): Boolean = Thread.currentThread().isUiThread
    .also { if (!it) throw CameraViewException("This task needs to be executed in UI thread.") }