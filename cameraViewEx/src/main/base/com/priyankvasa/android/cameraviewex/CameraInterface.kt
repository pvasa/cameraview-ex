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

import android.annotation.TargetApi
import android.media.ImageReader
import android.os.Build
import androidx.lifecycle.LifecycleOwner
import java.io.File

internal interface CameraInterface : LifecycleOwner {

    val preview: PreviewImpl

    val config: CameraConfiguration

    val listener: Listener

    val isCameraOpened: Boolean

    var isVideoRecording: Boolean

    val supportedAspectRatios: Set<AspectRatio>

    var displayOrientation: Int

    @Modes.JpegQuality
    var jpegQuality: Int

    val maxDigitalZoom: Float

    /**
     * @return `true` if the implementation was able to start the camera session.
     */
    fun start(): Boolean

    /**
     * @return `true` if the implementation was able to start the passed in cameraId
     */
    fun start(cameraId: Int): Boolean

    fun stop()

    /**
     * @return `true` if the aspect ratio was changed.
     */
    fun setAspectRatio(ratio: AspectRatio): Boolean

    fun takePicture()

    fun startVideoRecording(outputFile: File, config: VideoConfiguration)

    @TargetApi(Build.VERSION_CODES.N)
    fun pauseVideoRecording(): Boolean

    @TargetApi(Build.VERSION_CODES.N)
    fun resumeVideoRecording(): Boolean

    fun stopVideoRecording(): Boolean

    fun facingByCameraId(cameraId: Int): Int

    /**
     * Takes in either Modes.Facing.FACING_BACK or FACING_FRONT
     * @return `list of camera ids` that are facing the passed in direction
     */
    fun cameraIdsByFacing(facing: Int): List<Int>

    interface Listener {
        suspend fun onCameraOpened()
        suspend fun onCameraClosed()
        fun onPictureTaken(imageData: ByteArray)
        fun onCameraError(
                e: Exception,
                errorLevel: ErrorLevel = ErrorLevel.Error,
                isCritical: Boolean = false
        )

        @TargetApi(Build.VERSION_CODES.KITKAT)
        fun onPreviewFrame(reader: ImageReader)
    }
}