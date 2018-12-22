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

@file:Suppress("DEPRECATION")

package com.priyankvasa.android.cameraviewex

import android.annotation.SuppressLint
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.Camera
import android.os.Build
import android.support.v4.util.SparseArrayCompat
import android.view.SurfaceHolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.File
import java.util.SortedSet
import java.util.concurrent.atomic.AtomicBoolean

internal class Camera1(
        override val listener: CameraInterface.Listener,
        override val preview: PreviewImpl
) : CameraInterface {

    private var cameraId: Int = Modes.Facing.FACING_BACK

    private val isPictureCaptureInProgress = AtomicBoolean(false)

    var camera: Camera? = null

    private var cameraParameters: Camera.Parameters? = null

    private val cameraInfo = Camera.CameraInfo()

    private val previewSizes = SizeMap()

    private val pictureSizes = SizeMap()

    override var aspectRatio: AspectRatio = Modes.DEFAULT_ASPECT_RATIO
        set(value) {
            if (field == value) return
            field = value
            if (isCameraOpened) {
                stop()
                start()
            }
        }

    private var showingPreview: Boolean = false

    private var internalOutputFormat = ImageFormat.JPEG

    override var outputFormat: Int = Modes.DEFAULT_OUTPUT_FORMAT
        set(value) {
            field = value
            internalOutputFormat = when (value) {
                Modes.OutputFormat.JPEG -> ImageFormat.JPEG
                Modes.OutputFormat.YUV_420_888,
                Modes.OutputFormat.RGBA_8888 -> ImageFormat.NV21
                else -> ImageFormat.UNKNOWN
            }
        }

    override var jpegQuality: Int = Modes.DEFAULT_JPEG_QUALITY

    override var facing: Int = Modes.DEFAULT_FACING
        set(value) {
            if (field == value) return
            field = value
            if (isCameraOpened) {
                stop()
                start()
            }
        }

    override var displayOrientation: Int = 0
        set(value) {
            if (field == value) return
            field = value
            if (isCameraOpened) {
                try {
                    cameraParameters?.setRotation(calcCameraRotation(value))
                    camera?.parameters = cameraParameters
                    val needsToStopPreview = showingPreview && Build.VERSION.SDK_INT < 14
                    if (needsToStopPreview) camera?.stopPreview()
                    camera?.setDisplayOrientation(calcDisplayOrientation(value))
                    if (needsToStopPreview) camera?.startPreview()
                } catch (e: Exception) {
                    listener.onCameraError(e)
                }
            }
        }

    override val isCameraOpened: Boolean get() = camera != null

    override val supportedAspectRatios: Set<AspectRatio>
        get() {
            previewSizes.ratios()
                    .asSequence()
                    .filter { pictureSizes.sizes(it).isEmpty() }
                    .forEach { previewSizes.remove(it) }
            return previewSizes.ratios()
        }

    override var cameraMode: Int = Modes.DEFAULT_CAMERA_MODE

    override var autoFocus: Boolean = Modes.DEFAULT_AUTO_FOCUS
        get() {
            if (!isCameraOpened) return field
            val focusMode = cameraParameters?.focusMode
            return focusMode != null && focusMode.contains("continuous")
        }
        set(value) {
            if (field == value) return
            if (setAutoFocusInternal(value)) {
                field = value
                try {
                    camera?.parameters = cameraParameters
                } catch (e: RuntimeException) {
                    listener.onCameraError(e)
                }
            }
        }

    override var touchToFocus: Boolean = Modes.DEFAULT_TOUCH_TO_FOCUS
        get() = if (!isCameraOpened) field else false // TODO("Check cameraParameters")
        set(value) {
            if (field == value) return
            // TODO("set internal")
        }

    override var pinchToZoom: Boolean = Modes.DEFAULT_PINCH_TO_ZOOM

    override var currentDigitalZoom: Float = 1f

    override val maxDigitalZoom: Float = 1f

    override var awb: Int = Modes.DEFAULT_AWB
        get() = if (!isCameraOpened) field else Modes.DEFAULT_AWB // TODO("Check cameraParameters")
        set(value) {
            if (field == value) return
            // TODO("set internal")
        }

    override var flash: Int = Modes.DEFAULT_FLASH
        set(value) {
            if (field == value) return
            if (isCameraOpened) {
                try {
                    val modes = cameraParameters?.supportedFlashModes
                    val mode = FLASH_MODES.get(flash)
                    if (modes?.contains(mode) == true) {
                        cameraParameters?.flashMode = mode
                        field = value
                        camera?.parameters = cameraParameters
                    }
                    val currentMode = FLASH_MODES.get(this.flash)
                    if (modes == null || !modes.contains(currentMode)) {
                        cameraParameters?.flashMode = Camera.Parameters.FLASH_MODE_OFF
                        field = Modes.Flash.FLASH_OFF
                        camera?.parameters = cameraParameters
                    }
                } catch (e: RuntimeException) {
                    listener.onCameraError(e)
                }
            } else field = value
        }

    override var opticalStabilization: Boolean = Modes.DEFAULT_OPTICAL_STABILIZATION
        get() = if (!isCameraOpened) field else Modes.DEFAULT_OPTICAL_STABILIZATION // TODO("Check cameraParameters")
        set(value) {
            if (field == value) return
            // TODO("set internal")
        }

    override var noiseReduction: Int = Modes.DEFAULT_NOISE_REDUCTION
        get() = if (!isCameraOpened) field else Modes.DEFAULT_NOISE_REDUCTION // TODO("Check cameraParameters")
        set(value) {
            if (field == value) return
            // TODO("set internal")
        }

    override var zsl: Boolean = Modes.DEFAULT_ZSL
        set(value) {
            if (field == value) return
            // TODO("set internal")
        }

    init {
        preview.surfaceChangeListener = ::onPreviewSurfaceChanged
    }

    private fun onPreviewSurfaceChanged() {
        setUpPreview()
        adjustCameraParameters()
    }

    override fun start(): Boolean {
        chooseCamera()
        openCamera()
        if (preview.isReady) setUpPreview()
        showingPreview = true
        return try {
            camera?.startPreview()
            true
        } catch (e: RuntimeException) {
            listener.onCameraError(e)
            false
        }
    }

    override fun stop() {
        runCatching { camera?.stopPreview() }.onFailure { listener.onCameraError(it as Exception) }
        showingPreview = false
        releaseCamera()
    }

    // Suppresses Camera#setPreviewTexture
    @SuppressLint("NewApi")
    fun setUpPreview() {
        try {
            if (preview.outputClass === SurfaceHolder::class.java) {
                val needsToStopPreview = showingPreview && Build.VERSION.SDK_INT < 14
                if (needsToStopPreview) {
                    camera?.stopPreview()
                }
                camera?.setPreviewDisplay(preview.surfaceHolder)
                if (needsToStopPreview) {
                    camera?.startPreview()
                }
            } else {
                camera?.setPreviewTexture(preview.surfaceTexture as SurfaceTexture)
            }
        } catch (e: Exception) {
            listener.onCameraError(e)
        }
    }

    override fun setAspectRatio(ratio: AspectRatio): Boolean {
        if (!isCameraOpened) {
            // Handle this later when camera is opened
            aspectRatio = ratio
            return true
        } else if (aspectRatio != ratio) {
            val sizes = previewSizes.sizes(ratio)
            if (sizes.isEmpty()) {
                listener.onCameraError(UnsupportedOperationException("$ratio is not supported"))
            } else {
                aspectRatio = ratio
                adjustCameraParameters()
                return true
            }
        }
        return false
    }

    override fun takePicture() {
        if (!isCameraOpened) {
            listener.onCameraError(IllegalStateException("Camera is not ready. Call start() before capture()."))
        }
        try {
            if (this.autoFocus) {
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
        if (!isPictureCaptureInProgress.getAndSet(true)) {
            camera?.takePicture(null, null, null, Camera.PictureCallback { data, camera ->
                isPictureCaptureInProgress.set(false)
                listener.onPictureTaken(data)
                camera?.cancelAutoFocus()
                camera?.startPreview()
            })
        }
    }

    override fun startVideoRecording(outputFile: File, config: VideoConfiguration) {
    }

    override fun pauseVideoRecording(): Boolean = false

    override fun resumeVideoRecording(): Boolean = false

    override fun stopVideoRecording(): Boolean = false

    /**
     * This rewrites [.cameraId] and [.cameraInfo].
     */
    private fun chooseCamera() {
        var i = 0
        val count = Camera.getNumberOfCameras()
        while (i < count) {
            Camera.getCameraInfo(i, cameraInfo)
            if (cameraInfo.facing == facing) {
                cameraId = i
                return
            }
            i++
        }
        cameraId = INVALID_CAMERA_ID
    }

    private fun openCamera() {
        try {
            releaseCamera()
            camera = Camera.open(cameraId)
            cameraParameters = camera?.parameters
            // Supported preview sizes
            previewSizes.clear()
            cameraParameters?.supportedPreviewSizes?.forEach { size ->
                previewSizes.add(Size(size.width, size.height))
            }
            // Supported picture sizes;
            pictureSizes.clear()
            cameraParameters?.supportedPictureSizes?.forEach { size ->
                pictureSizes.add(Size(size.width, size.height))
            }
            adjustCameraParameters()
            camera?.setDisplayOrientation(calcDisplayOrientation(displayOrientation))
            GlobalScope.launch(Dispatchers.Main) { listener.onCameraOpened() }
        } catch (e: RuntimeException) {
            listener.onCameraError(e)
        }
    }

    private fun chooseAspectRatio(): AspectRatio {
        var r: AspectRatio = Modes.DEFAULT_ASPECT_RATIO
        for (ratio in previewSizes.ratios()) {
            r = ratio
            if (ratio == Modes.DEFAULT_ASPECT_RATIO) {
                return ratio
            }
        }
        return r
    }

    fun adjustCameraParameters() {
        var sizes = previewSizes.sizes(aspectRatio)
        if (sizes.isEmpty()) { // Not supported
            aspectRatio = chooseAspectRatio()
            sizes = previewSizes.sizes(aspectRatio)
        }
        val size = chooseOptimalSize(sizes)

        // Always re-apply camera parameters
        // Largest picture size in this ratio
        val pictureSize = pictureSizes.sizes(aspectRatio).last()
        if (showingPreview) camera?.stopPreview()
        cameraParameters?.apply {
            setPreviewSize(size.width, size.height)
            setPictureSize(pictureSize.width, pictureSize.height)
            setRotation(calcCameraRotation(displayOrientation))
        }?.also { camera?.parameters = it }
        setAutoFocusInternal(autoFocus)
        setFlashInternal(flash)
        try {
            if (showingPreview) camera?.startPreview()

        } catch (e: RuntimeException) {
            listener.onCameraError(e)
        }
    }

    private fun chooseOptimalSize(sizes: SortedSet<Size>): Size {
        if (!preview.isReady) { // Not yet laid out
            return sizes.first() // Return the smallest size
        }
        val desiredWidth: Int
        val desiredHeight: Int
        val surfaceWidth = preview.width
        val surfaceHeight = preview.height
        if (isLandscape(displayOrientation)) {
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

    private fun releaseCamera() {
        camera?.release()
        camera = null
        GlobalScope.launch(Dispatchers.Main) { listener.onCameraClosed() }
    }

    /**
     * Calculate display orientation
     * https://developer.android.com/reference/android/hardware/Camera.html#setDisplayOrientation(int)
     *
     *
     * This calculation is used for orienting the preview
     *
     *
     * Note: This is not the same calculation as the camera rotation
     *
     * @param screenOrientationDegrees Screen orientation in degrees
     * @return Number of degrees required to rotate preview
     */
    private fun calcDisplayOrientation(screenOrientationDegrees: Int): Int {
        return if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            (360 - (cameraInfo.orientation + screenOrientationDegrees) % 360) % 360
        } else {  // back-facing
            (cameraInfo.orientation - screenOrientationDegrees + 360) % 360
        }
    }

    /**
     * Calculate camera rotation
     *
     *
     * This calculation is applied to the output JPEG either via Exif Orientation tag
     * or by actually transforming the bitmap. (Determined by vendor camera API implementation)
     *
     *
     * Note: This is not the same calculation as the display orientation
     *
     * @param screenOrientationDegrees Screen orientation in degrees
     * @return Number of degrees to rotate image in order for it to view correctly.
     */
    private fun calcCameraRotation(screenOrientationDegrees: Int): Int {
        return when (cameraInfo.facing) {
            Camera.CameraInfo.CAMERA_FACING_FRONT ->
                (cameraInfo.orientation + screenOrientationDegrees) % 360
            else -> {  // back-facing
                val landscapeFlip = if (isLandscape(screenOrientationDegrees)) 180 else 0
                (cameraInfo.orientation + screenOrientationDegrees + landscapeFlip) % 360
            }
        }
    }

    /**
     * Test if the supplied orientation is in landscape.
     *
     * @param orientationDegrees Orientation in degrees (0,90,180,270)
     * @return True if in landscape, false if portrait
     */
    private fun isLandscape(orientationDegrees: Int): Boolean {
        return orientationDegrees == Modes.LANDSCAPE_90 || orientationDegrees == Modes.LANDSCAPE_270
    }

    /**
     * @return `true` if [.cameraParameters] was modified.
     */
    private fun setAutoFocusInternal(autoFocus: Boolean): Boolean = isCameraOpened.also {

        if (!it) return@also

        val modes = cameraParameters?.supportedFocusModes

        cameraParameters?.focusMode = when {
            autoFocus && modes?.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE) == true ->
                Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE
            modes?.contains(Camera.Parameters.FOCUS_MODE_FIXED) == true ->
                Camera.Parameters.FOCUS_MODE_FIXED
            modes?.contains(Camera.Parameters.FOCUS_MODE_INFINITY) == true ->
                Camera.Parameters.FOCUS_MODE_INFINITY
            else -> modes?.get(0)
        }
    }

    private fun setFlashInternal(flash: Int) {
        this.flash = flash
    }

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