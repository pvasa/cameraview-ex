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

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.hardware.camera2.params.StreamConfigurationMap
import android.media.ImageReader
import android.util.SparseIntArray
import com.google.android.cameraview.Modes.Flash.FLASH_AUTO
import com.google.android.cameraview.Modes.Flash.FLASH_OFF
import com.google.android.cameraview.Modes.Flash.FLASH_ON
import com.google.android.cameraview.Modes.Flash.FLASH_RED_EYE
import com.google.android.cameraview.Modes.Flash.FLASH_TORCH
import com.google.android.cameraview.Modes.NoiseReduction.NOISE_REDUCTION_HIGH_QUALITY
import timber.log.Timber
import java.util.Arrays

@TargetApi(21)
internal open class Camera2(
        callback: CameraViewImpl.Callback?,
        preview: PreviewImpl,
        context: Context
) : CameraViewImpl(callback, preview) {

    init {
        preview.setCallback(object : PreviewImpl.Callback {
            override fun onSurfaceChanged() {
                startCaptureSession()
            }
        })
    }

    private val cameraManager: CameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager

    private val cameraDeviceCallback = object : CameraDevice.StateCallback() {

        override fun onOpened(camera: CameraDevice) {
            this@Camera2.camera = camera
            this@Camera2.callback?.onCameraOpened()
            startCaptureSession()
        }

        override fun onClosed(camera: CameraDevice) {
            this@Camera2.callback?.onCameraClosed()
        }

        override fun onDisconnected(camera: CameraDevice) {
            this@Camera2.camera = null
        }

        override fun onError(camera: CameraDevice, error: Int) {
            Timber.e("onError: ${camera.id} ($error)")
            this@Camera2.camera = null
        }
    }

    private val sessionCallback = object : CameraCaptureSession.StateCallback() {

        override fun onConfigured(session: CameraCaptureSession) {
            if (camera == null) return
            captureSession = session
            updateModes()
            try {
                captureSession?.setRepeatingRequest(previewRequestBuilder?.build(), captureCallback, null)
            } catch (e: CameraAccessException) {
                Timber.e(e, "Failed to start camera preview because it couldn't access camera")
            } catch (e: IllegalStateException) {
                Timber.e(e, "Failed to start camera preview.")
            }
        }

        override fun onConfigureFailed(session: CameraCaptureSession) {
            Timber.e("Failed to configure capture session.")
        }

        override fun onClosed(session: CameraCaptureSession) {
            if (captureSession != null && captureSession == session) {
                captureSession = null
            }
        }
    }

    private val captureCallback: PictureCaptureCallback = object : PictureCaptureCallback() {

        override fun onPreCaptureRequired() {
            previewRequestBuilder?.set(
                    CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
                    CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START
            )
            setState(Camera2.PictureCaptureCallback.STATE_PRE_CAPTURE)
            try {
                captureSession?.capture(
                        previewRequestBuilder
                                ?.build()
                                ?: throw CameraAccessException(CameraAccessException.CAMERA_ERROR),
                        this,
                        null
                )
                previewRequestBuilder?.set(
                        CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
                        CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_IDLE
                )
            } catch (e: CameraAccessException) {
                Timber.e(e, "Failed to run precapture sequence.")
            }
        }

        override fun onReady() {
            captureStillPicture()
        }
    }

    private val mOnImageAvailableListener = ImageReader.OnImageAvailableListener { reader ->
        reader.acquireNextImage().use { image ->
            if (image.planes.isNotEmpty()) {
                val buffer = image.planes[0].buffer
                val data = ByteArray(buffer.remaining())
                buffer.get(data)
                callback?.onPictureTaken(data)
            }
        }
    }

    private lateinit var cameraId: String

    private var cameraCharacteristics: CameraCharacteristics? = null

    private var camera: CameraDevice? = null

    private var captureSession: CameraCaptureSession? = null

    private var previewRequestBuilder: CaptureRequest.Builder? = null

    private var imageReader: ImageReader? = null

    private val previewSizes = SizeMap()

    private val pictureSizes = SizeMap()

    override var displayOrientation: Int = 0
        set(displayOrientation) {
            field = displayOrientation
            preview.setDisplayOrientation(displayOrientation)
        }

    override val isCameraOpened: Boolean get() = camera != null

    override var facing: Int = Modes.FACING_BACK
        set(facing) {
            if (field == facing) return
            field = facing
            if (isCameraOpened) {
                stop()
                start()
            }
        }

    override val supportedAspectRatios: Set<AspectRatio> get() = previewSizes.ratios()

    override var autoFocus: Boolean = false
        set(autoFocus) {
            if (field == autoFocus) return
            field = autoFocus
            updateAutoFocus()
            try {
                captureSession?.setRepeatingRequest(
                        previewRequestBuilder?.build() ?: return,
                        captureCallback,
                        null
                )
            } catch (e: CameraAccessException) {
                field = !field // Revert
            }
        }

    override var touchToFocus: Boolean = false
        set(touchToFocus) {
            if (field == touchToFocus) return
            field = touchToFocus
            updateTouchOnFocus()
            try {
                captureSession?.setRepeatingRequest(
                        previewRequestBuilder?.build() ?: return,
                        captureCallback,
                        null
                )
            } catch (e: CameraAccessException) {
                field = !field // Revert
            }
        }

    override var awb: Int = Modes.AutoWhiteBalance.AWB_OFF
        set(awb) {
            if (field == awb) return
            val saved = field
            field = awb
            updateAutoWhiteBalance()
            try {
                captureSession?.setRepeatingRequest(
                        previewRequestBuilder?.build() ?: return,
                        captureCallback,
                        null
                )
            } catch (e: CameraAccessException) {
                field = saved // Revert
            }
        }

    override var flash: Int = Modes.Flash.FLASH_OFF
        set(flash) {
            if (field == flash) return
            val saved = field
            field = flash
            updateFlash()
            try {
                captureSession?.setRepeatingRequest(
                        previewRequestBuilder?.build() ?: return,
                        captureCallback,
                        null
                )
            } catch (e: CameraAccessException) {
                field = saved // Revert
            }
        }

    override var ae: Boolean = true
        set(ae) {
            if (field == ae) return
            field = ae
            updateAutoExposure()
            try {
                captureSession?.setRepeatingRequest(
                        previewRequestBuilder?.build() ?: return,
                        captureCallback,
                        null
                )
            } catch (e: CameraAccessException) {
                field = !field // Revert
            }
        }

    override var opticalStabilization: Boolean = true
        set(opticalStabilization) {
            if (field == opticalStabilization) return
            field = opticalStabilization
            updateOpticalStabilization()
            try {
                captureSession?.setRepeatingRequest(
                        previewRequestBuilder?.build() ?: return,
                        captureCallback,
                        null
                )
            } catch (e: CameraAccessException) {
                field = !field // Revert
            }
        }

    override var noiseReduction: Int = NOISE_REDUCTION_HIGH_QUALITY
        set(noiseReduction) {
            if (field == noiseReduction) return
            val saved = field
            field = noiseReduction
            updateFlash()
            try {
                captureSession?.setRepeatingRequest(
                        previewRequestBuilder?.build() ?: return,
                        captureCallback,
                        null
                )
            } catch (e: CameraAccessException) {
                field = saved // Revert
            }
        }

    override var aspectRatio: AspectRatio = Modes.DEFAULT_ASPECT_RATIO

    override fun start(): Boolean {
        if (!chooseCameraIdByFacing()) return false
        updateModes()
        collectCameraInfo()
        prepareImageReader()
        startOpeningCamera()
        return true
    }

    override fun stop() {
        captureSession?.close()
        captureSession = null
        camera?.close()
        camera = null
        imageReader?.close()
        imageReader = null
    }

    override fun setAspectRatio(ratio: AspectRatio): Boolean {
        if (ratio == aspectRatio || !previewSizes.ratios().contains(ratio)) {
            // TODO: Better error handling
            return false
        }
        aspectRatio = ratio
        prepareImageReader()
        captureSession?.close()
        captureSession = null
        startCaptureSession()
        return true
    }

    override fun takePicture() {
        if (autoFocus) lockFocus()
        else captureStillPicture()
    }

    /**
     * Chooses a camera ID by the specified camera facing ([.facing]).
     *
     * This rewrites [.cameraId], [.cameraCharacteristics], and optionally
     * [.facing].
     */
    private fun chooseCameraIdByFacing(): Boolean {
        try {
            val internalFacing = INTERNAL_FACINGS.get(facing)
            val ids = cameraManager.cameraIdList
            if (ids.isEmpty()) { // No camera
                throw RuntimeException("No camera available.")
            }
            for (id in ids) {
                val characteristics = cameraManager.getCameraCharacteristics(id)
                val level = characteristics.get(
                        CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)
                if (level == null || level == CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY) {
                    continue
                }
                val internal = characteristics.get(CameraCharacteristics.LENS_FACING)
                        ?: throw NullPointerException("Unexpected state: LENS_FACING null")
                if (internal == internalFacing) {
                    cameraId = id
                    cameraCharacteristics = characteristics
                    return true
                }
            }
            // Not found
            cameraId = ids[0]
            cameraCharacteristics = cameraManager.getCameraCharacteristics(cameraId)
            val level = cameraCharacteristics?.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)
            if (level == null || level == CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY) {
                return false
            }
            val internal = cameraCharacteristics?.get(CameraCharacteristics.LENS_FACING)
                    ?: throw NullPointerException("Unexpected state: LENS_FACING null")
            var i = 0
            val count = INTERNAL_FACINGS.size()
            while (i < count) {
                if (INTERNAL_FACINGS.valueAt(i) == internal) {
                    facing = INTERNAL_FACINGS.keyAt(i)
                    return true
                }
                i++
            }
            // The operation can reach here when the only camera device is an external one.
            // We treat it as facing back.
            facing = Modes.FACING_BACK
            return true
        } catch (e: CameraAccessException) {
            throw RuntimeException("Failed to get a list of camera devices", e)
        }
    }

    /**
     *
     * Collects some information from [.cameraCharacteristics].
     *
     * This rewrites [.previewSizes], [.pictureSizes], and optionally,
     * [.aspectRatio].
     */
    private fun collectCameraInfo() {

        val map = cameraCharacteristics?.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                ?: throw IllegalStateException("Failed to get configuration map: $cameraId")

        previewSizes.clear()

        for (size in map.getOutputSizes(preview.outputClass)) {
            val width = size.width
            val height = size.height
            if (width <= MAX_PREVIEW_WIDTH && height <= MAX_PREVIEW_HEIGHT) {
                previewSizes.add(Size(width, height))
            }
        }

        pictureSizes.clear()

        collectPictureSizes(pictureSizes, map)

        for (ratio in previewSizes.ratios()) {
            if (!pictureSizes.ratios().contains(ratio)) {
                previewSizes.remove(ratio)
            }
        }

        if (!previewSizes.ratios().contains(aspectRatio)) {
            aspectRatio = previewSizes.ratios().iterator().next()
        }
    }

    protected open fun collectPictureSizes(sizes: SizeMap, map: StreamConfigurationMap) {
        for (size in map.getOutputSizes(ImageFormat.JPEG)) {
            pictureSizes.add(Size(size.width, size.height))
        }
    }

    private fun prepareImageReader() {

        imageReader?.close()

        val largest = pictureSizes.sizes(aspectRatio).last()
        imageReader = ImageReader.newInstance(
                largest.width,
                largest.height,
                ImageFormat.JPEG,
                2 // maxImages
        )
        imageReader?.setOnImageAvailableListener(mOnImageAvailableListener, null)
    }

    /**
     * Starts opening a camera device.
     *
     * The result will be processed in [.cameraDeviceCallback].
     */
    @SuppressLint("MissingPermission")
    private fun startOpeningCamera() {
        try {
            cameraManager.openCamera(cameraId, cameraDeviceCallback, null)
        } catch (e: CameraAccessException) {
            throw RuntimeException("Failed to open camera: $cameraId", e)
        }
    }

    /**
     * Starts a capture session for camera preview.
     *
     * This rewrites [.previewRequestBuilder].
     *
     * The result will be continuously processed in [.sessionCallback].
     */
    fun startCaptureSession() {
        if (!isCameraOpened || !preview.isReady || imageReader == null) {
            return
        }
        val previewSize = chooseOptimalSize()
        preview.setBufferSize(previewSize.width, previewSize.height)
        val surface = preview.surface
        try {
            previewRequestBuilder = camera?.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                    ?: throw CameraAccessException(CameraAccessException.CAMERA_ERROR)
            previewRequestBuilder?.addTarget(surface)
            camera?.createCaptureSession(
                    Arrays.asList(
                            surface,
                            imageReader?.surface
                                    ?: throw CameraAccessException(CameraAccessException.CAMERA_ERROR)
                    ),
                    sessionCallback,
                    null
            )
        } catch (e: CameraAccessException) {
            throw RuntimeException("Failed to start camera session")
        }
    }

    /**
     * Chooses the optimal preview size based on [.previewSizes] and the surface size.
     *
     * @return The picked size for camera preview.
     */
    private fun chooseOptimalSize(): Size {
        val surfaceLonger: Int
        val surfaceShorter: Int
        val surfaceWidth = preview.width
        val surfaceHeight = preview.height
        if (surfaceWidth < surfaceHeight) {
            surfaceLonger = surfaceHeight
            surfaceShorter = surfaceWidth
        } else {
            surfaceLonger = surfaceWidth
            surfaceShorter = surfaceHeight
        }
        val candidates = previewSizes.sizes(aspectRatio)

        // Pick the smallest of those big enough
        for (size in candidates) {
            if (size.width >= surfaceLonger && size.height >= surfaceShorter) {
                return size
            }
        }
        // If no size is big enough, pick the largest one.
        return candidates.last()
    }

    /**
     * Updates the internal state of auto-focus to [.autoFocus].
     */
    private fun updateAutoFocus() {
        if (autoFocus) {
            val modes = cameraCharacteristics?.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES)
            // Auto focus is not supported
            if (modes == null
                    || modes.isEmpty()
                    || (modes.size == 1 && modes[0] == CameraCharacteristics.CONTROL_AF_MODE_OFF)) {
                previewRequestBuilder?.set(
                        CaptureRequest.CONTROL_AF_MODE,
                        CaptureRequest.CONTROL_AF_MODE_OFF
                )
            } else {
                previewRequestBuilder?.set(
                        CaptureRequest.CONTROL_AF_MODE,
                        CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
                )
            }
        } else {
            previewRequestBuilder?.set(
                    CaptureRequest.CONTROL_AF_MODE,
                    CaptureRequest.CONTROL_AF_MODE_OFF
            )
        }
    }

    private fun updateTouchOnFocus() {

    }

    /**
     * Updates the internal state of flash to [.flash].
     */
    private fun updateFlash() {
        previewRequestBuilder?.apply {
            when (flash) {
                FLASH_OFF -> {
                    set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                    set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF)
                }
                FLASH_ON -> {
                    set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_ALWAYS_FLASH)
                    set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF)
                }
                FLASH_TORCH -> {
                    set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                    set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH)
                }
                FLASH_AUTO -> {
                    set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH)
                    set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF)
                }
                FLASH_RED_EYE -> {
                    set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH_REDEYE)
                    set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF)
                }
            }
            ae = true
        }
    }

    private fun updateAutoExposure() {

    }

    private fun updateAutoWhiteBalance() {
        previewRequestBuilder?.apply {
            val modes = cameraCharacteristics?.get(CameraCharacteristics.CONTROL_AWB_AVAILABLE_MODES)
            if (modes == null
                    || modes.isEmpty()
                    || (modes.size == 1 && modes[0] == CameraCharacteristics.CONTROL_AWB_MODE_OFF)) {
                set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_OFF)
            } else if (modes.contains(awb)) set(CaptureRequest.CONTROL_AWB_MODE, awb)
        }
    }

    private fun updateOpticalStabilization() {
        if (opticalStabilization) {
            val modes = cameraCharacteristics?.get(CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION)
            if (modes == null
                    || modes.isEmpty()
                    || (modes.size == 1 && modes[0] == CameraCharacteristics.LENS_OPTICAL_STABILIZATION_MODE_OFF)) {
                previewRequestBuilder?.set(
                        CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE,
                        CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_OFF
                )
            } else {
                previewRequestBuilder?.set(
                        CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE,
                        CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_ON
                )
            }
        } else {
            previewRequestBuilder?.set(
                    CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE,
                    CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_OFF
            )
        }
    }

    private fun updateNoiseReduction() {
        previewRequestBuilder?.apply {
            val modes = cameraCharacteristics?.get(CameraCharacteristics.NOISE_REDUCTION_AVAILABLE_NOISE_REDUCTION_MODES)
            if (modes == null
                    || modes.isEmpty()
                    || (modes.size == 1 && modes[0] == CameraCharacteristics.NOISE_REDUCTION_MODE_OFF)) {
                set(CaptureRequest.NOISE_REDUCTION_MODE, CaptureRequest.NOISE_REDUCTION_MODE_OFF)
            } else if (modes.contains(noiseReduction)) set(CaptureRequest.NOISE_REDUCTION_MODE, noiseReduction)
        }
    }

    fun updateModes() {
        updateAutoFocus()
        updateTouchOnFocus()
        updateFlash()
        updateAutoExposure()
        updateAutoWhiteBalance()
        updateOpticalStabilization()
        updateNoiseReduction()
    }

    /**
     * Locks the focus as the first step for a still image capture.
     */
    private fun lockFocus() {
        previewRequestBuilder?.set(
                CaptureRequest.CONTROL_AF_TRIGGER,
                CaptureRequest.CONTROL_AF_TRIGGER_START
        )
        try {
            captureCallback.setState(PictureCaptureCallback.STATE_LOCKING)
            captureSession?.capture(
                    previewRequestBuilder?.build()
                            ?: throw CameraAccessException(CameraAccessException.CAMERA_ERROR),
                    captureCallback,
                    null
            )
        } catch (e: CameraAccessException) {
            Timber.e(e, "Failed to lock focus.")
        }
    }

    /**
     * Captures a still picture.
     */
    fun captureStillPicture() {
        try {

            val captureRequestBuilder = (camera?.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
                    ?: throw CameraAccessException(CameraAccessException.CAMERA_ERROR)).apply {

                addTarget(imageReader?.surface
                        ?: throw CameraAccessException(CameraAccessException.CAMERA_ERROR))

                set(
                        CaptureRequest.CONTROL_AF_MODE,
                        previewRequestBuilder?.get(CaptureRequest.CONTROL_AF_MODE)
                )

                set(
                        CaptureRequest.CONTROL_AWB_MODE,
                        previewRequestBuilder?.get(CaptureRequest.CONTROL_AWB_MODE)
                )

                set(
                        CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE,
                        previewRequestBuilder?.get(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE)
                )

                set(
                        CaptureRequest.NOISE_REDUCTION_MODE,
                        previewRequestBuilder?.get(CaptureRequest.NOISE_REDUCTION_MODE)
                )

                set(
                        CaptureRequest.CONTROL_AE_MODE,
                        previewRequestBuilder?.get(CaptureRequest.CONTROL_AE_MODE)
                )

                set(
                        CaptureRequest.FLASH_MODE,
                        previewRequestBuilder?.get(CaptureRequest.FLASH_MODE)
                )

                // Calculate JPEG orientation.
                val sensorOrientation = cameraCharacteristics?.get(CameraCharacteristics.SENSOR_ORIENTATION)
                        ?: throw CameraAccessException(CameraAccessException.CAMERA_ERROR)
                set(CaptureRequest.JPEG_ORIENTATION,
                        (sensorOrientation
                                + (displayOrientation * if (facing == Modes.FACING_FRONT) 1 else -1)
                                + 360) % 360
                )
            }

            // Stop preview and capture a still picture.
            captureSession?.stopRepeating()
            captureSession?.capture(
                    captureRequestBuilder.build(),
                    object : CameraCaptureSession.CaptureCallback() {
                        override fun onCaptureCompleted(
                                session: CameraCaptureSession,
                                request: CaptureRequest,
                                result: TotalCaptureResult
                        ) {
                            unlockFocus()
                        }
                    },
                    null
            )
        } catch (e: CameraAccessException) {
            Timber.e(e, "Cannot capture a still picture.")
        }
    }

    /**
     * Unlocks the auto-focus and restart camera preview. This is supposed to be called after
     * capturing a still picture.
     */
    fun unlockFocus() {
        previewRequestBuilder?.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_CANCEL)
        try {
            captureSession?.capture(
                    previewRequestBuilder?.build()
                            ?: throw CameraAccessException(CameraAccessException.CAMERA_ERROR),
                    captureCallback,
                    null
            )
            updateModes()
            previewRequestBuilder?.set(CaptureRequest.CONTROL_AF_TRIGGER,
                    CaptureRequest.CONTROL_AF_TRIGGER_IDLE)
            captureSession?.setRepeatingRequest(
                    previewRequestBuilder?.build()
                            ?: throw CameraAccessException(CameraAccessException.CAMERA_ERROR),
                    captureCallback,
                    null
            )
            captureCallback.setState(PictureCaptureCallback.STATE_PREVIEW)
        } catch (e: CameraAccessException) {
            Timber.e(e, "Failed to restart camera preview.")
        }
    }

    /**
     * A [CameraCaptureSession.CaptureCallback] for capturing a still picture.
     */
    private abstract class PictureCaptureCallback internal constructor() : CameraCaptureSession.CaptureCallback() {

        private var state: Int = 0

        internal fun setState(state: Int) {
            this.state = state
        }

        override fun onCaptureProgressed(
                session: CameraCaptureSession,
                request: CaptureRequest,
                partialResult: CaptureResult
        ) {
            process(partialResult)
        }

        override fun onCaptureCompleted(
                session: CameraCaptureSession,
                request: CaptureRequest,
                result: TotalCaptureResult
        ) {
            process(result)
        }

        private fun process(result: CaptureResult) {
            when (state) {
                STATE_LOCKING -> {
                    when (result.get(CaptureResult.CONTROL_AF_STATE) ?: return) { // af state
                        CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED,
                        CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED -> {
                            when (result.get(CaptureResult.CONTROL_AE_STATE)) { // ae state
                                null, CaptureResult.CONTROL_AE_STATE_CONVERGED -> {
                                    setState(STATE_CAPTURING)
                                    onReady()
                                }
                                else -> {
                                    setState(STATE_LOCKED)
                                    onPreCaptureRequired()
                                }
                            }
                        }
                    }
                }
                STATE_PRE_CAPTURE -> {
                    val ae = result.get(CaptureResult.CONTROL_AE_STATE)
                    if (ae == null || ae == CaptureResult.CONTROL_AE_STATE_PRECAPTURE ||
                            ae == CaptureRequest.CONTROL_AE_STATE_FLASH_REQUIRED ||
                            ae == CaptureResult.CONTROL_AE_STATE_CONVERGED) {
                        setState(STATE_WAITING)
                    }
                }
                STATE_WAITING -> {
                    val ae = result.get(CaptureResult.CONTROL_AE_STATE)
                    if (ae == null || ae != CaptureResult.CONTROL_AE_STATE_PRECAPTURE) {
                        setState(STATE_CAPTURING)
                        onReady()
                    }
                }
            }
        }

        /**
         * Called when it is ready to take a still picture.
         */
        abstract fun onReady()

        /**
         * Called when it is necessary to run the precapture sequence.
         */
        abstract fun onPreCaptureRequired()

        companion object {
            internal const val STATE_PREVIEW = 0
            internal const val STATE_LOCKING = 1
            internal const val STATE_LOCKED = 2
            internal const val STATE_PRE_CAPTURE = 3
            internal const val STATE_WAITING = 4
            internal const val STATE_CAPTURING = 5
        }
    }

    companion object {

        private val INTERNAL_FACINGS = SparseIntArray().apply {
            put(Modes.FACING_BACK, CameraCharacteristics.LENS_FACING_BACK)
            put(Modes.FACING_FRONT, CameraCharacteristics.LENS_FACING_FRONT)
        }

        /**
         * Max preview width that is guaranteed by Camera2 API
         */
        private const val MAX_PREVIEW_WIDTH = 1920

        /**
         * Max preview height that is guaranteed by Camera2 API
         */
        private const val MAX_PREVIEW_HEIGHT = 1080
    }
}
