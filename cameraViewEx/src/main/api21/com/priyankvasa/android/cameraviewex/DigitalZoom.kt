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

import android.graphics.Rect
import android.hardware.camera2.CameraCharacteristics
import android.os.Build
import android.support.annotation.RequiresApi
import kotlin.math.roundToInt

/** This is a helper class for digital zooming. */
@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
internal class DigitalZoom(private val getCameraCharacteristics: () -> CameraCharacteristics?) {

    /** Current zoom level. */
    private var currentZoom: Float = 1f

    /** Maximum possible digital zoom based on [CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM]. */
    val maxZoom: Float
        get() = getCameraCharacteristics()
            ?.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM)
            ?: 1f

    private val sensorArraySize: Rect?
        get() = getCameraCharacteristics()
            ?.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)

    /** Scale factor tolerance until which zooming is ignored */
    private val tolerance = 0.004f

    /** Calculate crop region rect for [scaleFactor]. */
    fun getZoomForScaleFactor(scaleFactor: Float): Float {

        val maxZoom = maxZoom

        var delta = 0.06f // Control this value to control the zooming sensitivity

        when {
            scaleFactor > 1f + tolerance -> {
                // Don't over zoom-in
                if (maxZoom - currentZoom <= delta) delta = maxZoom - currentZoom
                currentZoom += delta
            }
            scaleFactor < 1f - tolerance -> {
                // Don't over zoom-out
                if (currentZoom - delta < 1f) delta = currentZoom - 1f
                currentZoom -= delta
            }
        }

        return currentZoom
    }

    /** Calculate crop region rect for [zoom] value. */
    fun getCropRegionForZoom(zoom: Float): Rect? {

        val sensorArraySize = sensorArraySize ?: return null

        val xCenter = sensorArraySize.width() / 2f
        val yCenter = sensorArraySize.height() / 2f
        val xDelta = 0.5f * sensorArraySize.width() / zoom
        val yDelta = 0.5f * sensorArraySize.height() / zoom

        return Rect(
            (xCenter - xDelta).roundToInt(),
            (yCenter - yDelta).roundToInt(),
            (xCenter + xDelta).roundToInt(),
            (yCenter + yDelta).roundToInt()
        ).also { currentZoom = zoom }
    }
}