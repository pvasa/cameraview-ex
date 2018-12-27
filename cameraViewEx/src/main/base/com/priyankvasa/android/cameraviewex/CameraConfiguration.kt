/*
 * Copyright 2018 Priyank Vasa
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

internal class CameraConfiguration {

    val cameraMode: MutableLiveData<Int> = MutableLiveData(Modes.DEFAULT_CAMERA_MODE)
    val outputFormat: MutableLiveData<Int> = MutableLiveData(Modes.DEFAULT_OUTPUT_FORMAT)
    val jpegQuality: MutableLiveData<Int> = MutableLiveData(Modes.DEFAULT_JPEG_QUALITY)
    val facing: MutableLiveData<Int> = MutableLiveData(Modes.DEFAULT_FACING)
    val aspectRatio: MutableLiveData<AspectRatio> = MutableLiveData(Modes.DEFAULT_ASPECT_RATIO)
    val autoFocus: MutableLiveData<Int> = MutableLiveData(Modes.DEFAULT_AUTO_FOCUS)
    val touchToFocus: MutableLiveData<Boolean> = MutableLiveData(Modes.DEFAULT_TOUCH_TO_FOCUS)
    val pinchToZoom: MutableLiveData<Boolean> = MutableLiveData(Modes.DEFAULT_PINCH_TO_ZOOM)
    val currentDigitalZoom: MutableLiveData<Float> = MutableLiveData(1f)
    val awb: MutableLiveData<Int> = MutableLiveData(Modes.DEFAULT_AWB)
    val flash: MutableLiveData<Int> = MutableLiveData(Modes.DEFAULT_FLASH)
    val opticalStabilization: MutableLiveData<Boolean> = MutableLiveData(Modes.DEFAULT_OPTICAL_STABILIZATION)
    val noiseReduction: MutableLiveData<Int> = MutableLiveData(Modes.DEFAULT_NOISE_REDUCTION)
    val shutter: MutableLiveData<Int> = MutableLiveData(Modes.DEFAULT_SHUTTER)
    val zsl: MutableLiveData<Boolean> = MutableLiveData(Modes.DEFAULT_ZSL)
}