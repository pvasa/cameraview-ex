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

package com.priyankvasa.android.cameraviewex.extension

import com.priyankvasa.android.cameraviewex.Size
import java.util.SortedSet

/**
 * Chooses the optimal size based on [previewWidth] and [previewHeight].
 *
 * Find the longer and shorter edge from preview dimensions.
 * Choose smallest of sizes which has width >= longerEdge `AND` height >= shorterEdge.
 * Best case would be a size with exact dimensions as [previewWidth] and [previewHeight].
 * If none found then choose largest of sizes whose width < longerEdge `OR` height < shorterEdge.
 *
 * If set is empty return [Size.Invalid].
 *
 * @return The picked optimal size.
 */
internal fun SortedSet<Size>.chooseOptimalPreviewSize(previewWidth: Int, previewHeight: Int): Size {

    if (isEmpty()) return Size.Invalid

    val (surfaceLonger: Int, surfaceShorter: Int) =
        if (previewWidth > previewHeight) previewWidth to previewHeight
        else previewHeight to previewWidth

    return firstOrNull { it.width >= surfaceLonger && it.height >= surfaceShorter }
        ?: lastOrNull { it.width < previewWidth || it.height < previewHeight }
        ?: last()
}