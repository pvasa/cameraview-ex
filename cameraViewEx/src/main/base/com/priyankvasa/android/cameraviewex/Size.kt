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

/**
 * Immutable class for describing width and height dimensions in pixels.
 */
open class Size
/**
 * Create a new immutable Size instance.
 *
 * @param width  The width of the size, in pixels
 * @param height The height of the size, in pixels
 */
(val width: Int, val height: Int) : Comparable<Size> {

    override fun equals(other: Any?): Boolean = when {
        other == null -> false
        this === other -> true
        other is Size -> {
            val size = other as Size?
            width == size?.width && height == size.height
        }
        else -> false
    }

    override fun toString(): String = width.toString() + "x" + height

    override fun hashCode(): Int =
    // assuming most sizes are <2^16, doing a rotate will give us perfect hashing
            height xor (width shl Integer.SIZE / 2 or width.ushr(Integer.SIZE / 2))

    override fun compareTo(other: Size): Int = width * height - other.width * other.height
}