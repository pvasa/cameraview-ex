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

package com.priyankvasa.android.cameraviewex

/**
 * Data class to wrap frame data generated from preview frame listener for [Camera1].
 *
 * @param data preview frame data [ByteArray]
 * @param width of the preview frame
 * @param height of the preview frame
 * @param format image format of preview frame [android.graphics.ImageFormat]. Usually this would be [android.graphics.ImageFormat.NV21]
 */
data class LegacyImage(val data: ByteArray, val width: Int, val height: Int, val format: Int) {

    override fun equals(other: Any?): Boolean = this === other ||
        (javaClass == other?.javaClass &&
            data.contentEquals((other as LegacyImage).data) &&
            width == other.width &&
            height == other.height &&
            format == other.format)

    override fun hashCode(): Int {
        var result = data.contentHashCode()
        result = 31 * result + width
        result = 31 * result + height
        result = 31 * result + format
        return result
    }
}