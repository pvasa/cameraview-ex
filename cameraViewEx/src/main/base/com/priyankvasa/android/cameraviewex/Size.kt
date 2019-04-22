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

import android.os.Parcelable
import kotlinx.android.parcel.IgnoredOnParcel
import kotlinx.android.parcel.Parcelize
import java.lang.Long.signum
import kotlin.math.max
import kotlin.math.min

/**
 * Immutable class for describing width and height dimensions in pixels.
 */
@Parcelize
data class Size
/**
 * Create a new immutable Size instance.
 *
 * @param width  The width of the size, in pixels
 * @param height The height of the size, in pixels
 */
(val width: Int, val height: Int) : Comparable<Size>, Parcelable {

    val aspectRatio: AspectRatio get() = AspectRatio.of(this)

    @IgnoredOnParcel
    val longerEdge: Int = max(width, height)

    @IgnoredOnParcel
    val shorterEdge: Int = min(width, height)

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

        /**
         * Parse a string to [Size].
         * Valid formats are [W1440,H1080], [H1440,W1080], [W1440,1080], [1440,W1080], [H1440,1080], [1440,H1080]
         * @throws IllegalArgumentException if format is incorrect
         * @throws NumberFormatException if size is not parsable
         */
        @Throws(IllegalArgumentException::class, NumberFormatException::class)
        fun parse(size: CharSequence): Size {
            val (d1: String, d2: String) = size.split(',')
            return when {
                d1[0].equals('W', true) || d2[0].equals('H', true) ->
                    Size(d1.substringAfter('W').toInt(), d2.substringAfter('H').toInt())
                d2[0].equals('W', true) || d1[0].equals('H', true) ->
                    Size(d2.substringAfter('W').toInt(), d1.substringAfter('H').toInt())
                else -> throw IllegalArgumentException("String format is not a correct size format")
            }
        }
    }
}