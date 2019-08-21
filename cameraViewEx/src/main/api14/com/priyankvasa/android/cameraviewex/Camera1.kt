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

@file:Suppress("DEPRECATION")

package com.priyankvasa.android.cameraviewex

import android.arch.lifecycle.Lifecycle
import android.arch.lifecycle.LifecycleRegistry
import android.graphics.ImageFormat
import android.hardware.Camera
import android.os.Build
import android.os.SystemClock
import android.support.v4.util.SparseArrayCompat
import android.util.SparseIntArray
import android.view.SurfaceHolder
import com.priyankvasa.android.cameraviewex.exif.ExifInterface
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.IOException
import java.util.SortedSet
import java.util.TreeSet
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.CoroutineContext
import kotlin.math.roundToInt

internal class Camera1(
    private val listener: CameraInterface.Listener,
    private val preview: PreviewImpl,
    private val config: CameraConfiguration,
    private val cameraJob: Job
) : CameraInterface {

    override val coroutineContext: CoroutineContext get() = Dispatchers.Default + cameraJob

    private val lifecycleRegistry: LifecycleRegistry by lazy {
        LifecycleRegistry(this).also { it.markState(Lifecycle.State.CREATED) }
    }

    /** A [Semaphore] to prevent concurrent opening or closing camera. */
    private val cameraOpenCloseLock: Semaphore by lazy { Semaphore(1, true) }

    /** A [Semaphore] to prevent concurrent starting or stopping preview. */
    private val previewStartStopLock: Semaphore by lazy { Semaphore(1, true) }

    private var cameraIdInternal: Int = Modes.Facing.FACING_BACK

    override val cameraId: String get() = cameraIdInternal.toString()

    /**
     * Gets a list of camera ids for the current facing direction
     */
    override val cameraIdsForFacing: SortedSet<String>
        get() = (0 until Camera.getNumberOfCameras())
            .filter { id: Int ->
                val info = Camera.CameraInfo()
                runCatching { Camera.getCameraInfo(id, info) }.getOrNull() != null &&
                    info.facing == internalFacing
            }
            .mapTo(TreeSet<String>()) { it.toString() }

    private val isPictureCaptureInProgress: AtomicBoolean by lazy { AtomicBoolean(false) }

    var camera: Camera? = null

    private val videoManager: VideoManager
        by lazy { VideoManager { listener.onCameraError(CameraViewException(it), ErrorLevel.Warning) } }

    private var debounceIntervalMillis: Int = -1

    override var maxPreviewFrameRate: Float = -1f
        set(value) {
            field = value
            debounceIntervalMillis = when {
                value <= 0 -> -1
                value < 1 -> ((1 / value) * 1000).roundToInt()
                else -> (1000 / value).roundToInt()
            }
        }

    private val previewCallback: Camera.PreviewCallback by lazy {

        // Timestamp of the last frame processed. Used for de-bouncing purposes.
        val lastTimeStamp = AtomicLong(0L)

        Camera.PreviewCallback { data: ByteArray?, camera: Camera? ->

            // Data may be null when Camera api is "catching breath" between frame generation
            if (camera == null || data == null) return@PreviewCallback

            // Use debounce logic only if interval is > 0. If not, it means user wants max frame rate
            if (debounceIntervalMillis > 0) {
                val currentTimeStamp: Long = SystemClock.elapsedRealtime()
                // Debounce if current timestamp is within debounce interval
                if (debounceIntervalMillis > currentTimeStamp - lastTimeStamp.get()) return@PreviewCallback
                // Otherwise update last frame timestamp to current
                else lastTimeStamp.set(currentTimeStamp)
            }

            launch(CoroutineExceptionHandler { _, _ -> }) {
                val image = Image(
                    data,
                    camera.parameters.previewSize.width,
                    camera.parameters.previewSize.height,
                    ExifInterface().apply { rotation = calcCameraRotation(deviceRotation) },
                    camera.parameters.previewFormat
                )
                listener.onPreviewFrame(image)
            }
        }
    }

    private val pictureCallback: Camera.PictureCallback by lazy {
        Camera.PictureCallback { data: ByteArray?, camera: Camera? ->
            isPictureCaptureInProgress.set(false)
            camera?.cancelAutoFocus()
            startPreview()
            if (camera == null || data == null) return@PictureCallback
            camera.runCatching {
                val image = Image(
                    data,
                    parameters.pictureSize.width,
                    parameters.pictureSize.height,
                    ExifInterface().apply {
                        if (parameters.pictureFormat == ImageFormat.JPEG && readExifSafe(data)) return@apply
                        rotation = calcCameraRotation(deviceRotation)
                    },
                    parameters.pictureFormat
                )
                listener.onPictureTaken(image)
            }
        }
    }

    private val internalFacings: SparseIntArray by lazy {
        SparseIntArray().apply {
            put(Modes.Facing.FACING_BACK, Camera.CameraInfo.CAMERA_FACING_BACK)
            put(Modes.Facing.FACING_FRONT, Camera.CameraInfo.CAMERA_FACING_FRONT)
            put(Modes.Facing.FACING_EXTERNAL, 2) // CameraCharacteristics.LENS_FACING_EXTERNAL
        }
    }

    private val internalFacing: Int get() = internalFacings[config.facing.value]

    private val cameraInfo: Camera.CameraInfo by lazy { Camera.CameraInfo() }

    private val previewSizes: SizeMap by lazy { SizeMap() }

    private val pictureSizes: SizeMap by lazy { SizeMap() }

    private var showingPreview: Boolean = false

    override var deviceRotation: Int = 0
        set(value) {
            field = value
            if (isCameraOpened) updateCameraParams { setRotation(calcCameraRotation(value)) }
        }

    override var screenRotation: Int = 0

    override val isActive: Boolean get() = cameraJob.isActive

    override val isCameraOpened: Boolean get() = camera != null

    override val isVideoRecording: Boolean get() = videoManager.isVideoRecording

    override val supportedAspectRatios: Set<AspectRatio>
        get() {
            previewSizes.ratios()
                .asSequence()
                .filter { pictureSizes.sizes(it).isEmpty() }
                .forEach { previewSizes.remove(it) }
            return previewSizes.ratios()
        }

    private var autoFocus: Boolean = false
        get() =
            if (!isCameraOpened) field
            else camera?.parameters?.focusMode?.equals(Camera.Parameters.FOCUS_MODE_FIXED) == false
        set(value) {
            if (field == value) return
            field = if (isCameraOpened) setAutoFocusInternal(value) && value else value
        }

    override val maxDigitalZoom: Float = 1f

    private var flash: Int = Modes.DEFAULT_FLASH
        set(value) {
            if (isCameraOpened) updateCameraParams {
                val modes: MutableList<String>? = camera?.parameters?.supportedFlashModes
                val mode: String? = FLASH_MODES.get(value)
                if (mode != null && modes?.contains(mode) == true) {
                    flashMode = mode
                    field = value
                }
                val currentMode: String? = FLASH_MODES.get(field)
                if (modes == null || currentMode == null || !modes.contains(currentMode)) {
                    flashMode = Camera.Parameters.FLASH_MODE_OFF
                    field = Modes.Flash.FLASH_OFF
                }
            } else field = value
        }

    private fun previewSurfaceChangedAction() {
        runCatching { setUpPreview() }
            .onFailure {
                listener.onCameraError(CameraViewException("Unable to setup preview.", it))
                return
            }
        if (isCameraOpened) adjustCameraParameters()
    }

    init {
        preview.surfaceChangeListener = { previewSurfaceChangedAction() }
        addObservers()
    }

    override fun getLifecycle(): Lifecycle = lifecycleRegistry

    private fun addObservers(): Unit = config.run {

        facing.observe(this@Camera1) {
            if (isCameraOpened) {
                stop()
                start(Modes.DEFAULT_CAMERA_ID)
            }
        }

        continuousFrameSize.observe(this@Camera1) {
            if (isCameraOpened) adjustCameraParameters()
        }

        singleCaptureSize.observe(this@Camera1) {
            if (isCameraOpened) adjustCameraParameters()
        }

        cameraMode.observe(this@Camera1) {
            if (isCameraOpened) {
                stopPreview()
                startPreview()
            }
        }

        autoFocus.observe(this@Camera1) { this@Camera1.autoFocus = it != Modes.AutoFocus.AF_OFF }

        flash.observe(this@Camera1) { this@Camera1.flash = it }

        jpegQuality.observe(this@Camera1) { updateCameraParams { jpegQuality = it } }
    }

    /**
     * Start camera with given [cameraIdInternal].
     * If [cameraIdInternal] is [Modes.DEFAULT_CAMERA_ID] then
     * camera is selected based on provided facing from [CameraConfiguration.facing]
     */
    override fun start(cameraId: String): Boolean {

        if (!cameraOpenCloseLock.tryAcquire()) return false

        when (cameraId) {
            Modes.DEFAULT_CAMERA_ID -> chooseCameraIdByFacing()
            else -> setCameraId(cameraId)
        }

        runCatching { openCamera() }
            .onFailure {
                cameraOpenCloseLock.release()
                listener.onCameraError(
                    CameraViewException("Unable to open camera.", it),
                    ErrorLevel.ErrorCritical
                )
                return false
            }

        if (preview.isReady) runCatching { setUpPreview() }
            .onFailure {
                cameraOpenCloseLock.release()
                listener.onCameraError(CameraViewException("Unable to setup preview.", it))
                return false
            }

        cameraOpenCloseLock.release()

        return startPreview()
    }

    private fun startPreview(): Boolean =
        runCatching {
            if (!previewStartStopLock.tryAcquire()) return@runCatching false
            if (config.isContinuousFrameModeEnabled) camera?.setPreviewCallback(previewCallback)
            camera?.startPreview()
            showingPreview = true
            return@runCatching true
        }
            .getOrElse {
                listener.onCameraError(CameraViewException("Unable to start preview.", it))
                return@getOrElse false
            }
            .also { previewStartStopLock.release() }

    private fun stopPreview(): Boolean =
        runCatching {
            if (!previewStartStopLock.tryAcquire()) return@runCatching false
            camera?.setPreviewCallback(null)
            camera?.stopPreview()
            return@runCatching true
        }
            .getOrElse {
                listener.onCameraError(CameraViewException("Unable to stop preview.", it))
                return@getOrElse false
            }
            .also { previewStartStopLock.release() }

    override fun stop() {
        if (!cameraOpenCloseLock.tryAcquire()) return
        super.stop()
        stopPreview()
        showingPreview = false
        camera?.release()
        camera = null
        listener.onCameraClosed()
        cameraOpenCloseLock.release()
    }

    override fun destroy() {
        cameraJob.cancel()
        super.destroy()
    }

    @Throws(IOException::class, RuntimeException::class, IllegalStateException::class)
    private fun setUpPreview() {

        val camera: Camera = camera ?: return

        if (preview.outputClass === SurfaceHolder::class.java)
            camera.setPreviewDisplay(preview.surfaceHolder)
        else preview.surfaceTexture?.let { camera.setPreviewTexture(it) }
            ?: throw IllegalStateException("Surface texture not initialized!")

        lifecycleRegistry.markState(Lifecycle.State.STARTED)
    }

    private fun updateCameraParams(func: Camera.Parameters.() -> Unit): Boolean =
        runCatching {
            camera?.parameters = camera?.parameters?.apply(func)
                ?: throw IllegalStateException("Camera not opened or already closed!")
            return@runCatching true
        }.getOrElse {
            listener.onCameraError(
                CameraViewException("Unable to update camera parameters.", it),
                ErrorLevel.Warning
            )
            return@getOrElse false
        }

    override fun setAspectRatio(ratio: AspectRatio) {
        if (isCameraOpened) adjustCameraParameters()
    }

    override fun takePicture() {
        if (!isCameraOpened) {
            listener.onCameraError(CameraViewException("Camera is not ready. Call start() before capture()."))
            return
        }
        try {
            if (autoFocus) {
                camera?.cancelAutoFocus()
                camera?.autoFocus { _, _ -> takePictureInternal() }
            } else {
                takePictureInternal()
            }
        } catch (e: RuntimeException) {
            listener.onCameraError(CameraViewException("Unable to capture picture.", e))
        }
    }

    @Throws(RuntimeException::class)
    private fun takePictureInternal() {

        if (!isPictureCaptureInProgress.compareAndSet(false, true)) return

        val outputFormat = when (config.outputFormat.value) {
            Modes.OutputFormat.YUV_420_888 -> ImageFormat.NV21
            Modes.OutputFormat.RGBA_8888 -> ImageFormat.RGB_565
            else -> ImageFormat.JPEG
        }

        camera?.parameters?.pictureFormat = outputFormat
        camera?.takePicture(
            Camera.ShutterCallback { launch(Dispatchers.Main) { preview.shutterView.show() } },
            null,
            null,
            pictureCallback
        )
    }

    override fun startVideoRecording(outputFile: File, videoConfig: VideoConfiguration) {

        if (!isCameraOpened || !preview.isReady) {
            throw IllegalStateException("Camera not started or already stopped")
        }

        camera?.unlock()

        runCatching {
            videoManager.setupMediaRecorder(
                camera ?: return,
                cameraIdInternal,
                preview.surface,
                outputFile,
                videoConfig,
                calcCameraRotation(deviceRotation),
                Size(preview.width, preview.height)
            ) { stopVideoRecording() }
            videoManager.startMediaRecorder()
            listener.onVideoRecordStarted()
        }.getOrElse {
            camera?.lock()
            throw it
        }
    }

    override fun pauseVideoRecording(): Boolean {
        listener.onCameraError(CameraViewException("Video pausing and resuming is only supported on API 24 and higher"))
        return false
    }

    override fun resumeVideoRecording(): Boolean {
        listener.onCameraError(CameraViewException("Video pausing and resuming is only supported on API 24 and higher"))
        return false
    }

    override fun stopVideoRecording(): Boolean = runCatching {
        videoManager.stopVideoRecording()
        return@runCatching true
    }
        .getOrElse {
            listener.onCameraError(CameraViewException("Unable to stop video recording.", it))
            return@getOrElse false
        }
        .also {
            listener.onVideoRecordStopped(it)
            camera?.lock()
            return it
        }

    /**
     * Returns a camera ID next to current [cameraIdInternal] for facing ([CameraConfiguration.facing]).
     *
     * If current camera ID has a different facing then what is set currently
     * then this method will return the first camera ID for set facing.
     *
     * Camera IDs are grouped by facing.
     */
    override fun getNextCameraId(): String {

        val sortedIds: SortedSet<String> = cameraIdsForFacing

        // For invalid `cameraId`, new index will be -1 + 1 = 0 ie. first index of the group
        val newIdIndex: Int = sortedIds.indexOf(cameraId) + 1

        return if (newIdIndex in 0 until sortedIds.size) sortedIds.elementAt(newIdIndex) else Modes.DEFAULT_CAMERA_ID
    }

    private fun Camera.CameraInfo.copyFrom(other: Camera.CameraInfo) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            canDisableShutterSound = other.canDisableShutterSound
        }
        facing = other.facing
        orientation = other.orientation
    }

    /** This rewrites [.cameraIdInternal] and [.cameraInfo]. */
    private fun chooseCameraIdByFacing() {

        (0 until Camera.getNumberOfCameras()).forEach { id ->
            val info = Camera.CameraInfo()
            runCatching { Camera.getCameraInfo(id, info) }
                .getOrElse { return@forEach }
            if (info.facing != config.facing.value) return@forEach
            cameraInfo.copyFrom(info)
            cameraIdInternal = id
            return
        }

        cameraIdInternal = INVALID_CAMERA_ID
    }

    /** This rewrites [.cameraIdInternal] and [.cameraInfo]. */
    @Throws(IllegalArgumentException::class)
    private fun setCameraId(id: String) {

        val intId: Int = id.toIntOrNull() ?: INVALID_CAMERA_ID

        runCatching { Camera.getCameraInfo(intId, cameraInfo) }
            .onFailure { throw IllegalArgumentException("Id must be an integer and a valid camera id.", it) }

        cameraIdInternal = intId
    }

    @Throws(RuntimeException::class)
    private fun openCamera() {

        // It is recommended to open camera on another thread
        // https://developer.android.com/training/camera/cameradirect.html#TaskOpenCamera
        camera = runBlocking(coroutineContext) { Camera.open(cameraIdInternal) }.apply {

            // Supported preview sizes
            previewSizes.clear()
            parameters.supportedPreviewSizes
                ?.forEach { previewSizes.add(it.width, it.height) }

            // Supported video sizes
            parameters.supportedVideoSizes
                ?.asSequence()
                ?.map { com.priyankvasa.android.cameraviewex.Size(it.width, it.height) }
                ?.let { videoManager.addVideoSizes(it) }

            // Supported picture sizes
            pictureSizes.clear()
            parameters.supportedPictureSizes
                ?.forEach { pictureSizes.add(it.width, it.height) }

            setDisplayOrientation(calcDisplayOrientation(deviceRotation))
        }
        adjustCameraParameters()
        listener.onCameraOpened()
    }

    private fun adjustCameraParameters() {

        if (!preview.isReady) return

        val previewSize: Size =
            previewSizes.chooseOptimalSize(
                config.continuousFrameSize.value,
                config.sensorAspectRatio
            )
                ?: run {
                    listener.onCameraError(
                        CameraViewException("No supported preview size available. This camera device (id $cameraId) is not supported."),
                        ErrorLevel.Error
                    )
                    return
                }

        val pictureSize: Size =
            pictureSizes.chooseOptimalSize(
                config.singleCaptureSize.value,
                config.sensorAspectRatio
            )
                ?: previewSize

        if (showingPreview) stopPreview()

        updateCameraParams {
            setPreviewSize(previewSize.width, previewSize.height)
            setPictureSize(pictureSize.width, pictureSize.height)
            setRotation(calcCameraRotation(deviceRotation))
            jpegQuality = config.jpegQuality.value
        }

        flash = config.flash.value

        if (autoFocus && !setAutoFocusInternal(autoFocus)) autoFocus = false

        if (showingPreview) startPreview()
    }

    /**
     * Calculate display orientation
     * https://developer.android.com/reference/android/hardware/Camera.html#setDisplayOrientation(int)
     *
     * This calculation is used for orienting the preview
     *
     * Note: This is not the same calculation as the camera rotation
     *
     * @param rotationDegrees Screen orientation in degrees
     * @return Number of degrees required to rotate preview
     */
    private fun calcDisplayOrientation(rotationDegrees: Int): Int =
        if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            (360 - ((cameraInfo.orientation + rotationDegrees) % 360)) % 360
        } else { // back-facing
            (cameraInfo.orientation - rotationDegrees + 360) % 360
        }

    /**
     * Calculate camera rotation
     *
     * This calculation is applied to the output JPEG either via Exif Orientation tag
     * or by actually transforming the bitmap. (Determined by vendor camera API implementation)
     *
     * Note: This is not the same calculation as the display orientation
     *
     * @param screenOrientationDegrees Screen orientation in degrees
     * @return Number of degrees to rotate image in order for it to view correctly.
     */
    private fun calcCameraRotation(screenOrientationDegrees: Int): Int = when (cameraInfo.facing) {

        Camera.CameraInfo.CAMERA_FACING_FRONT ->
            (cameraInfo.orientation + screenOrientationDegrees) % 360

        else -> {  // back-facing
            val landscapeFlip: Int = if (screenOrientationDegrees % 180 == 90) 180 else 0
            (cameraInfo.orientation + screenOrientationDegrees + landscapeFlip) % 360
        }
    }

    /**
     * @return `true` if focus mode was set correctly, `false` otherwise.
     */
    private fun setAutoFocusInternal(autoFocus: Boolean): Boolean {

        val modes: MutableList<String> = camera?.parameters?.supportedFocusModes ?: return false

        val (focusMode: String, result: Boolean) = when {
            autoFocus &&
                config.isVideoCaptureModeEnabled &&
                modes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO) ->
                Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO to true
            autoFocus &&
                config.isSingleCaptureModeEnabled &&
                modes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE) ->
                Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE to true
            autoFocus && modes.contains(Camera.Parameters.FOCUS_MODE_EDOF) ->
                Camera.Parameters.FOCUS_MODE_FIXED to true
            modes.contains(Camera.Parameters.FOCUS_MODE_FIXED) ->
                Camera.Parameters.FOCUS_MODE_FIXED to !autoFocus
            modes.contains(Camera.Parameters.FOCUS_MODE_INFINITY) ->
                Camera.Parameters.FOCUS_MODE_INFINITY to !autoFocus
            else -> return false
        }

        return result && updateCameraParams { this.focusMode = focusMode }
    }

    companion object {

        private const val INVALID_CAMERA_ID = -1

        private val FLASH_MODES: SparseArrayCompat<String> = SparseArrayCompat<String>()
            .apply {
                put(Modes.Flash.FLASH_OFF, Camera.Parameters.FLASH_MODE_OFF)
                put(Modes.Flash.FLASH_ON, Camera.Parameters.FLASH_MODE_ON)
                put(Modes.Flash.FLASH_TORCH, Camera.Parameters.FLASH_MODE_TORCH)
                put(Modes.Flash.FLASH_AUTO, Camera.Parameters.FLASH_MODE_AUTO)
                put(Modes.Flash.FLASH_RED_EYE, Camera.Parameters.FLASH_MODE_RED_EYE)
            }
    }
}