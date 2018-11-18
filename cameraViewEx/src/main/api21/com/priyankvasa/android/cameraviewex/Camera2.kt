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
import android.hardware.camera2.TotalCaptureResult
import android.hardware.camera2.params.StreamConfigurationMap
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.renderscript.RenderScript
import android.util.SparseIntArray
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import timber.log.Timber

@TargetApi(21)
internal open class Camera2(
        final override val listener: CameraInterface.Listener,
        final override val preview: PreviewImpl,
        context: Context
) : CameraInterface {

    init {
        preview.setCallback(object : PreviewImpl.Callback {
            override fun onSurfaceChanged() {
                startCaptureSession()
            }
        })
    }

    private val rs = RenderScript.create(context)

    private val internalFacings = SparseIntArray().apply {
        put(Modes.Facing.FACING_BACK, CameraCharacteristics.LENS_FACING_BACK)
        put(Modes.Facing.FACING_FRONT, CameraCharacteristics.LENS_FACING_FRONT)
    }

    private val internalOutputFormats = SparseIntArray().apply {
        put(Modes.OutputFormat.JPEG, ImageFormat.JPEG)
        put(Modes.OutputFormat.YUV_420_888, ImageFormat.YUV_420_888)
        put(Modes.OutputFormat.RGBA_8888, ImageFormat.YUV_420_888)
    }

    /**
     * Max preview width that is guaranteed by Camera2 API
     */
    private val maxPreviewWidth = 1920

    /**
     * Max preview height that is guaranteed by Camera2 API
     */
    private val maxPreviewHeight = 1080

    /**
     * An additional thread for running tasks that shouldn't block the UI.
     */
    private var backgroundThread: HandlerThread? = HandlerThread("CameraViewExBackground").also { it.start() }

    /**
     * A [Handler] for running tasks in the background.
     */
    private var backgroundHandler: Handler? = Handler(backgroundThread?.looper)

    private val cameraManager: CameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager

    private val cameraDeviceCallback = object : CameraDevice.StateCallback() {

        override fun onOpened(camera: CameraDevice) {
            this@Camera2.camera = camera
            listener.onCameraOpened()
            startCaptureSession()
        }

        override fun onClosed(camera: CameraDevice) {
            listener.onCameraClosed()
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
                previewRequestBuilder?.build()?.let {
                    captureSession?.setRepeatingRequest(it, captureCallback, backgroundHandler)
                }
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
            if (captureSession != null && captureSession == session) captureSession = null
        }
    }

    private val captureCallback: PictureCaptureCallback = object : PictureCaptureCallback() {

        override fun onPreCaptureRequired() {
            previewRequestBuilder?.set(
                    CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
                    CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START
            )
            setState(STATE_PRE_CAPTURE)
            try {
                captureSession?.capture(
                        previewRequestBuilder?.build()
                                ?: throw CameraAccessException(CameraAccessException.CAMERA_ERROR),
                        this,
                        backgroundHandler
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

    private val onPreviewImageAvailableListener = ImageReader.OnImageAvailableListener { reader ->
        listener.onPreviewFrame(reader)
    }

    private val onCaptureImageAvailableListener = ImageReader.OnImageAvailableListener { reader ->

        val image = reader.acquireLatestImage()

        if (image.format == internalOutputFormat
                && image.planes.isNotEmpty()) GlobalScope.launch(Dispatchers.Main) {

            image.runCatching {
                decode(outputFormat, rs).await().also { listener.onPictureTaken(it) }
                close()
            }.onFailure { t ->
                Timber.w(t)
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

    override var cameraMode: Int = Modes.DEFAULT_CAMERA_MODE

    override var outputFormat: Int = Modes.DEFAULT_OUTPUT_FORMAT

    internal val internalOutputFormat: Int get() = internalOutputFormats[outputFormat]

    override var displayOrientation: Int = 0
        set(value) {
            field = value
            preview.setDisplayOrientation(value)
        }

    override val isCameraOpened: Boolean get() = camera != null

    override var facing: Int = Modes.DEFAULT_FACING
        set(value) {
            if (field == value) {
                if (!isCameraOpened) {
                    chooseCameraIdByFacing()
                    collectCameraInfo()
                }
                return
            }
            field = value
            if (isCameraOpened) {
                stop()
                start()
            } else {
                chooseCameraIdByFacing()
                collectCameraInfo()
            }
        }

    private val internalFacing: Int get() = internalFacings[facing]

    override val supportedAspectRatios: Set<AspectRatio> get() = previewSizes.ratios()

    override var autoFocus: Boolean = Modes.DEFAULT_AUTO_FOCUS
        set(value) {
            if (field == value) return
            field = value
            updateAutoFocus()
            try {
                captureSession?.setRepeatingRequest(
                        previewRequestBuilder?.build() ?: return,
                        captureCallback,
                        backgroundHandler
                )
            } catch (e: CameraAccessException) {
                field = !field // Revert
                updateAutoFocus()
            }
        }

    override var touchToFocus: Boolean = Modes.DEFAULT_TOUCH_TO_FOCUS
        set(value) {
            if (field == value) return
            field = value
            updateTouchOnFocus()
            try {
                captureSession?.setRepeatingRequest(
                        previewRequestBuilder?.build() ?: return,
                        captureCallback,
                        backgroundHandler
                )
            } catch (e: CameraAccessException) {
                field = !field // Revert
                updateTouchOnFocus()
            }
        }

    override var awb: Int = Modes.DEFAULT_AWB
        set(value) {
            if (field == value) return
            val saved = field
            field = value
            updateAutoWhiteBalance()
            try {
                captureSession?.setRepeatingRequest(
                        previewRequestBuilder?.build() ?: return,
                        captureCallback,
                        backgroundHandler
                )
            } catch (e: CameraAccessException) {
                field = saved // Revert
                updateAutoWhiteBalance()
            }
        }

    override var flash: Int = Modes.DEFAULT_FLASH
        set(value) {
            if (field == value) return
            val saved = field
            field = value
            updateFlash()
            try {
                captureSession?.setRepeatingRequest(
                        previewRequestBuilder?.build() ?: return,
                        captureCallback,
                        backgroundHandler
                )
            } catch (e: CameraAccessException) {
                field = saved // Revert
                updateFlash()
            }
        }

    override var ae: Boolean = Modes.DEFAULT_AUTO_EXPOSURE
        set(value) {
            if (field == value) return
            field = value
            updateAutoExposure()
            try {
                captureSession?.setRepeatingRequest(
                        previewRequestBuilder?.build() ?: return,
                        captureCallback,
                        backgroundHandler
                )
            } catch (e: CameraAccessException) {
                field = !field // Revert
                updateAutoExposure()
            }
        }

    override var opticalStabilization: Boolean = Modes.DEFAULT_OPTICAL_STABILIZATION
        set(value) {
            if (field == value) return
            field = value
            updateOpticalStabilization()
            try {
                captureSession?.setRepeatingRequest(
                        previewRequestBuilder?.build() ?: return,
                        captureCallback,
                        backgroundHandler
                )
            } catch (e: CameraAccessException) {
                field = !field // Revert
                updateOpticalStabilization()
            }
        }

    override var noiseReduction: Int = Modes.DEFAULT_NOISE_REDUCTION
        set(value) {
            if (field == value) return
            val saved = field
            field = value
            updateFlash()
            try {
                captureSession?.setRepeatingRequest(
                        previewRequestBuilder?.build() ?: return,
                        captureCallback,
                        backgroundHandler
                )
            } catch (e: CameraAccessException) {
                field = saved // Revert
                updateFlash()
            }
        }

    override var aspectRatio: AspectRatio = Modes.DEFAULT_ASPECT_RATIO

    override var zsl: Boolean = Modes.DEFAULT_ZSL
        set(value) {
            if (field == value) return
            field = value
            if (isCameraOpened) {
                stop()
                start()
            }
        }

    /**
     * Starts a background thread and its [Handler].
     */
    private fun startBackgroundThread() {
        backgroundThread = HandlerThread("CameraViewExBackground").also { it.start() }
        backgroundHandler = Handler(backgroundThread?.looper)
    }

    /**
     * Stops the background thread and its [Handler].
     */
    private fun stopBackgroundThread() {
        backgroundThread?.quitSafely()
        try {
            backgroundThread?.join()
            backgroundThread = null
            backgroundHandler = null
        } catch (e: InterruptedException) {
            Timber.e(e)
        }
    }

    override fun start(): Boolean {
        if (!chooseCameraIdByFacing()) return false
        if (backgroundThread == null && backgroundHandler == null) startBackgroundThread()
        collectCameraInfo()
        updateModes()
        prepareImageReaders()
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
        stopBackgroundThread()
    }

    override fun setAspectRatio(ratio: AspectRatio): Boolean {
        if (ratio == aspectRatio || !previewSizes.ratios().contains(ratio)) {
            // TODO: Better error handling
            return false
        }
        aspectRatio = ratio
        prepareImageReaders()
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
            cameraManager.cameraIdList.run {
                ifEmpty {
                    Timber.e("No camera available.") // No camera
                    return false
                }
                forEach { id ->
                    val characteristics = cameraManager.getCameraCharacteristics(id)
                    val level = characteristics.get(
                            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)
                    if (level == null || level == CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY) {
                        return@forEach
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
                cameraId = get(0)
            }

            cameraCharacteristics = cameraManager.getCameraCharacteristics(cameraId)

            val level = cameraCharacteristics?.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)

            if (level == null || level == CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY) return false

            val internal = cameraCharacteristics?.get(CameraCharacteristics.LENS_FACING)
                    ?: throw NullPointerException("Unexpected state: LENS_FACING null")

            for (i in 0 until internalFacings.size()) {
                if (internalFacings.valueAt(i) == internal) {
                    facing = internalFacings.keyAt(i)
                    return true
                }
            }

            // The operation can reach here when the only camera device is an external one.
            // We treat it as facing back.
            facing = Modes.Facing.FACING_BACK
            return true
        } catch (e: CameraAccessException) {
            Timber.e(e, "Failed to get a list of camera devices")
            return false
        }
    }

    /**
     * Collects some information from [.cameraCharacteristics].
     *
     * This rewrites [.previewSizes], [.pictureSizes], and optionally,
     * [.aspectRatio].
     */
    private fun collectCameraInfo() {

        val map = cameraCharacteristics?.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                ?: throw IllegalStateException("Failed to get configuration map: $cameraId")

        previewSizes.clear()

        map.getOutputSizes(preview.outputClass).forEach {
            if (it.width <= maxPreviewWidth && it.height <= maxPreviewHeight) {
                previewSizes.add(Size(it.width, it.height))
            }
        }

        pictureSizes.clear()

        collectPictureSizes(pictureSizes, map)

        previewSizes.ratios().forEach {
            if (!pictureSizes.ratios().contains(it)) previewSizes.remove(it)
        }

        previewSizes.ratios().run { if (!contains(aspectRatio)) aspectRatio = iterator().next() }
    }

    protected open fun collectPictureSizes(sizes: SizeMap, map: StreamConfigurationMap) {
        map.getOutputSizes(internalOutputFormat).forEach { pictureSizes.add(Size(it.width, it.height)) }
    }

    private fun prepareImageReaders() {

        imageReader?.close()

        imageReader = when (cameraMode) {

            Modes.CameraMode.SINGLE_CAPTURE -> {
                val largestPicture = pictureSizes.sizes(aspectRatio).last()
                ImageReader.newInstance(
                        largestPicture.width,
                        largestPicture.height,
                        internalOutputFormat,
                        2 // maxImages
                ).apply { setOnImageAvailableListener(onCaptureImageAvailableListener, backgroundHandler) }
            }

//            CameraMode.BURST_CAPTURE -> null

            Modes.CameraMode.CONTINUOUS_FRAME -> {
                val largestPreview = previewSizes.sizes(aspectRatio).last()
                ImageReader.newInstance(
                        largestPreview.width,
                        largestPreview.height,
                        ImageFormat.YUV_420_888,
                        2 // maxImages
                ).apply { setOnImageAvailableListener(onPreviewImageAvailableListener, backgroundHandler) }
            }

//            CameraMode.VIDEO -> null

            else -> null
        }
    }

    /**
     * Starts opening a camera device.
     *
     * The result will be processed in [.cameraDeviceCallback].
     */
    @SuppressLint("MissingPermission")
    private fun startOpeningCamera() {
        try {
            cameraManager.openCamera(cameraId, cameraDeviceCallback, backgroundHandler)
        } catch (e: CameraAccessException) {
            Timber.e(e, "Failed to open camera: $cameraId")
        }
    }

    /**
     * Starts a capture session for camera preview.
     *
     * This rewrites [.previewRequestBuilder].
     *
     * The result will be continuously processed in [.sessionCallback].
     */
    private fun startCaptureSession() {

        if (!isCameraOpened
                || !preview.isReady
                || imageReader == null) return

        chooseOptimalSize().run { preview.setBufferSize(width, height) }

        val surface = preview.surface
                ?: throw CameraAccessException(CameraAccessException.CAMERA_ERROR)

        val readerSurface = imageReader?.surface
                ?: throw CameraAccessException(CameraAccessException.CAMERA_ERROR)

        try {
            val template = if (zsl) CameraDevice.TEMPLATE_ZERO_SHUTTER_LAG else CameraDevice.TEMPLATE_PREVIEW

            previewRequestBuilder = camera?.createCaptureRequest(template)
                    ?.apply {
                        addTarget(surface)
                        readerSurface
                                .takeIf { cameraMode == Modes.CameraMode.CONTINUOUS_FRAME }
                                ?.let { addTarget(it) }
                    }
                    ?: throw CameraAccessException(CameraAccessException.CAMERA_ERROR)

            camera?.createCaptureSession(
                    arrayListOf(surface, readerSurface),
                    sessionCallback,
                    backgroundHandler
            )

        } catch (e: CameraAccessException) {
            Timber.e(e, "Failed to start camera session")
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
        candidates.firstOrNull { it.width >= surfaceLonger && it.height >= surfaceShorter }
                ?.also { return it }

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
                Modes.Flash.FLASH_OFF -> {
                    set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                    set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF)
                }
                Modes.Flash.FLASH_ON -> {
                    set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_ALWAYS_FLASH)
                    set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF)
                }
                Modes.Flash.FLASH_TORCH -> {
                    set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                    set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH)
                }
                Modes.Flash.FLASH_AUTO -> {
                    set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH)
                    set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF)
                }
                Modes.Flash.FLASH_RED_EYE -> {
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
                    backgroundHandler
            )
        } catch (e: CameraAccessException) {
            Timber.e(e, "Failed to lock focus.")
        }
    }

    /**
     * Captures a preview frame.
     */
    override fun capturePreviewFrame() {

        try {
            val surface = imageReader?.surface
                    ?: throw CameraAccessException(CameraAccessException.CAMERA_ERROR)

            previewRequestBuilder?.addTarget(surface)

            captureSession?.capture(
                    previewRequestBuilder?.build()
                            ?: throw CameraAccessException(CameraAccessException.CAMERA_ERROR),
                    object : CameraCaptureSession.CaptureCallback() {
                        override fun onCaptureCompleted(
                                session: CameraCaptureSession,
                                request: CaptureRequest,
                                result: TotalCaptureResult
                        ) {
                            previewRequestBuilder?.removeTarget(surface)
                        }
                    },
                    backgroundHandler
            )
        } catch (e: CameraAccessException) {
            Timber.e(e, "Cannot capture preview frame.")
        }
    }

    /**
     * Captures a still picture.
     */
    private fun captureStillPicture() {

        try {
            val surface = imageReader?.surface
                    ?: throw CameraAccessException(CameraAccessException.CAMERA_ERROR)

            val template = if (zsl) CameraDevice.TEMPLATE_ZERO_SHUTTER_LAG else CameraDevice.TEMPLATE_STILL_CAPTURE

            val captureRequestBuilder = (camera?.createCaptureRequest(template)
                    ?: throw CameraAccessException(CameraAccessException.CAMERA_ERROR)).apply {

                addTarget(surface)

                set(CaptureRequest.CONTROL_AF_MODE, previewRequestBuilder?.get(CaptureRequest.CONTROL_AF_MODE))
                set(CaptureRequest.CONTROL_AWB_MODE, previewRequestBuilder?.get(CaptureRequest.CONTROL_AWB_MODE))
                set(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE, previewRequestBuilder?.get(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE))
                set(CaptureRequest.NOISE_REDUCTION_MODE, previewRequestBuilder?.get(CaptureRequest.NOISE_REDUCTION_MODE))
                set(CaptureRequest.CONTROL_AE_MODE, previewRequestBuilder?.get(CaptureRequest.CONTROL_AE_MODE))
                set(CaptureRequest.FLASH_MODE, previewRequestBuilder?.get(CaptureRequest.FLASH_MODE))

                if (imageReader?.imageFormat == ImageFormat.JPEG) { // Calculate JPEG orientation.

                    val sensorOrientation =
                            cameraCharacteristics?.get(CameraCharacteristics.SENSOR_ORIENTATION)
                                    ?: throw CameraAccessException(CameraAccessException.CAMERA_ERROR)

                    val jpegOrientation = (sensorOrientation
                            + (displayOrientation * if (facing == Modes.Facing.FACING_FRONT) 1 else -1)
                            + 360) % 360

                    set(CaptureRequest.JPEG_ORIENTATION, jpegOrientation)
                }
            }

            // Stop preview and capture a still picture.
            captureSession?.stopRepeating()
            captureSession?.capture(
                    captureRequestBuilder.build(),
                    object : CameraCaptureSession.CaptureCallback() {

                        override fun onCaptureStarted(
                                session: CameraCaptureSession,
                                request: CaptureRequest,
                                timestamp: Long,
                                frameNumber: Long
                        ) {
                            GlobalScope.launch(Dispatchers.Main) { preview.shutterView.show() }
                        }

                        override fun onCaptureCompleted(
                                session: CameraCaptureSession,
                                request: CaptureRequest,
                                result: TotalCaptureResult
                        ) {
                            unlockFocus()
                        }
                    },
                    backgroundHandler
            )
        } catch (e: CameraAccessException) {
            Timber.e(e, "Cannot capture a still picture.")
        }
    }

    /**
     * Unlocks the auto-focus and restart camera preview. This is supposed to be called after
     * capturing a still picture.
     */
    private fun unlockFocus() {
        previewRequestBuilder?.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_CANCEL)
        try {
            captureSession?.capture(
                    previewRequestBuilder?.build()
                            ?: throw CameraAccessException(CameraAccessException.CAMERA_ERROR),
                    captureCallback,
                    backgroundHandler
            )
            updateModes()
            previewRequestBuilder?.set(CaptureRequest.CONTROL_AF_TRIGGER,
                    CaptureRequest.CONTROL_AF_TRIGGER_IDLE)
            captureSession?.setRepeatingRequest(
                    previewRequestBuilder?.build()
                            ?: throw CameraAccessException(CameraAccessException.CAMERA_ERROR),
                    captureCallback,
                    backgroundHandler
            )
            captureCallback.setState(PictureCaptureCallback.STATE_PREVIEW)
        } catch (e: CameraAccessException) {
            Timber.e(e, "Failed to restart camera preview.")
        }
    }
}