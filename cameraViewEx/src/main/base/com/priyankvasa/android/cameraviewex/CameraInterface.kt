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
import android.content.Context
import android.graphics.Rect
import android.graphics.RectF
import android.media.ImageReader
import android.os.Build
import android.support.v4.math.MathUtils
import android.view.View
import java.io.File
import kotlin.math.roundToInt

internal interface CameraInterface {

    val preview: PreviewImpl

    val view: View get() = preview.view

    val context: Context get() = view.context

    val listener: Listener

    val isCameraOpened: Boolean

    val supportedAspectRatios: Set<AspectRatio>

    val aspectRatio: AspectRatio

    @Modes.CameraMode
    var cameraMode: Int

    @Modes.OutputFormat
    var outputFormat: Int

    @Modes.Facing
    var facing: Int

    var autoFocus: Boolean

    var touchToFocus: Boolean

    @Modes.AutoWhiteBalance
    var awb: Int

    @Modes.Flash
    var flash: Int

    var opticalStabilization: Boolean

    var videoStabilization: Boolean

    @Modes.NoiseReduction
    var noiseReduction: Int

    var zsl: Boolean

    var displayOrientation: Int

    @Modes.JpegQuality
    var jpegQuality: Int

    /**
     * @return `true` if the implementation was able to start the camera session.
     */
    fun start(): Boolean

    fun stop()

    /**
     * @return `true` if the aspect ratio was changed.
     */
    fun setAspectRatio(ratio: AspectRatio): Boolean

    fun takePicture()

    fun startVideoRecording(outputFile: File)

    @TargetApi(Build.VERSION_CODES.N)
    fun pauseVideoRecording(): Boolean

    @TargetApi(Build.VERSION_CODES.N)
    fun resumeVideoRecording(): Boolean

    fun stopVideoRecording(): Boolean

    fun calculateTouchArea(
            surfaceWidth: Int,
            surfaceHeight: Int,
            x: Float,
            y: Float,
            coefficient: Float = 1.5f
    ): Rect {

        val areaSize = 100 * coefficient

        val left = MathUtils.clamp(x - areaSize / 2, 0f, surfaceWidth - areaSize)
        val top = MathUtils.clamp(y - areaSize / 2, 0f, surfaceHeight - areaSize)

        val rectF = RectF(left, top, left + areaSize, top + areaSize)

        return Rect(
                rectF.left.roundToInt(),
                rectF.top.roundToInt(),
                rectF.right.roundToInt(),
                rectF.bottom.roundToInt()
        )
    }

    interface Listener {
        suspend fun onCameraOpened()
        suspend fun onCameraClosed()
        fun onPictureTaken(imageData: ByteArray)
        fun onCameraError(e: Exception, isCritical: Boolean = false)

        @TargetApi(Build.VERSION_CODES.KITKAT)
        fun onPreviewFrame(reader: ImageReader)
    }
}