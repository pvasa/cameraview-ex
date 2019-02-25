package com.priyankvasa.android.cameraviewex

import android.media.Image
import android.media.ImageReader
import android.os.Build
import android.support.annotation.RequiresApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import timber.log.Timber
import kotlin.coroutines.CoroutineContext

internal class CameraListenerManager(private val handlerJob: Job) : CameraInterface.Listener, CoroutineScope {

    override val coroutineContext: CoroutineContext get() = Dispatchers.Default + handlerJob

    val cameraOpenedListeners: HashSet<() -> Unit> by lazy { HashSet<() -> Unit>() }

    val pictureTakenListeners: HashSet<(imageData: ByteArray) -> Unit>
        by lazy { HashSet<(imageData: ByteArray) -> Unit>() }

    var legacyPreviewFrameListener: ((image: LegacyImage) -> Unit)? = null

    var previewFrameListener: ((image: Image) -> Unit)? = null

    val cameraErrorListeners: HashSet<(t: Throwable, errorLevel: ErrorLevel) -> Unit>
        by lazy { HashSet<(t: Throwable, errorLevel: ErrorLevel) -> Unit>() }

    val cameraClosedListeners: HashSet<() -> Unit>
        by lazy { HashSet<() -> Unit>() }

    val videoRecordStartedListeners: HashSet<() -> Unit>
        by lazy { HashSet<() -> Unit>() }

    val videoRecordStoppedListeners: HashSet<(isSuccess: Boolean) -> Unit>
        by lazy { HashSet<(isSuccess: Boolean) -> Unit>() }

    private var requestLayoutOnOpen: Boolean = false

    var isEnabled: Boolean = true
        private set

    override fun onCameraOpened() {
        if (requestLayoutOnOpen) requestLayoutOnOpen = false
        cameraOpenedListeners.forEach { launchOnUi { it() } }
    }

    override fun onLegacyPreviewFrame(image: LegacyImage) {
        legacyPreviewFrameListener?.invoke(image)
    }

    @RequiresApi(Build.VERSION_CODES.KITKAT)
    override fun onPreviewFrame(reader: ImageReader) {
        reader.acquireLatestImage()?.use { previewFrameListener?.invoke(it) }
    }

    override fun onPictureTaken(imageData: ByteArray) {
        pictureTakenListeners.forEach { launchOnUi { it(imageData) } }
    }

    override fun onCameraError(e: Exception, errorLevel: ErrorLevel, isCritical: Boolean) {
        if (isCritical && cameraErrorListeners.isEmpty()) throw e
        if (errorLevel == ErrorLevel.Debug) Timber.d(e)
        else cameraErrorListeners.forEach { launchOnUi { it(e, errorLevel) } }
    }

    override fun onCameraClosed() {
        cameraClosedListeners.forEach { launchOnUi { it.invoke() } }
    }

    override fun onVideoRecordStarted() {
        videoRecordStartedListeners.forEach { launchOnUi { it.invoke() } }
    }

    override fun onVideoRecordStopped(isSuccess: Boolean) {
        videoRecordStoppedListeners.forEach { launchOnUi { it.invoke(isSuccess) } }
    }

    fun reserveRequestLayoutOnOpen() {
        requestLayoutOnOpen = true
    }

    fun disable() {
        isEnabled = false
        clear()
    }

    fun clear() {
        cameraOpenedListeners.clear()
        legacyPreviewFrameListener = null
        previewFrameListener = null
        pictureTakenListeners.clear()
        cameraErrorListeners.clear()
        cameraClosedListeners.clear()
        videoRecordStartedListeners.clear()
        videoRecordStoppedListeners.clear()
    }

    fun destroy() {
        clear()
        handlerJob.cancel()
    }

    private inline fun launchOnUi(crossinline block: suspend () -> Unit): Job =
        launch(Dispatchers.Main) { block() }
}