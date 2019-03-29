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
 * Chooses the optimal size based on respective supported sizes
 * and the surface size of [viewWidth] and [viewHeight].
 *
 * @return The picked optimal size.
 */
internal fun SortedSet<Size>.chooseOptimalPreviewSize(viewWidth: Int, viewHeight: Int): Size {

    val (maxWidth: Int, maxHeight: Int) =
        if (viewHeight > viewWidth) viewHeight to viewWidth
        else viewWidth to viewHeight

    return asSequence()
        .filter { it.width <= maxWidth && it.height <= maxHeight }
        .run {
            firstOrNull { it.width >= viewWidth && it.height >= viewHeight }
                ?: lastOrNull { it.width < viewWidth || it.height < viewHeight }
                ?: lastOrNull()
                ?: Size.Invalid
        }
}