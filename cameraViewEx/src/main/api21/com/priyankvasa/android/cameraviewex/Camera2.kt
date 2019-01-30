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

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.arch.lifecycle.Lifecycle
import android.arch.lifecycle.LifecycleRegistry
import android.content.Context
import android.graphics.ImageFormat
import android.graphics.Rect
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.hardware.camera2.params.MeteringRectangle
import android.hardware.camera2.params.StreamConfigurationMap
import android.media.ImageReader
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.renderscript.RenderScript
import android.util.SparseIntArray
import android.view.Surface
import com.priyankvasa.android.cameraviewex.extension.isAfSupported
import com.priyankvasa.android.cameraviewex.extension.isAwbSupported
import com.priyankvasa.android.cameraviewex.extension.isNoiseReductionSupported
import com.priyankvasa.android.cameraviewex.extension.isOisSupported
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

@TargetApi(Build.VERSION_CODES.LOLLIPOP)
internal open class Camera2(
    override val listener: CameraInterface.Listener,
    final override val preview: PreviewImpl,
    final override val config: CameraConfiguration,
    override val cameraJob: Job,
    context: Context
) : CameraInterface {

    private val lifecycleRegistry: LifecycleRegistry by lazy {
        LifecycleRegistry(this).also { it.markState(Lifecycle.State.CREATED) }
    }

    init {
        preview.surfaceChangeListener = { if (isCameraOpened) startPreviewCaptureSession() }
        addObservers()
    }

    /** A [Semaphore] to prevent the app from exiting before closing the camera. */
    private val cameraOpenCloseLock = Semaphore(1)

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

    /** Max preview width that is guaranteed by Camera2 API */
    private val maxPreviewWidth = 1920

    /** Max preview height that is guaranteed by Camera2 API */
    private val maxPreviewHeight = 1080

    /** An additional thread for running tasks that shouldn't block the UI. */
    private var backgroundThread: HandlerThread? =
        HandlerThread("CameraViewExBackground").also { it.start() }

    /** A [Handler] for running tasks in the background. */
    private var backgroundHandler: Handler? = Handler(backgroundThread?.looper)

    private val cameraManager: CameraManager =
        context.getSystemService(Context.CAMERA_SERVICE) as CameraManager

    private val cameraDeviceCallback = object : CameraDevice.StateCallback() {

        override fun onOpened(camera: CameraDevice) {
            this@Camera2.camera = camera
            cameraOpenCloseLock.release()
            videoManager.setCameraDevice(camera)
            listener.onCameraOpened()
            if (preview.isReady) startPreviewCaptureSession()
        }

        override fun onClosed(camera: CameraDevice) {
            cameraOpenCloseLock.release()
            listener.onCameraClosed()
        }

        override fun onDisconnected(camera: CameraDevice) {
            cameraOpenCloseLock.release()
            this@Camera2.camera = null
        }

        override fun onError(camera: CameraDevice, error: Int) {
            cameraOpenCloseLock.release()
            listener.onCameraError(CameraViewException("Error opening camera with id ${camera.id} (error: $error)"))
            this@Camera2.camera = null
        }
    }

    private val previewSessionStateCallback = object : CameraCaptureSession.StateCallback() {

        override fun onConfigured(session: CameraCaptureSession) {

            if (camera == null) return

            captureSession = session

            updateModes()

            try {
                captureSession?.setRepeatingRequest(
                    previewRequestBuilder.build(),
                    defaultCaptureCallback,
                    backgroundHandler
                )
            } catch (e: Exception) {
                listener.onCameraError(CameraViewException("Failed to start camera preview.", e))
            }
        }

        override fun onConfigureFailed(session: CameraCaptureSession) {
            listener.onCameraError(CameraViewException("Failed to configure capture session."))
        }

        override fun onClosed(session: CameraCaptureSession) {
            if (captureSession != null && captureSession == session) captureSession = null
        }
    }

    private val videoSessionStateCallback = object : CameraCaptureSession.StateCallback() {

        override fun onConfigured(session: CameraCaptureSession) {

            try {
                captureSession?.close()
                captureSession = session
                captureSession?.setRepeatingRequest(
                    videoManager.buildRequest(),
                    null,
                    backgroundHandler
                )
            } catch (e: Exception) {
                listener.onCameraError(CameraViewException("Failed to start camera preview.", e))
                return
            }

            launch { videoManager.startMediaRecorder() }
                .invokeOnCompletion { t ->
                    when (t) {
                        null -> listener.onVideoRecordStarted()
                        else -> listener.onCameraError(CameraViewException("Camera device is already in use", t))
                    }
                }
        }

        override fun onConfigureFailed(session: CameraCaptureSession) {
            listener.onCameraError(CameraViewException("Failed to configure video capture session."))
        }
    }

    private val defaultCaptureCallback: PictureCaptureCallback = object : PictureCaptureCallback() {

        override fun onPreCaptureRequired() {
            previewRequestBuilder.set(
                CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
                CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START
            )
            setState(STATE_PRE_CAPTURE)
            try {
                captureSession?.capture(
                    previewRequestBuilder.build(),
                    this,
                    backgroundHandler
                )
            } catch (e: Exception) {
                listener.onCameraError(CameraViewException("Failed to run precapture sequence.", e))
            } finally {
                previewRequestBuilder.set(
                    CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
                    CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_IDLE
                )
            }
        }

        override fun onReady() = captureStillPicture()
    }

    private val stillCaptureCallback = object : CameraCaptureSession.CaptureCallback() {

        override fun onCaptureStarted(
            session: CameraCaptureSession,
            request: CaptureRequest,
            timestamp: Long,
            frameNumber: Long
        ) {
            launch { preview.shutterView.show() }
        }

        override fun onCaptureCompleted(
            session: CameraCaptureSession,
            request: CaptureRequest,
            result: TotalCaptureResult
        ) {
            if (!videoManager.isVideoRecording) unlockFocus()
        }
    }

    private val onPreviewImageAvailableListener = ImageReader.OnImageAvailableListener { reader ->
        listener.onPreviewFrame(reader)
    }

    private val onCaptureImageAvailableListener = ImageReader.OnImageAvailableListener { reader ->

        val image = reader.runCatching { acquireLatestImage() }
            .getOrElse { t ->
                listener.onCameraError(CameraViewException("Failed to capture image.", t))
                return@OnImageAvailableListener
            }

        image.runCatching {
            if (format == internalOutputFormat && planes.isNotEmpty()) {
                val imageData: ByteArray = runBlocking { decode(config.outputFormat.value, rs) }
                listener.onPictureTaken(imageData)
            }
        }.onFailure { t -> listener.onCameraError(CameraViewException("Failed to capture image.", t)) }

        image.close()
    }

    private lateinit var cameraId: String

    private lateinit var cameraCharacteristics: CameraCharacteristics

    private var camera: CameraDevice? = null

    private var captureSession: CameraCaptureSession? = null

    private lateinit var previewRequestBuilder: CaptureRequest.Builder

    private var captureImageReader: ImageReader? = null

    private var previewImageReader: ImageReader? = null

    protected val videoManager: VideoManager =
        VideoManager { listener.onCameraError(CameraViewException(it), ErrorLevel.Warning) }

    private val previewSizes = SizeMap()

    private val pictureSizes = SizeMap()

    override var jpegQuality: Int = Modes.DEFAULT_JPEG_QUALITY

    protected val internalOutputFormat: Int get() = internalOutputFormats[config.outputFormat.value]

    override var deviceRotation: Int = 0

    override val isActive: Boolean
        get() = cameraJob.isActive &&
            backgroundHandler?.looper?.thread?.isAlive == true

    override val isCameraOpened: Boolean get() = camera != null

    override val isVideoRecording: Boolean get() = videoManager.isVideoRecording

    private val internalFacing: Int get() = internalFacings[config.facing.value]

    /**
     * Populate the [CameraMap] with all of the cameraIds based on facing [Modes.Facing]
     * and their [CameraCharacteristics]
     */
    override val cameraMap: CameraMap = CameraMap().apply {
        cameraManager.cameraIdList.forEach { cameraId ->
            val characteristics = cameraManager.getCameraCharacteristics(cameraId)
            val level = characteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)
            if (level != null && level != CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY) {
                when (characteristics.get(CameraCharacteristics.LENS_FACING)) {
                    CameraCharacteristics.LENS_FACING_BACK ->
                        add(Modes.Facing.FACING_BACK, cameraId, characteristics)
                    CameraCharacteristics.LENS_FACING_FRONT ->
                        add(Modes.Facing.FACING_FRONT, cameraId, characteristics)
                }
            }
        }
    }

    override val supportedAspectRatios: Set<AspectRatio> get() = previewSizes.ratios()

    private val digitalZoom: DigitalZoom = DigitalZoom { cameraCharacteristics }

    override val maxDigitalZoom: Float get() = digitalZoom.maxZoom

    private var manualFocusEngaged = false

    private val isMeteringAreaAFSupported: Boolean
        get() = cameraCharacteristics.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AF) ?: 0 > 0

    private val isMeteringAreaAESupported: Boolean
        get() = cameraCharacteristics.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AE) ?: 0 > 0

    private val isMeteringAreaAWBSupported: Boolean
        get() = cameraCharacteristics.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AWB) ?: 0 > 0

    private val previewSurfaceTappedListener: (x: Float, y: Float) -> Boolean = listener@{ x, y ->

        val requestBuilder: CaptureRequest.Builder =
            if (isVideoRecording) videoManager.getRequestBuilder() else previewRequestBuilder

        if (!isMeteringAreaAFSupported || manualFocusEngaged) return@listener false

        val sensorRect: Rect = cameraCharacteristics
            .get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)
            ?: return@listener false

        val tapRect = preview.calculateTouchAreaRect(
            sensorRect.width() - 1,
            sensorRect.height() - 1,
            centerX = x,
            centerY = y
        )

        preview.markTouchAreas(arrayOf(tapRect))

        val focusAreaMeteringRect = MeteringRectangle(tapRect, MeteringRectangle.METERING_WEIGHT_MAX)

        val sensorAreaMeteringRect = MeteringRectangle(sensorRect, MeteringRectangle.METERING_WEIGHT_MIN)

        val captureCallback = object : CameraCaptureSession.CaptureCallback() {

            override fun onCaptureCompleted(
                session: CameraCaptureSession,
                request: CaptureRequest,
                result: TotalCaptureResult
            ) {
                val afState = result.get(CaptureResult.CONTROL_AF_STATE)
                val aeState = result.get(CaptureResult.CONTROL_AE_STATE)
                val awbState = result.get(CaptureResult.CONTROL_AWB_STATE)

                if (afState != CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED &&
                    afState != CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED &&
                    aeState != CaptureResult.CONTROL_AE_STATE_CONVERGED &&
                    aeState != CaptureResult.CONTROL_AE_STATE_LOCKED &&
                    awbState != CaptureResult.CONTROL_AWB_STATE_CONVERGED &&
                    awbState != CaptureResult.CONTROL_AWB_STATE_LOCKED) return

                runCatching {

                    requestBuilder.set(
                        CaptureRequest.CONTROL_AF_TRIGGER,
                        CameraMetadata.CONTROL_AF_TRIGGER_CANCEL
                    )

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) requestBuilder.set(
                        CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
                        CameraMetadata.CONTROL_AE_PRECAPTURE_TRIGGER_CANCEL
                    )

                    captureSession?.capture(requestBuilder.build(), null, backgroundHandler)

                    requestBuilder.set(
                        CaptureRequest.CONTROL_AF_TRIGGER,
                        CaptureRequest.CONTROL_AF_TRIGGER_IDLE
                    )
                    requestBuilder.set(
                        CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
                        CameraMetadata.CONTROL_AE_PRECAPTURE_TRIGGER_IDLE
                    )

                    captureSession?.setRepeatingRequest(
                        requestBuilder.build(),
                        defaultCaptureCallback,
                        backgroundHandler
                    )
                }.onFailure { t -> listener.onCameraError(CameraViewException("Failed to restart camera preview.", t)) }

                manualFocusEngaged = false

                launch { preview.removeOverlay() }
            }
        }

        runCatching {
            // Cancel any existing AF trigger (repeated touches, etc.)
            requestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_CANCEL)
            requestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) requestBuilder.set(
                CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
                CameraMetadata.CONTROL_AE_PRECAPTURE_TRIGGER_CANCEL
            )

            captureSession?.capture(requestBuilder.build(), null, backgroundHandler)

            // Add a new AE trigger with focus region
            if (isMeteringAreaAESupported) {
                requestBuilder.set(
                    CaptureRequest.CONTROL_AE_REGIONS,
                    arrayOf(focusAreaMeteringRect, sensorAreaMeteringRect)
                )
            }

            // Add a new AWB trigger with focus region
            if (isMeteringAreaAWBSupported) {
                requestBuilder.set(
                    CaptureRequest.CONTROL_AWB_REGIONS,
                    arrayOf(focusAreaMeteringRect, sensorAreaMeteringRect)
                )
            }

            // Now add a new AF trigger with focus region
            requestBuilder.set(
                CaptureRequest.CONTROL_AF_REGIONS,
                arrayOf(focusAreaMeteringRect, sensorAreaMeteringRect)
            )
            requestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO)
            requestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_START)

            requestBuilder.set(
                CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
                CameraMetadata.CONTROL_AE_PRECAPTURE_TRIGGER_START
            )

            captureSession
                ?.setRepeatingRequest(requestBuilder.build(), captureCallback, backgroundHandler)
                ?: return@listener false

            manualFocusEngaged = true

        }.onFailure { t ->
            listener.onCameraError(CameraViewException("Failed to lock focus.", t))
            return@listener false
        }

        return@listener true
    }

    private val previewSurfacePinchedListener: (scaleFactor: Float) -> Boolean =
        { scaleFactor: Float ->
            config.currentDigitalZoom.value = digitalZoom.getZoomForScaleFactor(scaleFactor)
            true
        }

    override fun getLifecycle(): Lifecycle = lifecycleRegistry

    private fun addObservers(): Unit = config.run {
        cameraMode.observe(this@Camera2) {
            config.currentDigitalZoom.value = 1f
            if (isCameraOpened) {
                stop()
                start()
            }
        }
        outputFormat.observe(this@Camera2) {
            if (isCameraOpened) {
                stop()
                start()
            }
        }
        facing.observe(this@Camera2) {
            if (isCameraOpened) {
                stop()
                start(it)
            }/* else {
                chooseCameraIdByFacing()
                collectCameraInfo()
            }*/
        }
        autoFocus.observe(this@Camera2) {
            updateAf()
            try {
                captureSession?.setRepeatingRequest(
                    previewRequestBuilder.build(),
                    defaultCaptureCallback,
                    backgroundHandler
                )
            } catch (e: Exception) {
                autoFocus.revert()
                listener.onCameraError(
                    CameraViewException("Failed to set autoFocus to $it. Value reverted to ${autoFocus.value}.", e),
                    ErrorLevel.Warning
                )
            }
        }
        touchToFocus.observe(this@Camera2) {
            preview.surfaceTapListener = if (it) previewSurfaceTappedListener else null
        }
        pinchToZoom.observe(this@Camera2) {
            preview.surfacePinchListener = if (it) previewSurfacePinchedListener else null
        }
        currentDigitalZoom.observe(this@Camera2) {
            when {
                it > maxDigitalZoom -> {
                    config.currentDigitalZoom.value = maxDigitalZoom
                    return@observe
                }
                it < 1f -> {
                    config.currentDigitalZoom.value = 1f
                    return@observe
                }
            }
            updateScalerCropRegion() && runCatching {
                captureSession?.setRepeatingRequest(
                    (if (isVideoRecording) videoManager.getRequestBuilder() else previewRequestBuilder).build(),
                    defaultCaptureCallback,
                    backgroundHandler
                ) != null
            }.getOrElse { false }
        }
        awb.observe(this@Camera2) {
            updateAwb()
            try {
                captureSession?.setRepeatingRequest(
                    previewRequestBuilder.build(),
                    defaultCaptureCallback,
                    backgroundHandler
                )
            } catch (e: Exception) {
                awb.revert()
                listener.onCameraError(
                    CameraViewException("Failed to set awb to $it. Value reverted to ${awb.value}.", e),
                    ErrorLevel.Warning
                )
            }
        }
        flash.observe(this@Camera2) {
            updateFlash()
            try {
                captureSession?.setRepeatingRequest(
                    previewRequestBuilder.build(),
                    defaultCaptureCallback,
                    backgroundHandler
                )
            } catch (e: Exception) {
                flash.revert()
                listener.onCameraError(
                    CameraViewException("Failed to set flash to $it. Value reverted to ${flash.value}.", e),
                    ErrorLevel.Warning
                )
            }
        }
        noiseReduction.observe(this@Camera2) {
            updateNoiseReduction()
            try {
                captureSession?.setRepeatingRequest(
                    previewRequestBuilder.build(),
                    defaultCaptureCallback,
                    backgroundHandler
                )
            } catch (e: Exception) {
                noiseReduction.revert()
                listener.onCameraError(
                    CameraViewException("Failed to set noiseReduction to $it. Value reverted to ${noiseReduction.value}.", e),
                    ErrorLevel.Warning
                )
            }
        }
        opticalStabilization.observe(this@Camera2) {
            updateOis()
            try {
                captureSession?.setRepeatingRequest(
                    previewRequestBuilder.build(),
                    defaultCaptureCallback,
                    backgroundHandler
                )
            } catch (e: Exception) {
                opticalStabilization.revert()
                listener.onCameraError(
                    CameraViewException("Failed to set opticalStabilization to $it. Value reverted to ${!it}.", e),
                    ErrorLevel.Warning
                )
            }
        }
        zsl.observe(this@Camera2) {
            if (isCameraOpened) {
                stop()
                start()
            }
        }
    }

    /** Starts a background thread and its [Handler]. */
    private fun startBackgroundThread() {
        backgroundThread = backgroundThread ?: HandlerThread("CameraViewExBackground")
        backgroundThread?.runCatching {
            start()
            backgroundHandler = Handler(looper)
        }
    }

    /** Stops the background thread and its [Handler]. */
    private fun stopBackgroundThread() {
        backgroundThread?.quitSafely()
        try {
            backgroundThread?.join()
            backgroundThread = null
            backgroundHandler = null
        } catch (e: InterruptedException) {
            listener.onCameraError(CameraViewException("Background thread was interrupted.", e))
        }
    }

    override fun start(): Boolean {
        return start(config.facing.value)
    }

    /**
     * Can be used to open the camera to a specified cameraId
     */
    override fun start(cameraId: Int): Boolean {
        cameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)
        if (!chooseCameraById(cameraId.toString())) return false
        if (backgroundThread == null || backgroundHandler == null) {
            stopBackgroundThread()
            startBackgroundThread()
        }
        collectCameraInfo()
        prepareImageReaders()
        startOpeningCamera()
        return true
    }

    override fun stop() {
        super.stop()
        try {
            cameraOpenCloseLock.acquire()
            releaseCaptureSession()
            camera?.close()
            camera = null
            captureImageReader?.close()
            captureImageReader = null
            previewImageReader?.close()
            previewImageReader = null
            videoManager.release()
        } catch (e: InterruptedException) {
            listener.onCameraError(CameraViewException("Interrupted while trying to lock camera closing.", e))
        } finally {
            cameraOpenCloseLock.release()
        }
    }

    private fun releaseCaptureSession() {
        captureSession?.run {
            stopRepeating()
            abortCaptures()
            close()
        }
        captureSession = null
    }

    override fun destroy() {
        super.destroy()
        stopBackgroundThread()
    }

    override fun setAspectRatio(ratio: AspectRatio): Boolean {

        if (!ratio.isValid()) {
            config.aspectRatio.revert()
            return false
        }

        prepareImageReaders()
        releaseCaptureSession()
        startPreviewCaptureSession()
        return true
    }

    private fun AspectRatio.isValid(): Boolean {

        var isRatioValid = false
        val sbRatios = StringBuilder()

        run {
            supportedAspectRatios.forEachIndexed { i, ratio ->
                if (ratio == this) {
                    isRatioValid = true
                    return@run
                }
                sbRatios.append(ratio)
                if (i < supportedAspectRatios.size - 1) sbRatios.append(", ")
            }
        }

        if (!isRatioValid) {
            val e = IllegalArgumentException(
                "Aspect ratio $this is not supported by this device." +
                    " Valid ratios are $sbRatios. Refer CameraView.supportedAspectRatios"
            )
            listener.onCameraError(e, isCritical = true)
        }

        return isRatioValid
    }

    override fun takePicture() =
        if (config.autoFocus.value == Modes.AutoFocus.AF_OFF || videoManager.isVideoRecording) captureStillPicture()
        else lockFocus()

    /**
     * Chooses a camera ID by the specified camera facing ([CameraConfiguration.facing]).
     *
     * This rewrites [cameraId], [cameraCharacteristics], and optionally
     * [CameraConfiguration.facing].
     */
    private fun chooseCameraIdByFacing(): Boolean = runCatching {

        cameraManager.cameraIdList.run {
            ifEmpty { throw CameraViewException("No camera available.") }
            forEach { id ->
                val characteristics = cameraManager.getCameraCharacteristics(id)
                val level = characteristics.get(
                    CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)
                if (level == null ||
                    level == CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY) return@forEach
                val internal = characteristics.get(CameraCharacteristics.LENS_FACING)
                    ?: throw NullPointerException("Unexpected state: LENS_FACING null")
                if (internal == internalFacing) {
                    cameraId = id
                    cameraCharacteristics = characteristics
                    return@runCatching true
                }
            }
            // Not found
            cameraId = get(0)
        }

        cameraCharacteristics = cameraManager.getCameraCharacteristics(cameraId)

        val level = cameraCharacteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)

        if (level == null || level == CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY) {
            return@runCatching false
        }

        val internal = cameraCharacteristics.get(CameraCharacteristics.LENS_FACING)
            ?: throw NullPointerException("Unexpected state: LENS_FACING null")

        for (i in 0 until internalFacings.size()) {
            if (internalFacings.valueAt(i) == internal) {
                config.facing.value = internalFacings.keyAt(i)
                return@runCatching true
            }
        }

        // The operation can reach here when the only camera device is an external one.
        // We treat it as facing back.
        config.facing.value = Modes.Facing.FACING_BACK
        return@runCatching true
    }
        .getOrElse {
            listener.onCameraError(CameraViewException("Failed to get a list of camera devices", it))
            return@getOrElse false
        }
        .also { if (it) videoManager.setCameraCharacteristics(cameraCharacteristics) }

    /**
     * This will choose a camera based on a passed in cameraId
     * Called from [start(cameraId)]
     */
    private fun chooseCameraById(cameraId: String): Boolean {
        if (cameraManager.cameraIdList.contains(cameraId)) {
            val cameraCharacteristics = cameraManager.getCameraCharacteristics(cameraId)
            val level = cameraCharacteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)
            if (level != null &&
                    level != CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY) {
                this.cameraId = cameraId
                this.cameraCharacteristics = cameraCharacteristics
                return true
            }
        }
        return false
    }

    /**
     * Collects some information from [cameraCharacteristics].
     *
     * This rewrites [previewSizes], [pictureSizes], and optionally,
     * [CameraConfiguration.aspectRatio] in [config].
     */
    private fun collectCameraInfo() {

        val map = cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            ?: run {
                listener.onCameraError(IllegalStateException("Failed to get configuration map for camera id $cameraId"))
                return
            }

        previewSizes.clear()

        map.getOutputSizes(preview.outputClass).forEach {
            if (it.width <= maxPreviewWidth && it.height <= maxPreviewHeight) {
                previewSizes.add(Size(it.width, it.height))
            }
        }

        pictureSizes.clear()

        collectPictureSizes(pictureSizes, map)

        supportedAspectRatios.forEach {
            if (!pictureSizes.ratios().contains(it)) previewSizes.remove(it)
        }

        if (!supportedAspectRatios.contains(config.aspectRatio.value)) {
            config.aspectRatio.value = supportedAspectRatios.iterator().next()
        }

        videoManager.updateVideoSizes(map)
    }

    protected open fun collectPictureSizes(sizes: SizeMap, map: StreamConfigurationMap) {
        map.getOutputSizes(internalOutputFormat).forEach { pictureSizes.add(Size(it.width, it.height)) }
    }

    private fun prepareImageReaders() {

        captureImageReader?.close()
        previewImageReader?.close()

        captureImageReader = run {
            val largestPicture = pictureSizes.sizes(config.aspectRatio.value).last()
            ImageReader.newInstance(
                largestPicture.width,
                largestPicture.height,
                internalOutputFormat,
                2 // maxImages
            ).apply { setOnImageAvailableListener(onCaptureImageAvailableListener, backgroundHandler) }
        }

        previewImageReader = run {
            val largestPreview = previewSizes.sizes(config.aspectRatio.value).last()
            ImageReader.newInstance(
                largestPreview.width,
                largestPreview.height,
                ImageFormat.YUV_420_888,
                2 // maxImages
            ).apply { setOnImageAvailableListener(onPreviewImageAvailableListener, backgroundHandler) }
        }
    }

    /** Starts opening a camera device. The result will be processed in [cameraDeviceCallback]. */
    @SuppressLint("MissingPermission")
    private fun startOpeningCamera() {
        try {
            cameraManager.openCamera(cameraId, cameraDeviceCallback, backgroundHandler)
        } catch (e: CameraAccessException) {
            listener.onCameraError(CameraViewException("Failed to open camera with id $cameraId", e))
        } catch (e: IllegalArgumentException) {
            listener.onCameraError(CameraViewException("Failed to open camera with id $cameraId", e))
        } catch (e: SecurityException) {
            listener.onCameraError(CameraViewException("Camera permissions not granted", e))
        }
    }

    /**
     * Starts a capture session for camera preview.
     * This rewrites [previewRequestBuilder].
     * The result will be continuously processed in [previewSessionStateCallback].
     */
    private fun startPreviewCaptureSession() {

        if (!isCameraOpened || !preview.isReady) {
            listener.onCameraError(CameraViewException("Camera not started or already stopped"))
            return
        }

        with(chooseOptimalSize(Template.Preview)) { preview.setBufferSize(width, height) }

        val template =
            if (config.zsl.value) CameraDevice.TEMPLATE_ZERO_SHUTTER_LAG
            else CameraDevice.TEMPLATE_PREVIEW

        runCatching {
            previewRequestBuilder = camera?.createCaptureRequest(template)
                ?: throw IllegalStateException("Camera not started or already stopped")
        }.onFailure {
            listener.onCameraError(CameraViewException("Failed to start camera session", it))
            return
        }

        val surfaces: MutableList<Surface> = runCatching { setupSurfaces(previewRequestBuilder) }
            .getOrElse {
                listener.onCameraError(CameraViewException(cause = it))
                return
            }

        lifecycleRegistry.markState(Lifecycle.State.STARTED)

        runCatching { camera?.createCaptureSession(surfaces, previewSessionStateCallback, backgroundHandler) }
            .onFailure { listener.onCameraError(CameraViewException("Failed to start camera session", it)) }
    }

    @Throws(IllegalStateException::class)
    private fun setupSurfaces(
        captureRequestBuilder: CaptureRequest.Builder,
        shouldAddMediaRecorderSurface: Boolean = false
    ): MutableList<Surface> {

        val surfaces: MutableList<Surface> = mutableListOf()

        // Setup preview surface
        preview.surface
            ?.let {
                surfaces.add(it)
                captureRequestBuilder.addTarget(it)
            }
            ?: throw IllegalStateException("Preview surface not available")

        // Setup capture image reader surface
        if (config.isSingleCaptureModeEnabled) captureImageReader?.surface
            ?.let { surfaces.add(it) }
            ?: listener.onCameraError(CameraViewException("Capture image reader surface not available. Image capture is not available."))

        // Setup preview image reader surface
        if (config.isContinuousFrameModeEnabled) previewImageReader?.surface
            ?.let {
                surfaces.add(it)
                captureRequestBuilder.addTarget(it)
            }
            ?: listener.onCameraError(CameraViewException("Preview image reader surface not available. Preview frames are not available."))

        if (shouldAddMediaRecorderSurface && config.isVideoCaptureModeEnabled)
            runCatching { videoManager.getRecorderSurface() }
                .onSuccess {
                    surfaces.add(it)
                    captureRequestBuilder.addTarget(it)
                }
                .onFailure { listener.onCameraError(CameraViewException("Media recorder surface not available. Video recording is not available.", it)) }

        return surfaces
    }

    /**
     * Chooses the optimal size for [template] and [aspectRatio] based on respective supported sizes and the surface size.
     *
     * @param template one of the templates from [CameraDevice]
     * @param aspectRatio required aspect ratio for video recording
     * @return The picked optimal size.
     */
    private fun chooseOptimalSize(
        template: Template,
        aspectRatio: AspectRatio = config.aspectRatio.value
    ): Size {

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

        val candidates = when (template) {
            Template.Preview -> previewSizes.sizes(aspectRatio)
            Template.Record -> videoManager.getCandidatesForOptimalSize(aspectRatio)
        }

        // Pick the smallest of those big enough
        candidates.firstOrNull { it.width >= surfaceLonger && it.height >= surfaceShorter }
            ?.also { return it }

        // If no size is big enough, pick the largest one.
        return candidates.last()
    }

    /**
     * Updates [CaptureRequest.SCALER_CROP_REGION] to crop region rect for
     * [CameraConfiguration.currentDigitalZoom] value from [config].
     */
    private fun updateScalerCropRegion(): Boolean {
        (if (isVideoRecording) videoManager.getRequestBuilder() else previewRequestBuilder).set(
            CaptureRequest.SCALER_CROP_REGION,
            digitalZoom.getCropRegionForZoom(config.currentDigitalZoom.value) ?: return false
        )
        return true
    }

    /** Updates the internal state of auto-focus to [CameraConfiguration.autoFocus]. */
    private fun updateAf() {
        if (cameraCharacteristics.isAfSupported(config.autoFocus.value)) {
            previewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, config.autoFocus.value)
        } else {
            listener.onCameraError(
                CameraViewException("Af mode ${config.autoFocus.value} not supported by selected camera. Setting it to off."),
                ErrorLevel.Warning
            )
            config.autoFocus.value = Modes.AutoFocus.AF_OFF
        }
    }

    /** Updates the internal state of flash to [CameraConfiguration.flash]. */
    private fun updateFlash() {
        previewRequestBuilder.apply {
            when (config.flash.value) {
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
        }
    }

    private fun updateAwb() {
        if (cameraCharacteristics.isAwbSupported(config.awb.value)) {
            previewRequestBuilder.set(CaptureRequest.CONTROL_AWB_MODE, config.awb.value)
        } else {
            listener.onCameraError(
                CameraViewException("Awb mode ${config.awb.value} not supported by selected camera. Setting it to off."),
                ErrorLevel.Warning
            )
            config.awb.value = Modes.AutoWhiteBalance.AWB_OFF
        }
    }

    private fun updateOis() {
        if (config.opticalStabilization.value) {
            if (cameraCharacteristics.isOisSupported()) previewRequestBuilder.set(
                CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE,
                CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_ON
            ) else {
                listener.onCameraError(
                    CameraViewException("Optical image stabilization is not supported by selected camera $cameraId. Setting it to off."),
                    ErrorLevel.Warning
                )
                config.opticalStabilization.value = false
            }
        } else previewRequestBuilder.set(
            CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE,
            CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_OFF
        )
    }

    private fun updateNoiseReduction() {
        if (cameraCharacteristics.isNoiseReductionSupported(config.noiseReduction.value)) {
            previewRequestBuilder.set(CaptureRequest.NOISE_REDUCTION_MODE, config.noiseReduction.value)
        } else {
            listener.onCameraError(
                CameraViewException("Noise reduction mode ${config.noiseReduction.value} not supported by selected camera. Setting it to off."),
                ErrorLevel.Warning
            )
            config.noiseReduction.value = Modes.NoiseReduction.NOISE_REDUCTION_OFF
        }
    }

    private fun updateModes() {
        updateScalerCropRegion()
        updateAf()
        updateFlash()
        updateAwb()
        updateOis()
        updateNoiseReduction()
    }

    /** Locks the focus as the first step for a still image capture. */
    private fun lockFocus() {
        previewRequestBuilder.set(
            CaptureRequest.CONTROL_AF_TRIGGER,
            CaptureRequest.CONTROL_AF_TRIGGER_START
        )
        runCatching {
            defaultCaptureCallback.setState(PictureCaptureCallback.STATE_LOCKING)
            captureSession?.capture(
                previewRequestBuilder.build(),
                defaultCaptureCallback,
                backgroundHandler
            )
        }.onFailure { listener.onCameraError(CameraViewException("Failed to lock focus.", it)) }
    }

    // Calculate output orientation based on device sensor orientation.
    private val outputOrientation: Int
        get() {
            val cameraSensorOrientation = cameraCharacteristics.get(CameraCharacteristics.SENSOR_ORIENTATION)
                ?: throw CameraViewException("Camera characteristics not available")

            return (cameraSensorOrientation
                + (deviceRotation * if (config.facing.value == Modes.Facing.FACING_FRONT) 1 else -1)
                + 360) % 360
        }

    /** Captures a still picture. */
    private fun captureStillPicture() {

        try {
            val surface = captureImageReader?.surface
                ?: throw CameraViewException("Image reader surface not available")

            val template = when {
                videoManager.isVideoRecording -> CameraDevice.TEMPLATE_VIDEO_SNAPSHOT
                config.zsl.value -> CameraDevice.TEMPLATE_ZERO_SHUTTER_LAG
                else -> CameraDevice.TEMPLATE_STILL_CAPTURE
            }

            val captureRequestBuilder: CaptureRequest.Builder =
                (camera?.createCaptureRequest(template)
                    ?: throw IllegalStateException("Camera not started or already stopped"))

            captureRequestBuilder.apply {

                addTarget(surface)

                set(CaptureRequest.SCALER_CROP_REGION, previewRequestBuilder[CaptureRequest.SCALER_CROP_REGION])
                set(CaptureRequest.CONTROL_AF_MODE, previewRequestBuilder[CaptureRequest.CONTROL_AF_MODE])
                set(CaptureRequest.CONTROL_AWB_MODE, previewRequestBuilder[CaptureRequest.CONTROL_AWB_MODE])
                set(
                    CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE,
                    previewRequestBuilder[CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE]
                )
                set(CaptureRequest.NOISE_REDUCTION_MODE, previewRequestBuilder[CaptureRequest.NOISE_REDUCTION_MODE])
                set(CaptureRequest.CONTROL_AE_MODE, previewRequestBuilder[CaptureRequest.CONTROL_AE_MODE])

                val flashMode: Int =
                    if (videoManager.isVideoRecording) CaptureRequest.FLASH_MODE_OFF
                    else previewRequestBuilder[CaptureRequest.FLASH_MODE]
                        ?: CaptureRequest.FLASH_MODE_OFF

                set(CaptureRequest.FLASH_MODE, flashMode)

                if (captureImageReader?.imageFormat == ImageFormat.JPEG) {
                    set(CaptureRequest.JPEG_ORIENTATION, outputOrientation)
                }

                set(CaptureRequest.JPEG_QUALITY, jpegQuality.toByte())
            }

            // Stop preview and capture a still picture.
            if (!videoManager.isVideoRecording) captureSession?.stopRepeating()
            captureSession?.capture(captureRequestBuilder.build(), stillCaptureCallback, backgroundHandler)
        } catch (e: Exception) {
            listener.onCameraError(CameraViewException("Cannot capture a still picture.", e))
        }
    }

    override fun startVideoRecording(outputFile: File, videoConfig: VideoConfiguration) {

        runCatching {
            videoManager.setupMediaRecorder(
                outputFile,
                videoConfig,
                parseVideoSize(videoConfig.videoSize),
                outputOrientation
            )
        }
            .onFailure {
                listener.onCameraError(CameraViewException(cause = it))
                return
            }

        if (!isCameraOpened || !preview.isReady) {
            listener.onCameraError(CameraViewException("Camera not started or already stopped"))
            return
        }

        with(chooseOptimalSize(Template.Preview)) { preview.setBufferSize(width, height) }

        runCatching { videoManager.setupVideoRequestBuilder(previewRequestBuilder, config, videoConfig) }
            .onFailure {
                listener.onCameraError(CameraViewException(cause = it))
                return
            }

        val surfaces: MutableList<Surface> = runCatching {
            setupSurfaces(
                videoManager.getRequestBuilder(),
                shouldAddMediaRecorderSurface = true
            )
        }.getOrElse {
            listener.onCameraError(CameraViewException(cause = it))
            return
        }

        camera?.createCaptureSession(surfaces, videoSessionStateCallback, backgroundHandler)
    }

    override fun pauseVideoRecording(): Boolean {
        listener.onCameraError(UnsupportedOperationException("Video pausing and resuming is only supported on API 24 and higher"))
        return false
    }

    override fun resumeVideoRecording(): Boolean {
        listener.onCameraError(UnsupportedOperationException("Video pausing and resuming is only supported on API 24 and higher"))
        return false
    }

    override fun stopVideoRecording(): Boolean = runCatching { videoManager.stopVideoRecording() }
        .getOrElse {
            listener.onCameraError(CameraViewException(cause = it))
            false
        }
        .also {
            listener.onVideoRecordStopped(it)
            releaseCaptureSession()
            startPreviewCaptureSession()
        }

    /**
     * Unlocks the auto-focus and restart camera preview. This is supposed to be called after
     * capturing a still picture.
     */
    private fun unlockFocus() {
        previewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_CANCEL)
        try {
            captureSession?.capture(
                previewRequestBuilder.build(),
                defaultCaptureCallback,
                backgroundHandler
            )
            updateModes()
            previewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_IDLE)
            captureSession?.setRepeatingRequest(
                previewRequestBuilder.build(),
                defaultCaptureCallback,
                backgroundHandler
            )
            defaultCaptureCallback.setState(PictureCaptureCallback.STATE_PREVIEW)
        } catch (e: Exception) {
            listener.onCameraError(CameraViewException("Failed to restart camera preview.", e))
        }
    }

    /**
     * Parse the video size from popular [VideoSize] choices. If the [VideoSize]
     * is not supported then an optimal size sill be chosen.
     */
    private fun parseVideoSize(size: VideoSize): Size = when (size) {

        VideoSize.Max16x9 -> chooseOptimalSize(Template.Record, AspectRatio.Ratio16x9)

        VideoSize.Max4x3 -> chooseOptimalSize(Template.Record, AspectRatio.Ratio4x3)

        VideoSize.P1080 -> when (videoManager.isSize1080pSupported) {
            false -> chooseOptimalSize(Template.Record)
            true -> Size.P1080
        }

        VideoSize.P720 -> when (videoManager.isSize720pSupported) {
            false -> chooseOptimalSize(Template.Record)
            true -> Size.P720
        }

        else -> chooseOptimalSize(Template.Record)
    }

    internal sealed class Template {
        object Preview : Template()
        object Record : Template()
    }
}