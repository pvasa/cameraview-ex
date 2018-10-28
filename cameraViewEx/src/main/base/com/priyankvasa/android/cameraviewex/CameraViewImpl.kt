/*
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

import android.view.View

internal abstract class CameraViewImpl(
        protected val callback: Callback?,
        protected val preview: PreviewImpl
) {
    val view: View
        get() = preview.view

    internal abstract val isCameraOpened: Boolean

    internal abstract var facing: Int

    internal abstract val supportedAspectRatios: Set<AspectRatio>

    internal abstract val aspectRatio: AspectRatio

    internal abstract var autoFocus: Boolean

    internal abstract var touchToFocus: Boolean

    internal abstract var awb: Int

    internal abstract var flash: Int

    internal abstract var ae: Boolean

    internal abstract var opticalStabilization: Boolean

    internal abstract var noiseReduction: Int

    internal abstract var displayOrientation: Int

    /**
     * @return `true` if the implementation was able to start the camera session.
     */
    internal abstract fun start(): Boolean

    internal abstract fun stop()

    /**
     * @return `true` if the aspect ratio was changed.
     */
    internal abstract fun setAspectRatio(ratio: AspectRatio): Boolean

    internal abstract fun takePicture()

    internal interface Callback {
        fun onCameraOpened()
        fun onCameraClosed()
        fun onPictureTaken(data: ByteArray)
    }
}
