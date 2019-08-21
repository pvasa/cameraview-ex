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
import com.priyankvasa.android.cameraviewex.extension.chooseOptimalSize
import java.util.SortedSet
import java.util.TreeSet

/**
 * A collection class that automatically groups [Size]s by their [AspectRatio]s.
 */
internal class SizeMap {

    private val map: ArrayMap<AspectRatio, SortedSet<Size>> = ArrayMap()

    val isEmpty: Boolean get() = map.isEmpty

    /**
     * Add a new [Size] of [width] and [height] to this collection.
     *
     * @param width of size to add.
     * @param height of size to add.
     * @return `true` if it is added, `false` if it already exists and is not added.
     */
    fun add(width: Int, height: Int): Boolean = add(Size(width, height))

    /**
     * Add a new [Size] to this collection.
     *
     * @param size The size to add.
     * @return `true` if it is added, `false` if it already exists and is not added.
     */
    fun add(size: Size): Boolean {

        map.keys.forEach { ratio ->
            if (ratio.matches(size)) {
                return if (map[ratio]?.contains(size) == true) false
                else {
                    map[ratio]?.add(size)
                    true
                }
            }
        }
        // None of the existing ratio matches the provided size; add a new key
        map[AspectRatio.of(size)] = TreeSet<Size>().apply { add(size) }
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
        map.remove(ratio)
    }

    fun ratios(): Set<AspectRatio> = map.keys + map.keys.map { it.inverse() }

    /** Returns `true` if this map contains [size] mapped to any of the [ratios] */
    fun containsSize(size: Size): Boolean = sizes(AspectRatio.of(size)).contains(size)

    /**
     * Returns all the sizes mapped to [ratio] or an empty set if none found
     *
     * Note: [ratio] is nullable because for some reason, on older devices,
     * looping through previewSizes in [Camera1.supportedAspectRatios] has null elements. Seems like a ghost bug.
     */
    fun sizes(ratio: AspectRatio?): SortedSet<Size> = map[ratio] ?: sortedSetOf()

    /**
     * If [requestedSize] is [Size.Invalid], choose highest size from
     * set of sizes with aspect ratio of [fallbackRatio].
     *
     * Else if [requestedSize] is valid, choose best size from set of sizes
     * which has same aspect ratio as [requestedSize].
     *  Best case would be [requestedSize] itself.
     *
     * If this set is empty, choose best size from set of sizes
     * with aspect ratio of [fallbackRatio]
     *
     * Even if this set is empty, return `null`.
     */
    fun chooseOptimalSize(requestedSize: Size, fallbackRatio: AspectRatio): Size? {

        val fallbackRatioSizes by lazy { sizes(fallbackRatio) }

        return if (requestedSize == Size.Invalid) {
            fallbackRatioSizes.lastOrNull()
        } else {
            runCatching {
                sizes(AspectRatio.of(requestedSize))
                    .chooseOptimalSize(requestedSize.width, requestedSize.height)
            }
                .recoverCatching {
                    fallbackRatioSizes
                        .chooseOptimalSize(requestedSize.width, requestedSize.height)
                }
                .getOrNull()
        }
    }

    fun clear() = map.clear()
}
