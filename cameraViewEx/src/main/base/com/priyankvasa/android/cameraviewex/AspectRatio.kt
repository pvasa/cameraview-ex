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

import android.os.Parcel
import android.os.Parcelable
import android.support.v4.util.SparseArrayCompat

/** Immutable class for describing proportional relationship between width and height. */
class AspectRatio private constructor(val x: Int, val y: Int) : Comparable<AspectRatio>, Parcelable {

    fun matches(size: Size): Boolean {
        val gcd = gcd(size.width, size.height)
        val x = size.width / gcd
        val y = size.height / gcd
        return this.x == x && this.y == y
    }

    override fun equals(other: Any?): Boolean = when {
        other == null -> false
        this === other -> true
        other is AspectRatio -> x == other.x && y == other.y
        else -> false
    }

    override fun toString(): String = "$x:$y"

    fun toFloat(): Float = x.toFloat() / y

    override fun hashCode(): Int =
        // assuming most sizes are <2^16, doing a rotate will give us perfect hashing
        y xor (x shl Integer.SIZE / 2 or x.ushr(Integer.SIZE / 2))

    override fun compareTo(other: AspectRatio): Int = when {
        equals(other) -> 0
        toFloat() - other.toFloat() > 0 -> 1
        else -> -1
    }

    /**
     * @return The inverse of this [AspectRatio].
     */
    fun inverse(): AspectRatio = of(y, x)

    companion object {

        val Invalid: AspectRatio = AspectRatio(0, 0)

        private val cache = SparseArrayCompat<SparseArrayCompat<AspectRatio>>(16)

        /**
         * Returns an instance of [AspectRatio] specified by [x] and [y] values.
         * The values `x` and `y` will be reduced by their greatest common divider.
         *
         * @param x The width
         * @param y The height
         * @return An instance of [AspectRatio]
         */
        @JvmStatic
        fun of(x: Int, y: Int): AspectRatio {
            var a: Int = x
            var b: Int = y
            val gcd: Int = gcd(a, b)
            a /= gcd
            b /= gcd

            return cache.get(a)
                ?.run { get(b) ?: AspectRatio(a, b).also { put(b, it) } }
                ?: AspectRatio(a, b)
                    .also { cache.put(a, SparseArrayCompat<AspectRatio>().apply { put(b, it) }) }
        }

        /**
         * Returns an instance of [AspectRatio] specified by [Size.width] and [Size.height] of [size].
         * The values `width` and `height` will be reduced by their greatest common divider.
         *
         * @param size
         * @return An instance of [AspectRatio]
         */
        @JvmStatic
        fun of(size: Size): AspectRatio = of(size.width, size.height)

        /**
         * Parse an [AspectRatio] from a [String] formatted like "4:3".
         *
         * @param s The string representation of the aspect ratio
         * @return The aspect ratio
         * @throws IllegalArgumentException when the format is incorrect.
         */
        @JvmSynthetic
        @Throws(IllegalArgumentException::class)
        internal fun parse(s: String): AspectRatio = try {
            s.split(':').let { of(it[0].trim().toInt(), it[1].trim().toInt()) }
        } catch (e: NumberFormatException) {
            throw IllegalArgumentException("Malformed aspect ratio: $s", e)
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

        @JvmField
        val CREATOR: Parcelable.Creator<AspectRatio> = object : Parcelable.Creator<AspectRatio> {
            override fun createFromParcel(parcel: Parcel): AspectRatio =
                of(parcel.readInt(), parcel.readInt())

            override fun newArray(size: Int): Array<AspectRatio?> = arrayOfNulls(size)
        }
    }

    constructor(parcel: Parcel) : this(parcel.readInt(), parcel.readInt())

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeInt(x)
        parcel.writeInt(y)
    }

    override fun describeContents(): Int = 0
}