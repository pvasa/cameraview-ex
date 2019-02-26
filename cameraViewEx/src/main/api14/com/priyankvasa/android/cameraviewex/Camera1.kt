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
import android.support.v4.util.SparseArrayCompat
import android.view.SurfaceHolder
import com.priyankvasa.android.cameraviewex.extension.chooseOptimalPreviewSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.SortedSet
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.CoroutineContext

internal class Camera1(
    private val listener: CameraInterface.Listener,
    private val preview: PreviewImpl,
    private val config: CameraConfiguration,
    private val cameraJob: Job
) : CameraInterface {

    override val coroutineContext: CoroutineContext get() = Dispatchers.Default + cameraJob

    private val lifecycleRegistry: LifecycleRegistry =
        LifecycleRegistry(this).also { it.markState(Lifecycle.State.CREATED) }

    private var cameraId: Int = Modes.Facing.FACING_BACK

    private val isPictureCaptureInProgress: AtomicBoolean by lazy { AtomicBoolean(false) }

    var camera: Camera? = null

    private val videoManager: VideoManager
        by lazy { VideoManager { listener.onCameraError(CameraViewException(it), ErrorLevel.Warning) } }

    private val previewCallback: Camera.PreviewCallback by lazy {
        Camera.PreviewCallback { data, camera ->
            if (!isCameraOpened) return@PreviewCallback
            val image = LegacyImage(
                data,
                camera.parameters.previewSize.width,
                camera.parameters.previewSize.height,
                camera.parameters.previewFormat
            )
            listener.onLegacyPreviewFrame(image)
        }
    }

    private val cameraInfo: Camera.CameraInfo by lazy { Camera.CameraInfo() }

    private val previewSizes: SizeMap by lazy { SizeMap() }

    private val pictureSizes: SizeMap by lazy { SizeMap() }

    private var showingPreview: Boolean = false

    override var deviceRotation: Int = 0
        set(value) {
            field = value
            if (isCameraOpened) updateCameraParams { setRotation(calcCameraRotation(value)) }
        }

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

    private suspend fun previewSurfaceChangedAction() {
        runCatching { setUpPreview() }
            .onFailure {
                listener.onCameraError(CameraViewException("Unable to setup preview.", it))
                return
            }
        adjustCameraParameters()
    }

    init {
        preview.surfaceChangeListener = { launch { previewSurfaceChangedAction() } }
        addObservers()
    }

    override fun getLifecycle(): Lifecycle = lifecycleRegistry

    private fun addObservers(): Unit = config.run {
        facing.observe(this@Camera1) {
            if (isCameraOpened) runBlocking(coroutineContext) {
                stop()
                start()
            }
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

    override suspend fun start(): Boolean {
        chooseCamera()
        runCatching { openCamera() }
            .onFailure {
                listener.onCameraError(
                    CameraViewException("Unable to open camera.", it),
                    ErrorLevel.ErrorCritical
                )
                return false
            }
        if (preview.isReady) runCatching { setUpPreview() }
            .onFailure {
                listener.onCameraError(CameraViewException("Unable to setup preview.", it))
                return false
            }
        return startPreview()
    }

    private fun startPreview(): Boolean = runCatching {
        if (config.isContinuousFrameModeEnabled) camera?.setPreviewCallback(previewCallback)
        camera?.startPreview()
        showingPreview = true
        true
    }.getOrElse {
        listener.onCameraError(CameraViewException("Unable to start preview.", it))
        false
    }

    private fun stopPreview(): Boolean = runCatching {
        camera?.setPreviewCallback(null)
        camera?.stopPreview()
        true
    }.getOrElse {
        listener.onCameraError(CameraViewException("Unable to stop preview.", it))
        false
    }

    override suspend fun stop() {
        super.stop()
        stopPreview()
        showingPreview = false
        camera?.release()
        camera = null
        listener.onCameraClosed()
    }

    override suspend fun destroy() {
        super.destroy()
        cameraJob.cancel()
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

    override suspend fun setAspectRatio(ratio: AspectRatio): Boolean {
        // Handle this later when camera is opened
        if (!isCameraOpened) return true
        val sizes: SortedSet<Size> = previewSizes.sizes(ratio)
        if (sizes.isEmpty()) {
            listener.onCameraError(CameraViewException("Ratio $ratio is not supported"))
            return false
        }
        adjustCameraParameters()
        return true
    }

    override suspend fun takePicture() {
        if (!isCameraOpened) {
            listener.onCameraError(CameraViewException("Camera is not ready. Call start() before capture()."))
            return
        }
        try {
            if (autoFocus) {
                camera?.cancelAutoFocus()
                camera?.autoFocus { _, _ -> launch { takePictureInternal() } }
            } else {
                takePictureInternal()
            }
        } catch (e: RuntimeException) {
            listener.onCameraError(CameraViewException("Unable to capture picture.", e))
        }
    }

    @Throws(RuntimeException::class)
    private fun takePictureInternal() {
        val outputFormat = when (config.outputFormat.value) {
            Modes.OutputFormat.YUV_420_888 -> ImageFormat.NV21
            Modes.OutputFormat.RGBA_8888 -> ImageFormat.RGB_565
            else -> ImageFormat.JPEG
        }
        if (isPictureCaptureInProgress.compareAndSet(false, true)) {
            camera?.parameters?.pictureFormat = outputFormat
            camera?.takePicture(
                Camera.ShutterCallback { preview.shutterView.show() },
                null,
                null,
                Camera.PictureCallback { data, camera ->
                    isPictureCaptureInProgress.set(false)
                    listener.onPictureTaken(data)
                    camera?.cancelAutoFocus()
                    startPreview()
                }
            )
        }
    }

    override suspend fun startVideoRecording(outputFile: File, videoConfig: VideoConfiguration) {

        if (!isCameraOpened || !preview.isReady) {
            listener.onCameraError(CameraViewException("Camera not started or already stopped"))
            return
        }

        camera?.unlock()

        runCatching {
            videoManager.setupMediaRecorder(
                camera ?: return,
                cameraId,
                preview.surface,
                outputFile,
                videoConfig,
                config.aspectRatio.value,
                calcCameraRotation(deviceRotation)
            ) { launch { stopVideoRecording() } }
            videoManager.startMediaRecorder()
            listener.onVideoRecordStarted()
        }.onFailure {
            listener.onCameraError(CameraViewException("Unable to start video recording", it))
            camera?.lock()
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

    override suspend fun stopVideoRecording(): Boolean = runCatching {
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

    /** This rewrites [.cameraId] and [.cameraInfo]. */
    private fun chooseCamera() {

        (0 until Camera.getNumberOfCameras()).forEach { id ->
            Camera.getCameraInfo(id, cameraInfo)
            if (cameraInfo.facing != config.facing.value) return@forEach
            cameraId = id
            return
        }

        cameraId = INVALID_CAMERA_ID
    }

    @Throws(RuntimeException::class)
    private suspend fun openCamera() {

        camera = Camera.open(cameraId).apply {
            // Supported preview sizes
            previewSizes.clear()
            parameters.supportedPreviewSizes
                ?.forEach { previewSizes.add(it.width, it.height) }
            // Supported picture sizes;
            pictureSizes.clear()
            parameters.supportedPictureSizes
                ?.forEach { pictureSizes.add(it.width, it.height) }
            // Supported video sizes;
            parameters.supportedVideoSizes
                ?.asSequence()
                ?.map { com.priyankvasa.android.cameraviewex.Size(it.width, it.height) }
                ?.let { videoManager.addVideoSizes(it) }
            setDisplayOrientation(calcDisplayOrientation(deviceRotation))
        }
        adjustCameraParameters()
        listener.onCameraOpened()
    }

    private suspend fun chooseAspectRatio(): AspectRatio = withContext(coroutineContext) {
        var r: AspectRatio = Modes.DEFAULT_ASPECT_RATIO
        previewSizes.ratios().forEach { ratio: AspectRatio ->
            r = ratio
            if (ratio == Modes.DEFAULT_ASPECT_RATIO) return@withContext ratio
        }
        return@withContext r
    }

    private suspend fun adjustCameraParameters() {

        if (!preview.isReady) return

        val sizes: SortedSet<Size> = previewSizes.sizes(config.aspectRatio.value)

        if (sizes.isEmpty()) { // Not supported
            config.aspectRatio.value = chooseAspectRatio()
            return
        }

        val size: Size = sizes.chooseOptimalPreviewSize(preview.width, preview.height)

        // Always re-apply camera parameters
        // Largest picture size in this ratio
        val pictureSize: Size = pictureSizes.sizes(config.aspectRatio.value)
            .takeIf { it.isNotEmpty() }
            ?.last()
            ?: size

        if (showingPreview) stopPreview()

        updateCameraParams {
            setPreviewSize(size.width, size.height)
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