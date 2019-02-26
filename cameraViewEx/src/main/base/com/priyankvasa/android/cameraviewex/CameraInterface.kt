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
import kotlinx.coroutines.runBlocking
import java.io.File

internal interface CameraInterface : LifecycleOwner, CoroutineScope {

    val isActive: Boolean

    val isCameraOpened: Boolean

    val isVideoRecording: Boolean

    val supportedAspectRatios: Set<AspectRatio>

    var deviceRotation: Int

    val maxDigitalZoom: Float

    /**
     * @return `true` if the implementation was able to start the camera session.
     */
    suspend fun start(): Boolean

    suspend fun stop() {
        if (isVideoRecording) stopVideoRecording()
    }

    suspend fun destroy() {
        runBlocking(coroutineContext) { stop() }
    }

    /**
     * @return `true` if the aspect ratio was changed.
     */
    suspend fun setAspectRatio(ratio: AspectRatio): Boolean

    suspend fun takePicture()

    suspend fun startVideoRecording(outputFile: File, videoConfig: VideoConfiguration)

    fun pauseVideoRecording(): Boolean

    fun resumeVideoRecording(): Boolean

    suspend fun stopVideoRecording(): Boolean

    interface Listener {
        fun onCameraOpened()
        fun onCameraClosed()
        fun onPictureTaken(imageData: ByteArray)
        fun onVideoRecordStarted()
        fun onVideoRecordStopped(isSuccess: Boolean)
        fun onCameraError(e: Exception, errorLevel: ErrorLevel = ErrorLevel.Error)
        fun onLegacyPreviewFrame(image: LegacyImage)
        @RequiresApi(Build.VERSION_CODES.KITKAT)
        fun onPreviewFrame(reader: ImageReader)
    }
}