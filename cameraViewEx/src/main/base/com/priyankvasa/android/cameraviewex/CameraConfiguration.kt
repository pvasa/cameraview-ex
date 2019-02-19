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

import android.arch.lifecycle.LifecycleOwner

class CameraConfiguration {

    private val aspectRatioI: CameraConfigLiveData<AspectRatio> = CameraConfigLiveData(Modes.DEFAULT_ASPECT_RATIO)

    /** Set aspect ratio of camera. Valid format is "height:width" eg. "4:3". */
    var aspectRatio: AspectRatio
        get() = aspectRatioI.value
        set(value) {
            TODO("Confirm if require ui thread")
//            if (!requireInUiThread()) return
            aspectRatioI.value = value
        }

    internal val cameraMode: CameraConfigLiveData<Int> = CameraConfigLiveData(Modes.DEFAULT_CAMERA_MODE)
    internal val outputFormat: CameraConfigLiveData<Int> = CameraConfigLiveData(Modes.DEFAULT_OUTPUT_FORMAT)
    internal val jpegQuality: CameraConfigLiveData<Int> = CameraConfigLiveData(Modes.DEFAULT_JPEG_QUALITY)
    internal val facing: CameraConfigLiveData<Int> = CameraConfigLiveData(Modes.DEFAULT_FACING)
    internal val autoFocus: CameraConfigLiveData<Int> = CameraConfigLiveData(Modes.DEFAULT_AUTO_FOCUS)
    internal val touchToFocus: CameraConfigLiveData<Boolean> = CameraConfigLiveData(Modes.DEFAULT_TOUCH_TO_FOCUS)
    internal val pinchToZoom: CameraConfigLiveData<Boolean> = CameraConfigLiveData(Modes.DEFAULT_PINCH_TO_ZOOM)
    internal val currentDigitalZoom: CameraConfigLiveData<Float> = CameraConfigLiveData(1f)
    internal val awb: CameraConfigLiveData<Int> = CameraConfigLiveData(Modes.DEFAULT_AWB)
    internal val flash: CameraConfigLiveData<Int> = CameraConfigLiveData(Modes.DEFAULT_FLASH)
    internal val opticalStabilization: CameraConfigLiveData<Boolean> = CameraConfigLiveData(Modes.DEFAULT_OPTICAL_STABILIZATION)
    internal val noiseReduction: CameraConfigLiveData<Int> = CameraConfigLiveData(Modes.DEFAULT_NOISE_REDUCTION)
    internal val shutter: CameraConfigLiveData<Int> = CameraConfigLiveData(Modes.DEFAULT_SHUTTER)
    internal val zsl: CameraConfigLiveData<Boolean> = CameraConfigLiveData(Modes.DEFAULT_ZSL)

    internal val isSingleCaptureModeEnabled: Boolean get() = cameraMode.value and Modes.CameraMode.SINGLE_CAPTURE != 0
    internal val isContinuousFrameModeEnabled: Boolean get() = cameraMode.value and Modes.CameraMode.CONTINUOUS_FRAME != 0
    internal val isVideoCaptureModeEnabled: Boolean get() = cameraMode.value and Modes.CameraMode.VIDEO_CAPTURE != 0

    @JvmSynthetic
    internal fun observeAspectRatio(
        owner: LifecycleOwner,
        observer: (AspectRatio) -> Unit
    ): Unit = aspectRatioI.observe(owner, observer)

    @JvmSynthetic
    internal fun revertAspectRatio(): Unit = aspectRatioI.revert()
}