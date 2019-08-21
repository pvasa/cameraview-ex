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
 * Chooses the optimal size based on [requestedWidth] and [requestedHeight].
 *
 * Find the longer and shorter edge from provided dimensions.
 * Choose smallest of sizes which has width >= longerEdge `AND` height >= shorterEdge.
 * Best case would be a size with exact dimensions as [requestedWidth] and [requestedHeight].
 * If none found then choose largest of sizes whose width < longerEdge `OR` height < shorterEdge.
 * If again found null, choose highest available size in the set.
 *
 * @return The picked optimal size.
 * @throws UnsupportedOperationException when the receiver set is empty
 */
internal fun SortedSet<Size>.chooseOptimalSize(requestedWidth: Int, requestedHeight: Int): Size {

    if (isEmpty()) throw UnsupportedOperationException("No sizes to choose from.")

    val (surfaceLonger: Int, surfaceShorter: Int) =
        if (requestedWidth > requestedHeight) requestedWidth to requestedHeight
        else requestedHeight to requestedWidth

    return firstOrNull { it.width >= surfaceLonger && it.height >= surfaceShorter }
        ?: lastOrNull { it.width < requestedWidth || it.height < requestedHeight }
        ?: last()
}
