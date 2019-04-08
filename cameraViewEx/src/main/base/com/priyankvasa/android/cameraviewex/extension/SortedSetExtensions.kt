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
 * Choose smallest of sizes which has width >= [previewWidth] `AND` height >= [previewHeight].
 * Best case would be a size with exact dimensions of [previewWidth] and [previewHeight].
 * If none found then choose largest of sizes whose width < [previewWidth] `OR` height < [previewHeight].
 * If still nothing found then choose largest of the whole set; if set is empty return [Size.Invalid].
 *
 * @return The picked optimal size.
 */
internal fun SortedSet<Size>.chooseOptimalPreviewSize(previewWidth: Int, previewHeight: Int): Size =
    if (isNotEmpty()) firstOrNull { it.width >= previewWidth && it.height >= previewHeight }
        ?: lastOrNull { it.width < previewWidth || it.height < previewHeight }
        ?: last()
    else Size.Invalid