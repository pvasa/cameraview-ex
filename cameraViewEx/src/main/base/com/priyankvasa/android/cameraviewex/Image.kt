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

import com.priyankvasa.android.cameraviewex.exif.ExifInterface

/**
 * Data class to wrap preview frames or captured image data.
 *
 * @param data preview/capture frame data [ByteArray]
 * @param width of the frame
 * @param height of the frame
 * @param exifInterface contains frame metadata like orientation by which the frame needs to be rotated.
 *   For the image to be upright, it should be rotated by [ExifInterface.getRotation]
 * @param format image format of frame from [Modes.OutputFormat].
 */
data class Image internal constructor(
    val data: ByteArray,
    val width: Int,
    val height: Int,
    val exifInterface: ExifInterface,
    @Modes.OutputFormat val format: Int
) {

    override fun equals(other: Any?): Boolean =
        this === other ||
            (other is Image &&
                width == other.width &&
                height == other.height &&
                format == other.format &&
                exifInterface == other.exifInterface &&
                data.contentEquals(other.data))

    override fun hashCode(): Int {
        var result: Int = data.contentHashCode()
        result = 31 * result + width
        result = 31 * result + height
        result = 31 * result + format
        return result
    }
}