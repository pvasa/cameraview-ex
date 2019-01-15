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

import android.hardware.camera2.CameraCharacteristics
import androidx.collection.ArrayMap

/**
 * A collection class that groups cameraIds by their facing [Modes.Facing] direction and also
 * the cameraId's [CameraCharacteristics]
 */
class CameraMap {

    private val cameras = ArrayMap<Int, ArrayList<Int>>().apply {
        this[Modes.Facing.FACING_BACK] = ArrayList()
        this[Modes.Facing.FACING_FRONT] = ArrayList()
    }
    private val characteristics = ArrayMap<Int, CameraCharacteristics>()

    /**
     * Convenience method for Camera2 camera ids that are strings
     */
    fun add(facing: Int, cameraId: String, cameraCharacteristics: CameraCharacteristics?) {
        add(facing, Integer.parseInt(cameraId), cameraCharacteristics)
    }

    /**
     * Adds a camera to the camera map given a facing direction of [Modes.Facing.FACING_BACK]
     * or [Modes.Facing.FACING_FRONT], a camera id and optional [CameraCharacteristics]
     */
    fun add(facing: Int, cameraId: Int, cameraCharacteristics: CameraCharacteristics?) {
        cameras[facing]?.add(cameraId)
        characteristics[cameraId] = cameraCharacteristics
    }

    /**
     * Returns a list of cameras facing in the passed in direction
     * either [Modes.Facing.FACING_BACK] or [Modes.Facing.FACING_FRONT]
     *
     * @return list of cameras or an empty list
     */
    fun camerasByFacing(facing: Int): ArrayList<Int> = cameras[facing] ?: ArrayList()

    /**
     * This will be null for pre lollipop Camera1 devices
     *
     * @returns [CameraCharacteristics] for the cameraId
     */
    fun characteristics(cameraId: Int): CameraCharacteristics? = characteristics[cameraId]

    /**
     * This will return the cameraId of the next camera, looping through all back and front
     * cameras. It will loop through cameras by the facing direction and not in the order of
     * the cameras. For example it will loop through each back camera and then each front camera.
     *
     * @return [Int] of the next cameraId
     */
    fun nextCamera(cameraId: Int): Int {
        val backCameras = camerasByFacing(Modes.Facing.FACING_BACK)
        val frontCameras = camerasByFacing(Modes.Facing.FACING_FRONT)
        when (facing(cameraId)) {
            Modes.Facing.FACING_BACK -> {
                val index = backCameras.indexOf(cameraId)
                return if (index + 1 == backCameras.size) {
                    when (frontCameras.size) {
                        0 -> backCameras.first() // Device only has back cameras
                        else -> frontCameras.first()
                    }
                } else {
                    backCameras[index + 1]
                }
            }
            Modes.Facing.FACING_FRONT -> {
                val index = frontCameras.indexOf(cameraId)
                return if (index + 1 == frontCameras.size) {
                    when (backCameras.size) {
                        0 -> frontCameras.first() // Device only has front cameras
                        else -> backCameras.first()
                    }
                } else {
                    frontCameras[index + 1]
                }
            }
        }
        return Modes.DEFAULT_FACING
    }

    /**
     * This will return which direction a camera is facing
     *
     * @return [Modes.Facing.FACING_BACK] or [Modes.Facing.FACING_FRONT]
     */
    fun facing(cameraId: Int): Int {
        if (camerasByFacing(Modes.Facing.FACING_FRONT).contains(cameraId)) {
            return Modes.Facing.FACING_FRONT
        }
        return Modes.Facing.FACING_BACK
    }
}