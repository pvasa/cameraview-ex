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

object Modes {

    const val LANDSCAPE_90 = 90
    const val LANDSCAPE_270 = 270

    const val FACING_BACK = 0
    const val FACING_FRONT = 1

    internal object OutputFormat {
        const val JPEG = 0
        const val YUV_420_888 = 1
        const val RGBA_8888 = 2
    }

    object Flash {
        const val FLASH_OFF = 0
        const val FLASH_ON = 1
        const val FLASH_TORCH = 2
        const val FLASH_AUTO = 3
        const val FLASH_RED_EYE = 4
    }

    object NoiseReduction {
        const val NOISE_REDUCTION_OFF = 0
        const val NOISE_REDUCTION_FAST = 1
        const val NOISE_REDUCTION_HIGH_QUALITY = 2
        @TargetApi(23)
        const val NOISE_REDUCTION_MINIMAL = 3
        @TargetApi(23)
        const val NOISE_REDUCTION_ZERO_SHUTTER_LAG = 4
    }

    object Shutter {
        const val SHUTTER_OFF = 0 // ms
        const val SHUTTER_SHORT = 200 // ms
        const val SHUTTER_LONG = 400 // ms
    }

    object AutoWhiteBalance {
        const val AWB_OFF = 0
        const val AWB_AUTO = 1
        const val AWB_INCANDESCENT = 2
        const val AWB_FLUORESCENT = 3
        const val AWB_WARM_FLUORESCENT = 4
        const val AWB_DAYLIGHT = 5
        const val AWB_CLOUDY_DAYLIGHT = 6
        const val AWB_TWILIGHT = 7
        const val AWB_SHADE = 8
    }

    const val DEFAULT_ADJUST_VIEW_BOUNDS = false
    val DEFAULT_ASPECT_RATIO = AspectRatio.of(4, 3)
    const val DEFAULT_OUTPUT_FORMAT = OutputFormat.JPEG
    const val DEFAULT_FACING = FACING_BACK
    const val DEFAULT_AUTO_FOCUS = false
    const val DEFAULT_AUTO_EXPOSURE = false
    const val DEFAULT_TOUCH_TO_FOCUS = false
    const val DEFAULT_OPTICAL_STABILIZATION = false
    const val DEFAULT_FLASH = Flash.FLASH_OFF
    const val DEFAULT_NOISE_REDUCTION = NoiseReduction.NOISE_REDUCTION_OFF
    const val DEFAULT_SHUTTER = Shutter.SHUTTER_OFF
    const val DEFAULT_AWB = AutoWhiteBalance.AWB_OFF
}