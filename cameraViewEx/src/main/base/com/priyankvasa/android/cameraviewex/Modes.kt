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

import android.annotation.TargetApi
import android.os.Build
import android.support.annotation.IntDef

object Modes {

    @IntDef(CameraMode.SINGLE_CAPTURE,
        CameraMode.BURST_CAPTURE,
        CameraMode.CONTINUOUS_FRAME,
        CameraMode.VIDEO_CAPTURE)
    @Retention(AnnotationRetention.SOURCE)
    @Target(AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.PROPERTY, AnnotationTarget.PROPERTY_GETTER)
    annotation class CameraMode {
        companion object {
            /** Output format is according to [CameraView.outputFormat] */
            const val SINGLE_CAPTURE = 0x01
            internal const val BURST_CAPTURE = 0x02
            /** Output format is always [android.graphics.ImageFormat.YUV_420_888] */
            const val CONTINUOUS_FRAME = 0x04
            const val VIDEO_CAPTURE = 0x08
        }
    }

    /** Direction the camera faces relative to device screen. */
    @IntDef(OutputFormat.JPEG, OutputFormat.YUV_420_888, OutputFormat.RGBA_8888)
    @Retention(AnnotationRetention.SOURCE)
    @Target(AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.PROPERTY, AnnotationTarget.PROPERTY_GETTER)
    annotation class OutputFormat {
        companion object {
            const val JPEG = 0
            const val YUV_420_888 = 1
            const val RGBA_8888 = 2
        }
    }

    @IntDef(JpegQuality.LOW, JpegQuality.MEDIUM, JpegQuality.DEFAULT, JpegQuality.HIGH)
    @Retention(AnnotationRetention.SOURCE)
    @Target(AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.PROPERTY, AnnotationTarget.PROPERTY_GETTER)
    annotation class JpegQuality {
        companion object {
            const val LOW = 60
            const val MEDIUM = 80
            const val DEFAULT = 90
            const val HIGH = 100
        }
    }

    /** Direction the camera faces relative to device screen. */
    @IntDef(Facing.FACING_BACK, Facing.FACING_FRONT, Facing.FACING_EXTERNAL)
    @Retention(AnnotationRetention.SOURCE)
    @Target(AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.PROPERTY, AnnotationTarget.PROPERTY_GETTER)
    annotation class Facing {
        companion object {
            const val FACING_BACK = 0
            const val FACING_FRONT = 1
            const val FACING_EXTERNAL = 2
        }
    }

    /** The mode for the camera device's auto focus control */
    @IntDef(AutoFocus.AF_OFF,
        AutoFocus.AF_AUTO,
        AutoFocus.AF_MACRO,
        AutoFocus.AF_CONTINUOUS_VIDEO,
        AutoFocus.AF_CONTINUOUS_PICTURE,
        AutoFocus.AF_EDOF)
    @Retention(AnnotationRetention.SOURCE)
    @Target(AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.PROPERTY, AnnotationTarget.PROPERTY_GETTER)
    annotation class AutoFocus {
        companion object {
            const val AF_OFF = 0
            const val AF_AUTO = 1
            const val AF_MACRO = 2
            const val AF_CONTINUOUS_VIDEO = 3
            const val AF_CONTINUOUS_PICTURE = 4
            const val AF_EDOF = 5
        }
    }

    /** The mode for the camera device's flash control */
    @IntDef(Flash.FLASH_OFF,
        Flash.FLASH_ON,
        Flash.FLASH_TORCH,
        Flash.FLASH_AUTO,
        Flash.FLASH_RED_EYE)
    @Retention(AnnotationRetention.SOURCE)
    @Target(AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.PROPERTY, AnnotationTarget.PROPERTY_GETTER)
    annotation class Flash {
        companion object {
            const val FLASH_OFF = 0
            const val FLASH_ON = 1
            const val FLASH_TORCH = 2
            const val FLASH_AUTO = 3
            const val FLASH_RED_EYE = 4
        }
    }

