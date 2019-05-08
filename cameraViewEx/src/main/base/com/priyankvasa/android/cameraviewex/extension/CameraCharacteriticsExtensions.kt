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

package com.priyankvasa.android.cameraviewex.extension

import android.hardware.camera2.CameraCharacteristics
import android.os.Build
import android.support.annotation.RequiresApi
import com.priyankvasa.android.cameraviewex.Modes

@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
internal fun CameraCharacteristics.isHardwareLevelSupported(): Boolean =
    get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)
        .let { it != null && it != CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY }

@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
internal fun CameraCharacteristics.isAfSupported(mode: Int): Boolean =
    get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES)?.contains(mode) == true

@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
internal fun CameraCharacteristics.isAwbSupported(@Modes.AutoWhiteBalance mode: Int): Boolean =
    get(CameraCharacteristics.CONTROL_AWB_AVAILABLE_MODES)?.contains(mode) == true

@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
internal fun CameraCharacteristics.isOisSupported(): Boolean =
    get(CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION)
        ?.contains(CameraCharacteristics.LENS_OPTICAL_STABILIZATION_MODE_ON) == true

@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
internal fun CameraCharacteristics.isVideoStabilizationSupported(): Boolean =
    get(CameraCharacteristics.CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES)
        ?.contains(CameraCharacteristics.CONTROL_VIDEO_STABILIZATION_MODE_ON) == true

@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
internal fun CameraCharacteristics.isNoiseReductionSupported(@Modes.NoiseReduction mode: Int): Boolean =
    get(CameraCharacteristics.NOISE_REDUCTION_AVAILABLE_NOISE_REDUCTION_MODES)?.contains(mode) == true
