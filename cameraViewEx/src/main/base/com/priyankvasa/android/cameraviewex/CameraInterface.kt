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
import kotlinx.coroutines.CoroutineScope
import java.io.File
import java.util.SortedSet

internal interface CameraInterface : LifecycleOwner, CoroutineScope {

    val isActive: Boolean

    val isCameraOpened: Boolean

    val isVideoRecording: Boolean

    val supportedAspectRatios: Set<AspectRatio>

    var deviceRotation: Int

    var screenRotation: Int

    val maxDigitalZoom: Float

    /**
     * Maximum number of frames to generate per second for preview frame listener.
     * Actual frame rate might be less based on device capabilities but will not be more than this value.
     * A float can be set for eg., max frame rate of 0.5f will produce one frame every 2 seconds.
     * Any value less than or equal to zero (<= 0f) will produce maximum frames per second supported by device.
     */
    var maxPreviewFrameRate: Float

    val cameraId: String

    val cameraIdsForFacing: SortedSet<String>

    /**
     * @return `true` if the implementation was able to start the passed in cameraId
     */
    fun start(cameraId: String): Boolean

    fun getNextCameraId(): String

    fun stop() {
        if (isVideoRecording) stopVideoRecording()
    }

    fun destroy() = stop()

    /**
     * @return `true` if the aspect ratio was changed.
     */
    fun setAspectRatio(ratio: AspectRatio)

    fun takePicture()

    fun startVideoRecording(outputFile: File, videoConfig: VideoConfiguration)

    fun pauseVideoRecording(): Boolean

    fun resumeVideoRecording(): Boolean

    fun stopVideoRecording(): Boolean

    interface Listener {
        fun onCameraOpened()
        fun onCameraClosed()
        fun onPictureTaken(image: Image)
        fun onVideoRecordStarted()
        fun onVideoRecordStopped(isSuccess: Boolean)
        fun onCameraError(e: Exception, errorLevel: ErrorLevel = ErrorLevel.Error)
        fun onPreviewFrame(image: Image)
    }
}