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
import android.view.View

internal interface CameraInterface {

    val preview: PreviewImpl

    val view: View get() = preview.view

    val listener: Listener

    val isCameraOpened: Boolean

    val supportedAspectRatios: Set<AspectRatio>

    val aspectRatio: AspectRatio

    var cameraMode: Int

    var outputFormat: Int

    var facing: Int

    var autoFocus: Boolean

    var touchToFocus: Boolean

    var awb: Int

    var flash: Int

    var ae: Boolean

    var opticalStabilization: Boolean

    var noiseReduction: Int

    var zsl: Boolean

    var displayOrientation: Int

    /**
     * @return `true` if the implementation was able to start the camera session.
     */
    fun start(): Boolean

    fun stop()

    /**
     * @return `true` if the aspect ratio was changed.
     */
    fun setAspectRatio(ratio: AspectRatio): Boolean

    fun capturePreviewFrame()

    fun takePicture()

    interface Listener {
        fun onCameraOpened()
        fun onCameraClosed()
        fun onPictureTaken(imageData: ByteArray)

        @TargetApi(Build.VERSION_CODES.KITKAT)
        fun onPreviewFrame(reader: ImageReader)
    }
}