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

import android.support.v4.util.ArrayMap
import java.util.SortedSet
import java.util.TreeSet

/**
 * A collection class that automatically groups [Size]s by their [AspectRatio]s.
 */
internal class SizeMap {

    private val ratios = ArrayMap<AspectRatio, SortedSet<Size>>()

    val isEmpty: Boolean get() = ratios.isEmpty

    /**
     * Add a new [Size] to this collection.
     *
     * @param size The size to add.
     * @return `true` if it is added, `false` if it already exists and is not added.
     */
    fun add(size: Size): Boolean {

        ratios.keys.forEach { ratio ->
            if (ratio.matches(size)) {
                return if (ratios[ratio]?.contains(size) == true) false
                else {
                    ratios[ratio]?.add(size)
                    true
                }
            }
        }
        // None of the existing ratio matches the provided size; add a new key
        ratios[AspectRatio.of(size.width, size.height)] = TreeSet<Size>().apply { add(size) }
        return true
    }

    /**
     * Removes the specified aspect ratio and all sizes associated with it.
     *
     * @param ratio The aspect ratio to be removed.
     * Note: [ratio] is nullable because for some reason, on older devices,
     * looping through previewSizes in [Camera2.collectCameraInfo] has null elements. Seems like a ghost bug.
     */
    fun remove(ratio: AspectRatio?) {
        ratios.remove(ratio)
    }

    fun ratios(): Set<AspectRatio> = ratios.keys

    /**
     * Note: [ratio] is nullable because for some reason, on older devices,
     * looping through previewSizes in [Camera1.supportedAspectRatios] has null elements. Seems like a ghost bug.
     */
    fun sizes(ratio: AspectRatio?): SortedSet<Size> = ratios[ratio] ?: sortedSetOf()

    fun clear() = ratios.clear()
}