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

import android.arch.lifecycle.LifecycleOwner
import android.media.ImageReader
import android.os.Build
import android.support.annotation.RequiresApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import java.io.File
import kotlin.coroutines.CoroutineContext

internal interface CameraInterface : LifecycleOwner, CoroutineScope {

    val cameraJob: Job

    override val coroutineContext: CoroutineContext get() = Dispatchers.Main + cameraJob

    val preview: PreviewImpl

    val config: CameraConfiguration

    val listener: Listener

    val isActive: Boolean

    val isCameraOpened: Boolean

    val isVideoRecording: Boolean

    val supportedAspectRatios: Set<AspectRatio>

    var deviceRotation: Int

    @Modes.JpegQuality
    var jpegQuality: Int

    val maxDigitalZoom: Float

    /**
     * @return `true` if the implementation was able to start the camera session.
     */
    fun start(): Boolean

    fun stop() {
        if (isVideoRecording) stopVideoRecording()
    }

    fun destroy() {
        cameraJob.cancel()
        stop()
    }

    /**
     * @return `true` if the aspect ratio was changed.
     */
    fun setAspectRatio(ratio: AspectRatio): Boolean

    fun takePicture()

    fun startVideoRecording(outputFile: File, videoConfig: VideoConfiguration)

    @RequiresApi(Build.VERSION_CODES.N)
    fun pauseVideoRecording(): Boolean

    @RequiresApi(Build.VERSION_CODES.N)
    fun resumeVideoRecording(): Boolean

    fun stopVideoRecording(): Boolean

    interface Listener {
        fun onCameraOpened()
        fun onCameraClosed()
        fun onPictureTaken(imageData: ByteArray)
        fun onVideoRecordStarted()
        fun onVideoRecordStopped(isSuccess: Boolean)
        fun onCameraError(
            e: Exception,
            errorLevel: ErrorLevel = ErrorLevel.Error,
            isCritical: Boolean = false
        )

        @RequiresApi(Build.VERSION_CODES.KITKAT)
        fun onPreviewFrame(reader: ImageReader)
    }
}