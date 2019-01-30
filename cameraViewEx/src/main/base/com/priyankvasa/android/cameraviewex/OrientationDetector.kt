/*
 * Copyright 2019 Priyank Vasa
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

import android.content.Context
import android.util.SparseIntArray
import android.view.Display
import android.view.OrientationEventListener
import android.view.Surface
import java.util.concurrent.atomic.AtomicInteger

/** Monitors the value returned from [Display.getRotation] and device's sensor orientation. */
internal abstract class OrientationDetector(context: Context) {

    var display: Display? = null

    var lastKnownDisplayOrientation = 0
        private set

    private val orientationEventListener: OrientationEventListener = object : OrientationEventListener(context) {

        /** This is either Surface.Rotation_0, _90, _180, _270, or -1 (invalid). */
        private val lastKnownRotation = AtomicInteger(-1)

        override fun onOrientationChanged(orientation: Int) {

            if (orientation == OrientationEventListener.ORIENTATION_UNKNOWN) return

            onSensorOrientationChanged(orientation)

            val rotation = display?.rotation ?: return

            if (lastKnownRotation.getAndSet(rotation) != rotation) {
                dispatchOnDisplayOrientationChanged(rotation)
            }
        }
    }

    fun enable(display: Display) {
        this.display = display
        orientationEventListener.enable()
        // Immediately dispatch the first callback
        dispatchOnDisplayOrientationChanged(display.rotation)
    }

    fun disable() {
        orientationEventListener.disable()
        display = null
    }

    private fun dispatchOnDisplayOrientationChanged(rotation: Int) {
        val displayOrientation =
            DISPLAY_ORIENTATIONS.get(rotation, -1)
                .takeIf { it != -1 }
                ?: return
        lastKnownDisplayOrientation = displayOrientation
        onDisplayOrientationChanged(displayOrientation)
    }

    abstract fun onDisplayOrientationChanged(displayOrientation: Int)

    abstract fun onSensorOrientationChanged(sensorOrientation: Int)

    companion object {

        /** Mapping from Surface.Rotation_n to degrees. */
        val DISPLAY_ORIENTATIONS = SparseIntArray()

        init {
            DISPLAY_ORIENTATIONS.put(Surface.ROTATION_0, 0)
            DISPLAY_ORIENTATIONS.put(Surface.ROTATION_90, 90)
            DISPLAY_ORIENTATIONS.put(Surface.ROTATION_180, 180)
            DISPLAY_ORIENTATIONS.put(Surface.ROTATION_270, 270)
        }
    }
}