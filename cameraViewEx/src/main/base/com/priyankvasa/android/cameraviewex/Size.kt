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

import java.lang.Long.signum

/**
 * Immutable class for describing width and height dimensions in pixels.
 */
data class Size
/**
 * Create a new immutable Size instance.
 *
 * @param width  The width of the size, in pixels
 * @param height The height of the size, in pixels
 */
(val width: Int, val height: Int) : Comparable<Size> {

    val aspectRatio: AspectRatio = AspectRatio.of(this)

    override fun equals(other: Any?): Boolean = when {
        other == null -> false
        this === other -> true
        other is Size -> width == other.width && height == other.height
        else -> false
    }

    override fun toString(): String = width.toString() + "x" + height

    override fun hashCode(): Int =
    // assuming most sizes are <2^16, doing a rotate will give us perfect hashing
        height xor (width shl Integer.SIZE / 2 or width.ushr(Integer.SIZE / 2))

    override fun compareTo(other: Size): Int =
        signum((width.toLong() * height) - (other.width.toLong() * other.height))

    companion object {
        val P2160: Size = Size(3840, 2160)
        val P1440: Size = Size(2560, 1440)
        val P1080: Size = Size(1920, 1080)
        val P720: Size = Size(1280, 720)
        val P480: Size = Size(720, 480)
        val CIF: Size = Size(352, 288)
        val QVGA: Size = Size(320, 240)
        val QCIF: Size = Size(176, 144)
        val Invalid: Size = Size(-1, -1)
    }
}