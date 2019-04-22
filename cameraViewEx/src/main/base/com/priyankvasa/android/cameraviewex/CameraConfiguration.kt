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

import android.content.Context
import android.content.res.TypedArray
import android.util.AttributeSet

class CameraConfiguration private constructor() {

    internal val aspectRatio: NonNullableLiveData<AspectRatio> = NonNullableLiveData(Modes.DEFAULT_ASPECT_RATIO)
    /** Same dimensions as [aspectRatio] but x >= y is always `true` */
    internal val sensorAspectRatio: AspectRatio
        get() = aspectRatio.value.run { if (x < y) inverse() else this }
    internal val cameraMode: NonNullableLiveData<Int> = NonNullableLiveData(Modes.DEFAULT_CAMERA_MODE)
    internal val continuousFrameSize: NonNullableLiveData<Size> = NonNullableLiveData(Modes.DEFAULT_CONTINUOUS_FRAME_SIZE)
    internal val singleCaptureSize: NonNullableLiveData<Size> = NonNullableLiveData(Modes.DEFAULT_SINGLE_CAPTURE_SIZE)
    internal val outputFormat: NonNullableLiveData<Int> = NonNullableLiveData(Modes.DEFAULT_OUTPUT_FORMAT)
    internal val jpegQuality: NonNullableLiveData<Int> = NonNullableLiveData(Modes.DEFAULT_JPEG_QUALITY)
    internal val facing: NonNullableLiveData<Int> = NonNullableLiveData(Modes.DEFAULT_FACING)
    internal val autoFocus: NonNullableLiveData<Int> = NonNullableLiveData(Modes.DEFAULT_AUTO_FOCUS)
    internal val touchToFocus: NonNullableLiveData<Boolean> = NonNullableLiveData(Modes.DEFAULT_TOUCH_TO_FOCUS)
    internal val pinchToZoom: NonNullableLiveData<Boolean> = NonNullableLiveData(Modes.DEFAULT_PINCH_TO_ZOOM)
    internal val currentDigitalZoom: NonNullableLiveData<Float> = NonNullableLiveData(1f)
    internal val awb: NonNullableLiveData<Int> = NonNullableLiveData(Modes.DEFAULT_AWB)
    internal val flash: NonNullableLiveData<Int> = NonNullableLiveData(Modes.DEFAULT_FLASH)
    internal val opticalStabilization: NonNullableLiveData<Boolean> = NonNullableLiveData(Modes.DEFAULT_OPTICAL_STABILIZATION)
    internal val noiseReduction: NonNullableLiveData<Int> = NonNullableLiveData(Modes.DEFAULT_NOISE_REDUCTION)
    internal val shutter: NonNullableLiveData<Int> = NonNullableLiveData(Modes.DEFAULT_SHUTTER)
    internal val zsl: NonNullableLiveData<Boolean> = NonNullableLiveData(Modes.DEFAULT_ZSL)

    internal val isSingleCaptureModeEnabled: Boolean get() = cameraMode.value and Modes.CameraMode.SINGLE_CAPTURE != 0
    internal val isContinuousFrameModeEnabled: Boolean get() = cameraMode.value and Modes.CameraMode.CONTINUOUS_FRAME != 0
    internal val isVideoCaptureModeEnabled: Boolean get() = cameraMode.value and Modes.CameraMode.VIDEO_CAPTURE != 0

    companion object {

        val defaultConfig: CameraConfiguration by lazy { CameraConfiguration() }

        fun newInstance(
            context: Context,
            attributeSet: AttributeSet?,
            defStyleAttr: Int,
            setAdjustViewBounds: (Boolean) -> Unit,
            warn: (message: String, cause: Throwable) -> Unit
        ): CameraConfiguration = CameraConfiguration().apply {

            // Attributes
            val attrs: TypedArray = context.obtainStyledAttributes(
                attributeSet,
                R.styleable.CameraView,
                defStyleAttr,
                R.style.Widget_CameraView
            )

            setAdjustViewBounds(attrs.getBoolean(R.styleable.CameraView_android_adjustViewBounds, Modes.DEFAULT_ADJUST_VIEW_BOUNDS))

            facing.value = attrs.getInt(R.styleable.CameraView_facing, Modes.DEFAULT_FACING)
            aspectRatio.value = attrs.getString(R.styleable.CameraView_aspectRatio)
                .runCatching ar@{
                    if (this@ar.isNullOrBlank()) Modes.DEFAULT_ASPECT_RATIO
                    else AspectRatio.parse(this@ar)
                }
                .getOrElse {
                    warn(
                        "Invalid aspect ratio." +
                            " Reverting to default ${Modes.DEFAULT_ASPECT_RATIO}",
                        it
                    )
                    Modes.DEFAULT_ASPECT_RATIO
                }
            autoFocus.value = attrs.getInt(R.styleable.CameraView_autoFocus, Modes.DEFAULT_AUTO_FOCUS)
            flash.value = attrs.getInt(R.styleable.CameraView_flash, Modes.DEFAULT_FLASH)
            cameraMode.value = attrs.getInt(R.styleable.CameraView_cameraMode, Modes.DEFAULT_CAMERA_MODE)
            outputFormat.value = attrs.getInt(R.styleable.CameraView_outputFormat, Modes.DEFAULT_OUTPUT_FORMAT)
            shutter.value = attrs.getInt(R.styleable.CameraView_shutter, Modes.DEFAULT_SHUTTER)
            jpegQuality.value = attrs.getInt(R.styleable.CameraView_jpegQuality, Modes.DEFAULT_JPEG_QUALITY)

            // API 21+
            continuousFrameSize.value = attrs.getString(R.styleable.CameraView_continuousFrameSize)
                .runCatching {
                    if (this.isNullOrBlank()) Modes.DEFAULT_CONTINUOUS_FRAME_SIZE
                    else Size.parse(this)
                }
                .getOrElse {
                    warn("Cannot parse size. Fallback to default sizes based on aspect ratio.", it)
                    Modes.DEFAULT_CONTINUOUS_FRAME_SIZE
                }
            singleCaptureSize.value = attrs.getString(R.styleable.CameraView_singleCaptureSize)
                .runCatching {
                    if (this.isNullOrBlank()) Modes.DEFAULT_SINGLE_CAPTURE_SIZE
                    else Size.parse(this)
                }
                .getOrElse {
                    warn("Cannot parse size. Fallback to default sizes based on aspect ratio.", it)
                    Modes.DEFAULT_SINGLE_CAPTURE_SIZE
                }
            touchToFocus.value = attrs.getBoolean(R.styleable.CameraView_touchToFocus, Modes.DEFAULT_TOUCH_TO_FOCUS)
            pinchToZoom.value = attrs.getBoolean(R.styleable.CameraView_pinchToZoom, Modes.DEFAULT_PINCH_TO_ZOOM)
            awb.value = attrs.getInt(R.styleable.CameraView_awb, Modes.DEFAULT_AWB)
            opticalStabilization.value = attrs.getBoolean(R.styleable.CameraView_opticalStabilization, Modes.DEFAULT_OPTICAL_STABILIZATION)
            noiseReduction.value = attrs.getInt(R.styleable.CameraView_noiseReduction, Modes.DEFAULT_NOISE_REDUCTION)
            zsl.value = attrs.getBoolean(R.styleable.CameraView_zsl, Modes.DEFAULT_ZSL)

            attrs.recycle()
        }
    }
}