    /** The mode for the camera device's noise reduction control */
    @IntDef(NoiseReduction.NOISE_REDUCTION_OFF,
        NoiseReduction.NOISE_REDUCTION_FAST,
        NoiseReduction.NOISE_REDUCTION_HIGH_QUALITY,
        NoiseReduction.NOISE_REDUCTION_MINIMAL,
        NoiseReduction.NOISE_REDUCTION_ZERO_SHUTTER_LAG)
    @Retention(AnnotationRetention.SOURCE)
    @Target(AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.PROPERTY, AnnotationTarget.PROPERTY_GETTER)
    annotation class NoiseReduction {
        companion object {
            const val NOISE_REDUCTION_OFF = 0
            const val NOISE_REDUCTION_FAST = 1
            const val NOISE_REDUCTION_HIGH_QUALITY = 2
            @TargetApi(Build.VERSION_CODES.M)
            const val NOISE_REDUCTION_MINIMAL = 3
            @TargetApi(Build.VERSION_CODES.M)
            const val NOISE_REDUCTION_ZERO_SHUTTER_LAG = 4
        }
    }

    /** The mode for the camera device's auto white balance control */
    @IntDef(AutoWhiteBalance.AWB_OFF,
        AutoWhiteBalance.AWB_AUTO,
        AutoWhiteBalance.AWB_INCANDESCENT,
        AutoWhiteBalance.AWB_FLUORESCENT,
        AutoWhiteBalance.AWB_WARM_FLUORESCENT,
        AutoWhiteBalance.AWB_DAYLIGHT,
        AutoWhiteBalance.AWB_CLOUDY_DAYLIGHT,
        AutoWhiteBalance.AWB_TWILIGHT,
        AutoWhiteBalance.AWB_SHADE)
    @Retention(AnnotationRetention.SOURCE)
    @Target(AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.PROPERTY, AnnotationTarget.PROPERTY_GETTER)
    annotation class AutoWhiteBalance {
        companion object {
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
    }

    /** Shutter time in milliseconds */
    @IntDef(Shutter.SHUTTER_OFF,
        Shutter.SHUTTER_SHORT,
        Shutter.SHUTTER_LONG)
    @Retention(AnnotationRetention.SOURCE)
    @Target(AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.PROPERTY, AnnotationTarget.PROPERTY_GETTER)
    annotation class Shutter {
        companion object {
            const val SHUTTER_OFF = 0 // ms
            const val SHUTTER_SHORT = 200 // ms
            const val SHUTTER_LONG = 400 // ms
        }
    }

    const val DEFAULT_ADJUST_VIEW_BOUNDS = true
    val DEFAULT_ASPECT_RATIO: AspectRatio = AspectRatio.of(4, 3)
    val DEFAULT_CONTINUOUS_FRAME_SIZE: Size = Size.Invalid
    val DEFAULT_SINGLE_CAPTURE_SIZE: Size = Size.Invalid
    const val DEFAULT_CAMERA_MODE: Int = CameraMode.SINGLE_CAPTURE
    const val DEFAULT_OUTPUT_FORMAT: Int = OutputFormat.JPEG
    const val DEFAULT_JPEG_QUALITY: Int = JpegQuality.DEFAULT
    const val DEFAULT_FACING: Int = Facing.FACING_BACK
    const val DEFAULT_AUTO_FOCUS: Int = AutoFocus.AF_OFF
    const val DEFAULT_TOUCH_TO_FOCUS = false
    const val DEFAULT_PINCH_TO_ZOOM = false
    const val DEFAULT_OPTICAL_STABILIZATION = false
    const val DEFAULT_FLASH: Int = Flash.FLASH_OFF
    const val DEFAULT_NOISE_REDUCTION: Int = NoiseReduction.NOISE_REDUCTION_OFF
    const val DEFAULT_SHUTTER: Int = Shutter.SHUTTER_OFF
    const val DEFAULT_AWB: Int = AutoWhiteBalance.AWB_OFF
    const val DEFAULT_ZSL = false

    const val DEFAULT_CAMERA_ID = "default"
}