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

package com.google.android.cameraview

import android.os.Parcelable
import android.support.v4.util.SparseArrayCompat
import kotlinx.android.parcel.Parcelize

/**
 * Immutable class for describing proportional relationship between width and height.
 */
@Parcelize
class AspectRatio private constructor(val x: Int, val y: Int) : Comparable<AspectRatio>, Parcelable {

    fun matches(size: Size): Boolean {
        val gcd = gcd(size.width, size.height)
        val x = size.width / gcd
        val y = size.height / gcd
        return this.x == x && this.y == y
    }

    override fun equals(other: Any?): Boolean {
        if (other == null) {
            return false
        }
        if (this === other) {
            return true
        }
        if (other is AspectRatio) {
            val ratio = other as AspectRatio?
            return x == ratio!!.x && y == ratio.y
        }
        return false
    }

    override fun toString(): String {
        return x.toString() + ":" + y
    }

    fun toFloat(): Float {
        return x.toFloat() / y
    }

    override fun hashCode(): Int {
        // assuming most sizes are <2^16, doing a rotate will give us perfect hashing
        return y xor (x shl Integer.SIZE / 2 or x.ushr(Integer.SIZE / 2))
    }

    override fun compareTo(other: AspectRatio): Int = when {
        equals(other) -> 0
        toFloat() - other.toFloat() > 0 -> 1
        else -> -1
    }

    /**
     * @return The inverse of this [AspectRatio].
     */
    fun inverse(): AspectRatio {

        return AspectRatio.of(y, x)
    }

    companion object {

        private val sCache = SparseArrayCompat<SparseArrayCompat<AspectRatio>>(16)

        /**
         * Returns an instance of [AspectRatio] specified by `x` and `y` values.
         * The values `x` and `` will be reduced by their greatest common divider.
         *
         * @param x The width
         * @param y The height
         * @return An instance of [AspectRatio]
         */
        fun of(x: Int, y: Int): AspectRatio {
            var a = x
            var b = y
            val gcd = gcd(a, b)
            a /= gcd
            b /= gcd
            var arrayX: SparseArrayCompat<AspectRatio>? = sCache.get(a)
            return if (arrayX == null) {
                val ratio = AspectRatio(a, b)
                arrayX = SparseArrayCompat()
                arrayX.put(b, ratio)
                sCache.put(a, arrayX)
                ratio
            } else {
                var ratio: AspectRatio? = arrayX.get(b)
                if (ratio == null) {
                    ratio = AspectRatio(a, b)
                    arrayX.put(b, ratio)
                }
                ratio
            }
        }

        /**
         * Parse an [AspectRatio] from a [String] formatted like "4:3".
         *
         * @param s The string representation of the aspect ratio
         * @return The aspect ratio
         * @throws IllegalArgumentException when the format is incorrect.
         */
        fun parse(s: String): AspectRatio {
            val position = s.indexOf(':')
            if (position == -1) {
                throw IllegalArgumentException("Malformed aspect ratio: $s")
            }
            try {
                val x = Integer.parseInt(s.substring(0, position))
                val y = Integer.parseInt(s.substring(position + 1))
                return AspectRatio.of(x, y)
            } catch (e: NumberFormatException) {
                throw IllegalArgumentException("Malformed aspect ratio: $s", e)
            }

        }

        private fun gcd(x: Int, y: Int): Int {
            var a = x
            var b = y
            while (b != 0) {
                val c = b
                b = a % b
                a = c
            }
            return a
        }
    }
}
