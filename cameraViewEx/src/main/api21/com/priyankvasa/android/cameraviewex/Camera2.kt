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
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.renderscript.RenderScript
import android.util.SparseIntArray
import android.view.Surface
import com.priyankvasa.android.cameraviewex.exif.ExifInterface
import com.priyankvasa.android.cameraviewex.extension.chooseOptimalSize
import com.priyankvasa.android.cameraviewex.extension.cropHeight
import com.priyankvasa.android.cameraviewex.extension.cropWidth
import com.priyankvasa.android.cameraviewex.extension.isAfSupported
import com.priyankvasa.android.cameraviewex.extension.isAwbSupported
import com.priyankvasa.android.cameraviewex.extension.isHardwareLevelSupported
import com.priyankvasa.android.cameraviewex.extension.isNoiseReductionSupported
import com.priyankvasa.android.cameraviewex.extension.isOisSupported
import com.priyankvasa.android.cameraviewex.extension.isVideoStabilizationSupported
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File
import java.util.SortedSet
import java.util.TreeSet
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.CoroutineContext
import kotlin.math.roundToInt

@TargetApi(Build.VERSION_CODES.LOLLIPOP)
internal open class Camera2(
    protected val listener: CameraInterface.Listener,
    private val preview: PreviewImpl,
    private val config: CameraConfiguration,
    private val cameraJob: Job,
    context: Context
) : CameraInterface {

    final override val coroutineContext: CoroutineContext get() = Dispatchers.Default + cameraJob

    private val lifecycleRegistry: LifecycleRegistry by lazy {
        LifecycleRegistry(this).also { it.markState(Lifecycle.State.CREATED) }
    }

    init {
        preview.surfaceChangeListener = { if (isCameraOpened) startPreview() }
        addObservers()
    }

    /** A [Semaphore] to prevent concurrent opening or closing camera. */
    private val cameraOpenCloseLock: Semaphore by lazy { Semaphore(1, true) }

    /** A [Semaphore] to prevent concurrent starting or stopping preview. */
    private val previewStartStopLock: Semaphore by lazy { Semaphore(1, true) }

    private val imageProcessor: ImageProcessor
        by lazy {
            val contextType: RenderScript.ContextType =
                if (BuildConfig.DEBUG) RenderScript.ContextType.DEBUG
                else RenderScript.ContextType.NORMAL
            ImageProcessor(RenderScript.create(context, contextType, RenderScript.CREATE_FLAG_NONE))
        }

    protected open val internalFacings: SparseIntArray by lazy {
        SparseIntArray().apply {
            put(Modes.Facing.FACING_BACK, CameraCharacteristics.LENS_FACING_BACK)
            put(Modes.Facing.FACING_FRONT, CameraCharacteristics.LENS_FACING_FRONT)
            put(Modes.Facing.FACING_EXTERNAL, 2) // CameraCharacteristics.LENS_FACING_EXTERNAL
        }
    }

    /** An additional thread for running tasks that shouldn't block the UI. */
    private val backgroundThread: HandlerThread
        by lazy { HandlerThread("CameraViewExBackground").also { it.start() } }

    /** A [Handler] for running tasks in the background. */
    private val backgroundHandler: Handler by lazy { Handler(backgroundThread.looper) }

    private val cameraManager: CameraManager
        by lazy { context.getSystemService(Context.CAMERA_SERVICE) as CameraManager }

    private val cameraDeviceCallback: CameraDevice.StateCallback by lazy {

        object : CameraDevice.StateCallback() {

            override fun onOpened(camera: CameraDevice) {
                this@Camera2.camera = camera
                cameraOpenCloseLock.release()
                listener.onCameraOpened()
                if (preview.isReady) startPreview()
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
                listener.onCameraError(
                    CameraViewException("Error opening camera with id ${camera.id} (error: $error)"),
                    ErrorLevel.ErrorCritical
                )
                this@Camera2.camera = null
            }
        }
    }

    private val previewSessionStateCallback: CameraCaptureSession.StateCallback by lazy {

        object : CameraCaptureSession.StateCallback() {

            override fun onConfigured(session: CameraCaptureSession) {

                if (camera == null) return

                try {
                    captureSession?.close()
                    captureSession = session
                    updateModes()
                    captureSession?.setRepeatingRequest(
                        previewRequestBuilder.build(),
                        defaultCaptureCallback,
                        backgroundHandler
                    )
                } catch (e: Exception) {
                    listener.onCameraError(CameraViewException("Failed to start camera preview.", e))
                } finally {
                    previewStartStopLock.release()
                }
            }

            override fun onConfigureFailed(session: CameraCaptureSession) {
                listener.onCameraError(CameraViewException("Failed to configure capture session."))
            }

            override fun onClosed(session: CameraCaptureSession) {
                if (captureSession != null && captureSession == session) captureSession = null
            }
        }
    }

    private val videoSessionStateCallback: CameraCaptureSession.StateCallback by lazy {

        object : CameraCaptureSession.StateCallback() {

            override fun onConfigured(session: CameraCaptureSession) {

                runCatching {
                    captureSession?.close()
                    captureSession = session
                    captureSession?.setRepeatingRequest(
                        videoRequestBuilder.build(),
                        null,
                        backgroundHandler
                    )
                    videoManager.startMediaRecorder()
                }
                    .onSuccess { listener.onVideoRecordStarted() }
                    .onFailure { listener.onCameraError(CameraViewException("Unable to start video recording", it)) }
            }

            override fun onConfigureFailed(session: CameraCaptureSession) {
                listener.onCameraError(CameraViewException("Failed to configure video capture session."))
            }
        }
    }

    private val defaultCaptureCallback: PictureCaptureCallback by lazy {

        object : PictureCaptureCallback() {

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
    }

    private val stillCaptureCallback: CameraCaptureSession.CaptureCallback by lazy {

        object : CameraCaptureSession.CaptureCallback() {

            override fun onCaptureStarted(
                session: CameraCaptureSession,
                request: CaptureRequest,
                timestamp: Long,
                frameNumber: Long
            ) {
                launch(Dispatchers.Main) { preview.shutterView.show() }
            }

            override fun onCaptureCompleted(
                session: CameraCaptureSession,
                request: CaptureRequest,
                result: TotalCaptureResult
            ) {
                if (!videoManager.isVideoRecording) unlockFocus()
            }
        }
    }

    private var debounceIntervalMillis: Int = 0

    final override var maxPreviewFrameRate: Float = 0f
        set(value) {
            field = value
            debounceIntervalMillis = when {
                value <= 0 -> 0
                value < 1 -> ((1 / value) * 1000).roundToInt()
                else -> (1000 / value).roundToInt()
            }
        }

    private fun isValidOutputOrientation(outputOrientation: Int): Boolean =
        when (outputOrientation) {
            0, 90, 180, 270 -> true
            else -> false
        }

    /**
     * Return output orientation set on receiver jpeg [android.media.Image] by image reader.
     * If image is not in jpeg format, return `-1`.
     */
    private fun android.media.Image.getJpegOutputOrientation(): Int =
        if (format == ImageFormat.JPEG)
            with(planes[0].buffer) {
                rewind()
                val jpegData = ByteArray(remaining())
                get(jpegData)
                ExifInterface().run { if (readExifSafe(jpegData)) rotation else -1 }
            }
        else -1

    /**
     * Set a crop rect if size adjustments required.
     * @return [Pair] of [Boolean] stating if adjustments were made and
     *   [Int] which is the exif rotation set by camera api on the image.
     */
    private fun android.media.Image.setCropRect(
        sensorOutputOrientation: Int,
        imageOutputOrientation: Int
    ): Boolean {

        val isScreenPortrait: Boolean = screenRotation % 180 == 0

        val x: Float = config.aspectRatio.value.x.toFloat()
        val y: Float = config.aspectRatio.value.y.toFloat()

        // `true` when screen rotation is portrait (height > width)
        // and requested output dimensions are landscape (width > height)
        // or vice versa.
        val needOutputSizeAdjustment: Boolean =
            (isScreenPortrait && x > y) || (!isScreenPortrait && y > x)

        if (!needOutputSizeAdjustment) return false

        val ratio: Float = if (isScreenPortrait) y / x else x / y

        // When size adjustment is needed then based on screen rotation
        // decide which dimension should be adjusted, `width` or `height`
        val adjustWidth: Boolean = deviceRotation % 180 != imageOutputOrientation % 180

        val (iWidth, iHeight) =
            if (imageOutputOrientation != sensorOutputOrientation) height to width
            else width to height

        // Set the crop rect with new adjusted dimensions which will be used later to produce final output
        cropRect = Rect(
            0, // left
            0, // top
            if (adjustWidth) (iHeight * ratio).roundToInt() else iWidth, // right
            if (!adjustWidth) (iWidth * ratio).roundToInt() else iHeight // bottom
        )

        return true
    }

    private val onPreviewImageAvailableListener: ImageReader.OnImageAvailableListener by lazy {

        // Timestamp of the last frame processed. Used for de-bouncing purposes.
        val lastTimeStamp = AtomicLong(0L)

        // Flag to decide when to debounce a frame
        fun debounce(): Boolean {

            // Use debounce logic only if interval is > 0. If not, it means user wants max frame rate
            if (debounceIntervalMillis <= 0) return false

            val currentTimeStamp: Long = SystemClock.elapsedRealtime()
            // Set debounce flag to true if current timestamp is within debounce interval
            if (debounceIntervalMillis > currentTimeStamp - lastTimeStamp.get()) {
                return true
            }
            // Otherwise update last frame timestamp to current
            lastTimeStamp.set(currentTimeStamp)
            return false
        }

        return@lazy ImageReader.OnImageAvailableListener { reader: ImageReader ->

            launch {

                val internalImage: android.media.Image =
                    // acquireNextImage() is nullable and throws exception if max images already acquired
                    runCatching { reader.acquireNextImage() }.getOrNull() ?: return@launch

                // The reason for using flag instead of de-bouncing right from beginning is because
                // once this listener is called, image must be acquired from image reader otherwise preview will freeze.
                // After acquiring image if debounce is true, close the image and return.
                if (debounce()) {
                    internalImage.close()
                    return@launch
                }

                runCatching { internalImage.checkValidYuv() }
                    .onFailure {
                        internalImage.close()
                        return@launch
                    }

                val outputOrientation: Int = getSensorOutputOrientation()

                internalImage.setCropRect(outputOrientation, outputOrientation)

                val imageData: ByteArray =
                    runCatching { imageProcessor.decode(internalImage, Modes.OutputFormat.YUV_420_888) }
                        .getOrElse {
                            internalImage.close()
                            return@launch
                        }

                val exif: ExifInterface = ExifInterface().apply { rotation = outputOrientation }

                internalImage
                    .runCatching { Image(imageData, cropWidth, cropHeight, exif, Modes.OutputFormat.YUV_420_888) }
                    .onSuccess { image: Image -> listener.onPreviewFrame(image) }

                internalImage.close()
            }
        }
    }

    private val onCaptureImageAvailableListener: ImageReader.OnImageAvailableListener by lazy {

        ImageReader.OnImageAvailableListener { reader: ImageReader ->

            val internalImage: android.media.Image =
                runCatching {
                    reader.acquireLatestImage()
                        ?: throw NullPointerException("No new image is available.")
                }
                    .getOrElse {
                        listener.onCameraError(CameraViewException("Failed to capture image.", it))
                        return@OnImageAvailableListener
                    }

            if (internalImage.planes.isEmpty()) {
                internalImage.close()
                listener.onCameraError(CameraViewException("Failed to capture image. Invalid format or internal image data."))
                return@OnImageAvailableListener
            }

            val sensorOutputOrientation: Int = getSensorOutputOrientation()
            val imageOutputOrientation: Int = internalImage.getJpegOutputOrientation()
                .takeIf(::isValidOutputOrientation)
                ?: sensorOutputOrientation

            val isImageSizeAdjusted: Boolean =
                internalImage.setCropRect(sensorOutputOrientation, imageOutputOrientation)

            val imageData: ByteArray =
                runCatching { imageProcessor.decode(internalImage, config.outputFormat.value) }
                    .getOrElse {
                        internalImage.close()
                        listener.onCameraError(CameraViewException("Failed to capture image.", it))
                        return@OnImageAvailableListener
                    }

            val exif = ExifInterface()

            val isExifAlreadyRead: Boolean
                by lazy { internalImage.format == ImageFormat.JPEG && exif.readExifSafe(imageData) }

            val finalImageData: ByteArray =
                if (isImageSizeAdjusted || !isExifAlreadyRead) {
                    exif.rotation = imageOutputOrientation
                    if (internalImage.format == ImageFormat.JPEG) exif.writeExif(imageData)
                    else imageData
                } else {
                    imageData
                }

            val image = Image(
                finalImageData,
                internalImage.cropWidth,
                internalImage.cropHeight,
                exif,
                config.outputFormat.value
            )

            listener.onPictureTaken(image)

            internalImage.close()
        }
    }

    final override lateinit var cameraId: String private set

    /**
     * Gets a list of camera ids for the current facing direction
     */
    override val cameraIdsForFacing: SortedSet<String>
        get() = cameraManager.cameraIdList
            .asSequence()
            .filter { id: String ->
                val characteristics: CameraCharacteristics = cameraManager.getCameraCharacteristics(id)
                characteristics.isHardwareLevelSupported() &&
                    characteristics[CameraCharacteristics.LENS_FACING] == internalFacing
            }
            .mapTo(TreeSet()) { it }

    private lateinit var cameraCharacteristics: CameraCharacteristics

    private var camera: CameraDevice? = null

    private var captureSession: CameraCaptureSession? = null

    protected val videoManager: VideoManager
        by lazy { VideoManager { listener.onCameraError(CameraViewException(it), ErrorLevel.Warning) } }

    private lateinit var previewRequestBuilder: CaptureRequest.Builder

    private lateinit var videoRequestBuilder: CaptureRequest.Builder

    private var singleCaptureReader: ImageReader? = null

    private var continuousFrameReader: ImageReader? = null

    private val previewSizes: SizeMap by lazy { SizeMap() }

    private val pictureSizes: SizeMap by lazy { SizeMap() }

    protected val internalOutputFormat: Int
        get() = when (config.outputFormat.value) {
            Modes.OutputFormat.JPEG -> ImageFormat.JPEG
            Modes.OutputFormat.RGBA_8888 -> ImageFormat.YUV_420_888
            Modes.OutputFormat.YUV_420_888 -> ImageFormat.YUV_420_888
            else -> Modes.DEFAULT_OUTPUT_FORMAT
        }

    // 0, 90, 180, or 270
    final override var deviceRotation: Int = 0

    // 0, 90, 180, or 270
    final override var screenRotation: Int = 0

    final override val isActive: Boolean
        get() = cameraJob.isActive && backgroundHandler.looper?.thread?.isAlive == true

    final override val isCameraOpened: Boolean get() = camera != null

    final override val isVideoRecording: Boolean get() = videoManager.isVideoRecording

    private val internalFacing: Int get() = internalFacings[config.facing.value]

    final override val supportedAspectRatios: Set<AspectRatio> get() = previewSizes.ratios()

    private val digitalZoom: DigitalZoom by lazy { DigitalZoom { cameraCharacteristics } }

    final override val maxDigitalZoom: Float get() = digitalZoom.maxZoom

    private var manualFocusEngaged = false

    private val isMeteringAreaAFSupported: Boolean
        get() = cameraCharacteristics[CameraCharacteristics.CONTROL_MAX_REGIONS_AF] ?: 0 > 0

    private val isMeteringAreaAESupported: Boolean
        get() = cameraCharacteristics[CameraCharacteristics.CONTROL_MAX_REGIONS_AE] ?: 0 > 0

    private val isMeteringAreaAWBSupported: Boolean
        get() = cameraCharacteristics[CameraCharacteristics.CONTROL_MAX_REGIONS_AWB] ?: 0 > 0

    private val previewSurfaceTappedListener: (x: Float, y: Float) -> Boolean by lazy {
        listener@{ x: Float, y: Float ->

            val requestBuilder: CaptureRequest.Builder =
                if (isVideoRecording) videoRequestBuilder else previewRequestBuilder

            if (!isMeteringAreaAFSupported || manualFocusEngaged) return@listener false

            val sensorRect: Rect = cameraCharacteristics
                .get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)
                ?: return@listener false

            val tapRect: Rect = preview.calculateTouchAreaRect(
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
                    val afState: Int? = result.get(CaptureResult.CONTROL_AF_STATE)
                    val aeState: Int? = result.get(CaptureResult.CONTROL_AE_STATE)
                    val awbState: Int? = result.get(CaptureResult.CONTROL_AWB_STATE)

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
                    }.onFailure { listener.onCameraError(CameraViewException("Failed to restart camera preview.", it)) }

                    manualFocusEngaged = false

                    launch(Dispatchers.Main) { preview.removeOverlay() }
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

            }.onFailure {
                listener.onCameraError(CameraViewException("Failed to lock focus.", it))
                return@listener false
            }

            return@listener true
        }
    }

    private val previewSurfacePinchedListener: (scaleFactor: Float) -> Boolean by lazy {
        { scaleFactor: Float ->
            config.currentDigitalZoom.value = digitalZoom.getZoomForScaleFactor(scaleFactor)
            true
        }
    }

    // Calculate output orientation based on device sensor orientation.
    @Throws(IllegalStateException::class)
    private fun getSensorOutputOrientation(): Int {

        val cameraSensorOrientation: Int = cameraCharacteristics[CameraCharacteristics.SENSOR_ORIENTATION]
            ?: throw IllegalStateException("Camera characteristics not available")

        return (cameraSensorOrientation
            + (deviceRotation * if (config.facing.value == Modes.Facing.FACING_FRONT) 1 else -1)
            + 360) % 360
    }

    final override fun getLifecycle(): Lifecycle = lifecycleRegistry

    private fun addObservers(): Unit = config.run {

        cameraMode.observe(this@Camera2) {
            config.currentDigitalZoom.value = 1f
            restartPreview()
        }

        continuousFrameSize.observe(this@Camera2) { restartPreview() }

        singleCaptureSize.observe(this@Camera2) { restartPreview() }

        outputFormat.observe(this@Camera2) { prepareSingleCaptureReader() }

        facing.observe(this@Camera2) {
            if (!isCameraOpened) return@observe
            stop()
            start(Modes.DEFAULT_CAMERA_ID)
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
                    (if (isVideoRecording) videoRequestBuilder else previewRequestBuilder).build(),
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

        zsl.observe(this@Camera2) { restartPreview() }
    }

    private fun restartPreview() {
        if (!isCameraOpened) return
        stopPreview()
        startPreview()
    }

    /** Stops the background thread and its [Handler]. */
    private fun stopBackgroundThread() = runCatching {
        backgroundThread.quitSafely()
        backgroundThread.join()
    }.getOrElse {
        listener.onCameraError(CameraViewException("Background thread was interrupted.", it))
    }

    /**
     * Start camera with given [cameraId].
     * If [cameraId] is [Modes.DEFAULT_CAMERA_ID] then
     * camera is selected based on provided facing from [CameraConfiguration.facing]
     */
    @SuppressLint("MissingPermission")
    final override fun start(cameraId: String): Boolean = runCatching {
        if (!cameraOpenCloseLock.tryAcquire()) return@runCatching false
        when (cameraId) {
            Modes.DEFAULT_CAMERA_ID -> chooseCameraIdByFacing()
            else -> setCameraId(cameraId)
        }
        collectCameraInfo()
        prepareSingleCaptureReader()
        cameraManager.openCamera(this.cameraId, cameraDeviceCallback, backgroundHandler)
        return@runCatching true
    }
        .getOrElse {
            cameraOpenCloseLock.release()
            when (it) {
                is SecurityException -> listener.onCameraError(CameraViewException("Camera permissions not granted", it))
                else -> listener.onCameraError(CameraViewException("Failed to open camera with id '$cameraId'", it))
            }
            return@getOrElse false
        }

    final override fun stop() {
        try {
            if (!cameraOpenCloseLock.tryAcquire()) return
            super.stop()
            stopPreview()
            camera?.close()
            camera = null
            singleCaptureReader?.close()
            singleCaptureReader = null
            continuousFrameReader?.close()
            continuousFrameReader = null
            videoManager.release()
        } catch (e: InterruptedException) {
            listener.onCameraError(CameraViewException("Interrupted while trying to lock camera closing.", e))
        } finally {
            cameraOpenCloseLock.release()
        }
    }

    private fun stopPreview() {
        if (!previewStartStopLock.tryAcquire()) return
        captureSession?.runCatching {
            stopRepeating()
            abortCaptures()
        }
        captureSession?.close()
        captureSession = null
        previewStartStopLock.release()
    }

    final override fun destroy() {
        cameraJob.cancel()
        super.destroy()
        stopBackgroundThread()
    }

    final override fun setAspectRatio(ratio: AspectRatio) {
        prepareSingleCaptureReader()
        restartPreview()
    }

    final override fun takePicture() {
        if (config.autoFocus.value == Modes.AutoFocus.AF_OFF || videoManager.isVideoRecording) captureStillPicture()
        else lockFocus()
    }

    /**
     * Returns a camera ID next to current [cameraId] for facing ([CameraConfiguration.facing]).
     *
     * If current camera ID has a different facing then what is set currently
     * then this method will return the first camera ID for set facing.
     *
     * Camera IDs are grouped by facing and sorted by string comparision.
     */
    override fun getNextCameraId(): String {

        val sortedIds: SortedSet<String> = cameraIdsForFacing

        // For invalid `cameraId`, new index will be -1 + 1 = 0 ie. first index of the group
        val newIdIndex: Int = sortedIds.indexOf(cameraId) + 1

        return if (newIdIndex in 0 until sortedIds.size) sortedIds.elementAt(newIdIndex) else Modes.DEFAULT_CAMERA_ID
    }

    /**
     * Chooses a camera ID by the specified camera facing ([CameraConfiguration.facing]).
     *
     * This rewrites [cameraId] and [cameraCharacteristics]
     */
    @Throws(IllegalStateException::class, UnsupportedOperationException::class)
    private fun chooseCameraIdByFacing() {

        cameraManager.cameraIdList.run {

            ifEmpty { throw IllegalStateException("No camera available.") }

            forEach { id: String ->

                val characteristics: CameraCharacteristics =
                    runCatching { cameraManager.getCameraCharacteristics(id) }
                        .getOrElse { return@forEach }

                if (!characteristics.isHardwareLevelSupported()) return@forEach

                val facing: Int =
                    characteristics[CameraCharacteristics.LENS_FACING] ?: return@forEach

                if (facing == internalFacing) {
                    cameraId = id
                    cameraCharacteristics = characteristics
                    return
                }
            }

            throw UnsupportedOperationException("No supported camera found for facing $internalFacing")
        }
    }

    /**
     * Selects camera with id [id]
     *
     * This rewrites [cameraId], [cameraCharacteristics], and optionally
     * [CameraConfiguration.facing].
     */
    @Throws(
        IllegalStateException::class,
        UnsupportedOperationException::class,
        IllegalArgumentException::class,
        CameraAccessException::class
    )
    private fun setCameraId(id: String) {

        val characteristics: CameraCharacteristics = cameraManager.getCameraCharacteristics(id)

        if (!characteristics.isHardwareLevelSupported())
            throw UnsupportedOperationException(
                "Camera with id $id has legacy hardware level," +
                    " which is not supported by ${this@Camera2} implementation."
            )

        characteristics[CameraCharacteristics.LENS_FACING]
            ?: throw IllegalStateException("Unexpected state: LENS_FACING null")

        cameraId = id
        cameraCharacteristics = characteristics
    }

    /**
     * Collects size information from [cameraCharacteristics].
     *
     * This rewrites [previewSizes] and [pictureSizes].
     *
     * @throws IllegalStateException when camera configuration map is null
     */
    @Throws(IllegalStateException::class)
    private fun collectCameraInfo() {

        val map: StreamConfigurationMap = cameraCharacteristics[CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP]
            ?: throw IllegalStateException("Failed to get configuration map for camera id $cameraId")

        // Setup preview sizes
        previewSizes.clear()
        // Collect all supported preview sizes
        map.getOutputSizes(preview.outputClass)
            ?.forEach { previewSizes.add(it.width, it.height) }

        // Setup picture sizes
        pictureSizes.clear()
        // Collect all supported picture sizes
        collectPictureSizes(pictureSizes, map)

        // Collect all supported video sizes
        map.getOutputSizes(MediaRecorder::class.java)
            ?.asSequence()
            ?.map { Size(it.width, it.height) }
            ?.let { videoManager.addVideoSizes(it) }

        // Remove all `preview` sizes for ratio which is not a supported `picture` ratio
        supportedAspectRatios.forEach {
            if (!pictureSizes.ratios().contains(it)) previewSizes.remove(it)
        }
    }

    protected open fun collectPictureSizes(sizes: SizeMap, map: StreamConfigurationMap) {
        map.getOutputSizes(internalOutputFormat).forEach { pictureSizes.add(it.width, it.height) }
    }

    private fun prepareContinuousFrameReader() {

        continuousFrameReader?.close()

        val (width: Int, height: Int) =
            previewSizes.chooseOptimalSize(
                config.continuousFrameSize.value,
                config.sensorAspectRatio
            )
                ?: Size(preview.width, preview.height)

        continuousFrameReader =
            ImageReader.newInstance(
                width,
                height,
                ImageFormat.YUV_420_888,
                4 // maxImages
            ).apply { setOnImageAvailableListener(onPreviewImageAvailableListener, backgroundHandler) }
    }

    private fun prepareSingleCaptureReader() {

        singleCaptureReader?.close()

        val (pictureWidth: Int, pictureHeight: Int) =
            pictureSizes.chooseOptimalSize(
                config.singleCaptureSize.value,
                config.sensorAspectRatio
            )
                ?: run {
                    listener.onCameraError(
                        CameraViewException("No supported picture size found for this camera. Try reopening this camera or use another one."),
                        ErrorLevel.Error
                    )
                    return
                }

        singleCaptureReader =
            ImageReader.newInstance(
                pictureWidth,
                pictureHeight,
                internalOutputFormat,
                2 // maxImages
            ).apply { setOnImageAvailableListener(onCaptureImageAvailableListener, backgroundHandler) }
    }

    /**
     * Starts a capture session for camera preview.
     * This rewrites [previewRequestBuilder].
     * The result will be continuously processed in [previewSessionStateCallback].
     */
    private fun startPreview() {

        if (!previewStartStopLock.tryAcquire()) return

        if (!isCameraOpened || !preview.isReady) {
            listener.onCameraError(CameraViewException("Camera not started or already stopped"))
            previewStartStopLock.release()
            return
        }

        val (width: Int, height: Int) =
            previewSizes.sizes(config.sensorAspectRatio)
                .runCatching { chooseOptimalSize(preview.width, preview.height) }
                .getOrElse {
                    listener.onCameraError(CameraViewException("No supported preview size available. This camera device (id $cameraId) is not supported.", it))
                    return
                }

        preview.setBufferSize(width, height)

        prepareContinuousFrameReader()

        val template: Int =
            if (config.zsl.value) CameraDevice.TEMPLATE_ZERO_SHUTTER_LAG
            else CameraDevice.TEMPLATE_PREVIEW

        runCatching {
            previewRequestBuilder = camera?.createCaptureRequest(template)
                ?: throw IllegalStateException("Camera not started or already stopped")
        }.getOrElse {
            listener.onCameraError(CameraViewException("Failed to start camera session", it))
            previewStartStopLock.release()
            return
        }

        lifecycleRegistry.markState(Lifecycle.State.STARTED)

        val surfaces: List<Surface> = runCatching { setupSurfaces(previewRequestBuilder) }
            .getOrElse {
                listener.onCameraError(CameraViewException("Unable to setup surfaces.", it))
                previewStartStopLock.release()
                return
            }

        runCatching { camera?.createCaptureSession(surfaces, previewSessionStateCallback, backgroundHandler) }
            .onFailure { listener.onCameraError(CameraViewException("Failed to start camera session", it)) }

        previewStartStopLock.release()
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
            ?: throw IllegalStateException("Preview surface is null")

        // Setup capture image reader surface
        if (config.isSingleCaptureModeEnabled) singleCaptureReader?.surface
            ?.let { surfaces.add(it) }
            ?: throw IllegalStateException("Capture image reader surface is null")

        // Setup preview image reader surface
        if (config.isContinuousFrameModeEnabled) continuousFrameReader?.surface
            ?.let {
                surfaces.add(it)
                captureRequestBuilder.addTarget(it)
            }
            ?: throw CameraViewException("Preview image reader surface is null")

        if (shouldAddMediaRecorderSurface && config.isVideoCaptureModeEnabled) {
            val surface: Surface = videoManager.getRecorderSurface()
            surfaces.add(surface)
            captureRequestBuilder.addTarget(surface)
        }

        return surfaces
    }

    /**
     * Updates [CaptureRequest.SCALER_CROP_REGION] to crop region rect for
     * [CameraConfiguration.currentDigitalZoom] value from [config].
     */
    private fun updateScalerCropRegion(): Boolean {
        (if (isVideoRecording) videoRequestBuilder else previewRequestBuilder).set(
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
                CameraViewException("Af mode ${config.autoFocus.value} not supported by selected camera (id $cameraId). Setting it to off."),
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
                CameraViewException("Awb mode ${config.awb.value} not supported by selected camera (id $cameraId). Setting it to off."),
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
                    CameraViewException("Optical image stabilization is not supported by selected camera (id $cameraId). Setting it to off."),
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
                CameraViewException("Noise reduction mode ${config.noiseReduction.value} not supported by selected camera (id $cameraId). Setting it to off."),
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

    /** Captures a still picture. */
    private fun captureStillPicture() {

        try {
            val surface: Surface = singleCaptureReader?.surface
                ?: throw IllegalStateException("Image reader surface not available")

            val template: Int = when {
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

                if (singleCaptureReader?.imageFormat == ImageFormat.JPEG) {
                    set(CaptureRequest.JPEG_ORIENTATION, getSensorOutputOrientation())
                }

                set(CaptureRequest.JPEG_QUALITY, config.jpegQuality.value.toByte())
            }

            // Stop preview and capture a still picture.
            if (!videoManager.isVideoRecording) captureSession?.stopRepeating()
            captureSession?.capture(captureRequestBuilder.build(), stillCaptureCallback, backgroundHandler)
        } catch (e: Exception) {
            listener.onCameraError(CameraViewException("Unable to capture still picture.", e))
        }
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

    final override fun startVideoRecording(outputFile: File, videoConfig: VideoConfiguration) {

        videoManager.setupMediaRecorder(
            camera?.id?.toIntOrNull(),
            outputFile,
            videoConfig,
            getSensorOutputOrientation(),
            Size(preview.width, preview.height)
        ) { stopVideoRecording() }

        if (!isCameraOpened || !preview.isReady) {
            throw IllegalStateException("Camera not started or already stopped")
        }

        videoRequestBuilder = videoManager.createVideoRequestBuilder(
            camera ?: throw IllegalStateException("Camera not initialized or already stopped"),
            previewRequestBuilder,
            config,
            videoConfig
        ) { cameraCharacteristics.isVideoStabilizationSupported() }

        val surfaces: List<Surface> = setupSurfaces(
            videoRequestBuilder,
            shouldAddMediaRecorderSurface = true
        )

        camera?.createCaptureSession(surfaces, videoSessionStateCallback, backgroundHandler)
            ?: Unit
    }

    override fun pauseVideoRecording(): Boolean {
        listener.onCameraError(CameraViewException("Video pausing and resuming is only supported on API 24 and higher"))
        return false
    }

    override fun resumeVideoRecording(): Boolean {
        listener.onCameraError(CameraViewException("Video pausing and resuming is only supported on API 24 and higher"))
        return false
    }

    final override fun stopVideoRecording(): Boolean = runCatching {
        videoManager.stopVideoRecording()
        return@runCatching true
    }
        .getOrElse {
            listener.onCameraError(CameraViewException("Unable to stop video recording.", it))
            return@getOrElse false
        }
        .also {
            listener.onVideoRecordStopped(it)
            restartPreview()
        }
}