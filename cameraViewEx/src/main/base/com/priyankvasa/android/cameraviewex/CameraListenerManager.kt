/*
 * Copyright 2019 Priyank Vasa
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.priyankvasa.android.cameraviewex

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import timber.log.Timber
import kotlin.coroutines.CoroutineContext

internal class CameraListenerManager(private val handlerJob: Job) : CameraInterface.Listener, CoroutineScope {

    override val coroutineContext: CoroutineContext get() = Dispatchers.Default + handlerJob

    val cameraOpenedListeners: HashSet<() -> Unit> by lazy { HashSet<() -> Unit>() }

    val pictureTakenListeners: HashSet<(image: Image) -> Unit> by lazy { HashSet<(image: Image) -> Unit>() }

    var continuousFrameListener: ((image: Image) -> Unit)? = null

    val cameraErrorListeners: HashSet<(t: Throwable, errorLevel: ErrorLevel) -> Unit>
        by lazy { HashSet<(t: Throwable, errorLevel: ErrorLevel) -> Unit>() }

    val cameraClosedListeners: HashSet<() -> Unit> by lazy { HashSet<() -> Unit>() }

    val videoRecordStartedListeners: HashSet<() -> Unit> by lazy { HashSet<() -> Unit>() }

    val videoRecordStoppedListeners: HashSet<(isSuccess: Boolean) -> Unit>
        by lazy { HashSet<(isSuccess: Boolean) -> Unit>() }

    private var requestLayoutOnOpen: Boolean = false

    var isEnabled: Boolean = true
        private set

    override fun onCameraOpened() {
        if (requestLayoutOnOpen) requestLayoutOnOpen = false
        cameraOpenedListeners.forEach { launchOnUi { it() } }
    }

    override fun onPreviewFrame(image: Image) {
        continuousFrameListener?.invoke(image)
    }

    override fun onPictureTaken(image: Image) {
        pictureTakenListeners.forEach { launchOnUi { it(image) } }
    }

    override fun onCameraError(e: Exception, errorLevel: ErrorLevel): Unit = when {
        errorLevel == ErrorLevel.ErrorCritical && cameraErrorListeners.isEmpty() -> throw e
        errorLevel == ErrorLevel.Debug -> Timber.d(e)
        else -> cameraErrorListeners.forEach { launchOnUi { it(e, errorLevel) } }
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
        continuousFrameListener = null
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