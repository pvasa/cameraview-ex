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
import kotlinx.coroutines.Job
import java.io.File
import java.util.SortedSet
import java.util.concurrent.atomic.AtomicBoolean

internal class Camera1(
    override val listener: CameraInterface.Listener,
    override val preview: PreviewImpl,
    override val config: CameraConfiguration,
    override val cameraJob: Job
) : CameraInterface {

    private val lifecycleRegistry: LifecycleRegistry =
        LifecycleRegistry(this).also { it.markState(Lifecycle.State.CREATED) }

    private var cameraId: Int = Modes.Facing.FACING_BACK

    private val isPictureCaptureInProgress = AtomicBoolean(false)

    var camera: Camera? = null

    private val previewCallback = Camera.PreviewCallback { data, camera ->
        if (!isCameraOpened) return@PreviewCallback
        val image = LegacyImage(
            data,
            camera.parameters.previewSize.width,
            camera.parameters.previewSize.height,
            camera.parameters.previewFormat
        )
        listener.onLegacyPreviewFrame(image)
    }

    private val cameraInfo = Camera.CameraInfo()

    private val previewSizes = SizeMap()

    private val pictureSizes = SizeMap()

    private var showingPreview: Boolean = false

    override var jpegQuality: Int = Modes.DEFAULT_JPEG_QUALITY

    override var deviceRotation: Int = 0
        set(value) {
            if (field == value ||
                (isCameraOpened && !updateCameraParams { setRotation(calcCameraRotation(value)) })) return
            field = value
        }

    override val isActive: Boolean get() = cameraJob.isActive

    override val isCameraOpened: Boolean get() = camera != null

    override var isVideoRecording: Boolean = false

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
            if (setAutoFocusInternal(value)) field = value
        }

    override val maxDigitalZoom: Float = 1f

    private var flash: Int = Modes.DEFAULT_FLASH
        set(value) {
            if (isCameraOpened) updateCameraParams {
                val modes = camera?.parameters?.supportedFlashModes
                val mode = FLASH_MODES.get(value)
                if (modes?.contains(mode) == true) {
                    flashMode = mode
                    field = value
                }
                val currentMode = FLASH_MODES.get(field)
                if (modes == null || !modes.contains(currentMode)) {
                    flashMode = Camera.Parameters.FLASH_MODE_OFF
                    field = Modes.Flash.FLASH_OFF
                }
            } else field = value
        }

    private val previewSurfaceChangedListener: () -> Unit = {
        setUpPreview()
        adjustCameraParameters()
    }

    init {
        preview.surfaceChangeListener = previewSurfaceChangedListener
        addObservers()
    }

    override fun getLifecycle(): Lifecycle = lifecycleRegistry

    private fun addObservers() = config.run {
        facing.observe(this@Camera1) {
            if (isCameraOpened) {
                stop()
                start()
            }
        }
        cameraMode.observe(this@Camera1) {
            if (isCameraOpened) {
                stop()
                start()
            }
        }
        autoFocus.observe(this@Camera1) { this@Camera1.autoFocus = it != Modes.AutoFocus.AF_OFF }
        flash.observe(this@Camera1) { this@Camera1.flash = it }
    }

    override fun start(): Boolean {
        chooseCamera()
        openCamera()
        if (preview.isReady) setUpPreview()
        return startPreview()
    }

    private fun startPreview(): Boolean = runCatching {
        if (config.isContinuousFrameModeEnabled) camera?.setPreviewCallback(previewCallback)
        camera?.startPreview()
        showingPreview = true
        true
    }.getOrElse {
        listener.onCameraError(CameraViewException(cause = it))
        false
    }

    private fun stopPreview(): Boolean = runCatching {
        camera?.setPreviewCallback(null)
        camera?.stopPreview()
        true
    }.getOrElse {
        listener.onCameraError(CameraViewException(cause = it))
        false
    }

    override fun stop() {
        super.stop()
        stopPreview()
        showingPreview = false
        camera?.release()
        camera = null
        listener.onCameraClosed()
    }

    private fun setUpPreview() {

        camera.runCatching {
            this ?: return
            if (preview.outputClass === SurfaceHolder::class.java) setPreviewDisplay(preview.surfaceHolder)
            else preview.surfaceTexture?.let { setPreviewTexture(it) }
                ?: throw IllegalStateException("Surface texture not initialized!")
            lifecycleRegistry.markState(Lifecycle.State.STARTED)
        }
            .onFailure { listener.onCameraError(CameraViewException(cause = it)) }
    }

    private fun updateCameraParams(func: Camera.Parameters.() -> Unit): Boolean =
        runCatching {
            camera?.parameters = camera?.parameters?.apply(func)
            true
        }.getOrElse {
            listener.onCameraError(CameraViewException(cause = it))
            false
        }

    override fun setAspectRatio(ratio: AspectRatio): Boolean {
        // Handle this later when camera is opened
        if (!isCameraOpened) return true
        val sizes = previewSizes.sizes(ratio)
        if (sizes.isEmpty()) {
            listener.onCameraError(UnsupportedOperationException("$ratio is not supported"))
            return false
        }
        adjustCameraParameters()
        return true
    }

    override fun takePicture() {
        if (!isCameraOpened) {
            listener.onCameraError(IllegalStateException("Camera is not ready. Call start() before capture()."))
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
            listener.onCameraError(e)
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

    override fun startVideoRecording(outputFile: File, videoConfig: VideoConfiguration) =
        listener.onCameraError(UnsupportedOperationException("Video recording is not supported on API < 21 (ie. camera1 implementation.)"))

    override fun pauseVideoRecording(): Boolean = false

    override fun resumeVideoRecording(): Boolean = false

    override fun stopVideoRecording(): Boolean = false

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

    private fun openCamera() {
        try {
            camera = Camera.open(cameraId)
            // Supported preview sizes
            previewSizes.clear()
            camera?.parameters?.supportedPreviewSizes
                ?.forEach { size -> previewSizes.add(Size(size.width, size.height)) }
            // Supported picture sizes;
            pictureSizes.clear()
            camera?.parameters?.supportedPictureSizes
                ?.forEach { size -> pictureSizes.add(Size(size.width, size.height)) }
            adjustCameraParameters()
            camera?.setDisplayOrientation(calcDisplayOrientation(deviceRotation))
            listener.onCameraOpened()
        } catch (e: RuntimeException) {
            listener.onCameraError(e)
        }
    }

    private fun chooseAspectRatio(): AspectRatio {
        var r: AspectRatio = Modes.DEFAULT_ASPECT_RATIO
        previewSizes.ratios().forEach { ratio ->
            r = ratio
            if (ratio == Modes.DEFAULT_ASPECT_RATIO) return ratio
        }
        return r
    }

    private fun adjustCameraParameters() {

        val sizes = previewSizes.sizes(config.aspectRatio.value)

        if (sizes.isEmpty()) { // Not supported
            config.aspectRatio.value = chooseAspectRatio()
            return
        }

        val size = chooseOptimalSize(sizes)

        // Always re-apply camera parameters
        // Largest picture size in this ratio
        val pictureSize = pictureSizes.sizes(config.aspectRatio.value).last()

        if (showingPreview) stopPreview()

        updateCameraParams {
            setPreviewSize(size.width, size.height)
            setPictureSize(pictureSize.width, pictureSize.height)
            setRotation(calcCameraRotation(deviceRotation))
        }

        flash = config.flash.value

        setAutoFocusInternal(autoFocus)

        if (showingPreview) startPreview()
    }

    private fun chooseOptimalSize(sizes: SortedSet<Size>): Size {
        if (!preview.isReady) return sizes.first() // Not yet laid out. Return the smallest size.
        val desiredWidth: Int
        val desiredHeight: Int
        val surfaceWidth = preview.width
        val surfaceHeight = preview.height
        if (deviceRotation % 180 == 90) {
            desiredWidth = surfaceHeight
            desiredHeight = surfaceWidth
        } else {
            desiredWidth = surfaceWidth
            desiredHeight = surfaceHeight
        }
        return sizes
            .firstOrNull { desiredWidth <= it.width && desiredHeight <= it.height }
            ?: sizes.last()
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
            val landscapeFlip = if (screenOrientationDegrees % 180 == 90) 180 else 0
            (cameraInfo.orientation + screenOrientationDegrees + landscapeFlip) % 360
        }
    }

    /**
     * @return `true` if [Camera.Parameters] was modified for [camera], `false` otherwise.
     */
    private fun setAutoFocusInternal(autoFocus: Boolean): Boolean = if (isCameraOpened) updateCameraParams {

        val modes = supportedFocusModes ?: return@updateCameraParams

        focusMode = when {
            autoFocus &&
                config.isVideoCaptureModeEnabled &&
                modes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO) ->
                Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO
            autoFocus &&
                config.isSingleCaptureModeEnabled &&
                modes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE) ->
                Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE
            modes.contains(Camera.Parameters.FOCUS_MODE_FIXED) ->
                Camera.Parameters.FOCUS_MODE_FIXED
            modes.contains(Camera.Parameters.FOCUS_MODE_INFINITY) ->
                Camera.Parameters.FOCUS_MODE_INFINITY
            else -> modes[0]
        }
    } else false

    companion object {

        private const val INVALID_CAMERA_ID = -1

        private val FLASH_MODES = SparseArrayCompat<String>()

        init {
            FLASH_MODES.put(Modes.Flash.FLASH_OFF, Camera.Parameters.FLASH_MODE_OFF)
            FLASH_MODES.put(Modes.Flash.FLASH_ON, Camera.Parameters.FLASH_MODE_ON)
            FLASH_MODES.put(Modes.Flash.FLASH_TORCH, Camera.Parameters.FLASH_MODE_TORCH)
            FLASH_MODES.put(Modes.Flash.FLASH_AUTO, Camera.Parameters.FLASH_MODE_AUTO)
            FLASH_MODES.put(Modes.Flash.FLASH_RED_EYE, Camera.Parameters.FLASH_MODE_RED_EYE)
        }
    }
}