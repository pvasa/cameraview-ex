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

import android.graphics.Bitmap
import android.graphics.BitmapRegionDecoder
import android.graphics.ImageFormat
import android.media.Image
import android.os.Build
import android.os.SystemClock
import android.renderscript.Allocation
import android.renderscript.Element
import android.renderscript.RenderScript
import android.renderscript.ScriptIntrinsicYuvToRGB
import android.renderscript.Type
import android.support.annotation.RequiresApi
import com.priyankvasa.android.cameraviewex.extension.cropHeight
import com.priyankvasa.android.cameraviewex.extension.cropWidth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.ByteBuffer

@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
@Throws(IllegalArgumentException::class)
fun Image.checkValidYuv() {
    if ((format != ImageFormat.YUV_420_888 &&
            format != ImageFormat.NV21 &&
            format != ImageFormat.YV12) ||
        planes.size < 3) {
        throw IllegalArgumentException("This is not a valid YUV image.")
    }
}

@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
class ImageProcessor(private val rs: RenderScript) {

    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Default)

    /**
     * Decode receiver [Image] to a byte array based on format of the image
     *
     * @param outputFormat from [Modes.OutputFormat]
     * @return [ByteArray] image data
     */
    @Throws(
        UnsupportedOperationException::class,
        IllegalStateException::class,
        IllegalArgumentException::class
    )
    internal fun decode(image: Image, outputFormat: Int): ByteArray = when (image.format) {

        ImageFormat.JPEG -> getJpegImageData(image)

        ImageFormat.YUV_420_888 -> when (outputFormat) {
            Modes.OutputFormat.YUV_420_888 -> getYuvImageData(image)
            Modes.OutputFormat.RGBA_8888 ->
                yuvToRgb(getYuvImageData(image), image.cropWidth, image.cropHeight)
            else -> throw IllegalArgumentException("Output format $outputFormat is invalid.")
        }

        else -> throw UnsupportedOperationException("Image format ${image.format} is not supported.")
    }

    @Throws(IllegalArgumentException::class, IllegalStateException::class, IOException::class)
    fun getJpegImageData(image: Image): ByteArray {

        val startTime: Long = SystemClock.elapsedRealtime()

        val adjustedWidth: Int = image.cropWidth
        val adjustedHeight: Int = image.cropHeight

        val buffer: ByteBuffer = image.planes[0].buffer.apply { rewind() }

        val imageData: ByteArray = ByteArray(buffer.remaining()).apply { buffer.get(this) }

        Timber.d("Normal processing time: ${SystemClock.elapsedRealtime() - startTime}")

        if (adjustedWidth == image.width && adjustedHeight == image.height) return imageData

        val croppedBitmap: Bitmap =
            BitmapRegionDecoder.newInstance(imageData, 0, imageData.size, true)
                ?.decodeRegion(image.cropRect, null)
                ?: throw IllegalStateException("Provided image data could not be decoded.")

        return ByteArrayOutputStream(croppedBitmap.byteCount)
            .apply { croppedBitmap.compress(Bitmap.CompressFormat.JPEG, 100, this) }
            .toByteArray()
            .also {
                croppedBitmap.recycle()
                Timber.d("Extra processing time: ${SystemClock.elapsedRealtime() - startTime}")
            }
    }

    @Throws(IllegalArgumentException::class)
    fun getYuvImageData(image: Image): ByteArray {

        image.checkValidYuv()

        val imageWidth: Int = image.cropWidth
        val imageHeight: Int = image.cropHeight

        val planes: Array<Image.Plane> = image.planes
        val wh: Int = imageWidth * imageHeight
        val halfWh: Int = wh / 2
        // dataSize = width * height * ImageFormat.getBitsPerPixel(format) / 8
        // ~= wh * 12 / 8
        // ~= wh + (wh / 2)
        val imageData = ByteArray(wh + halfWh)
        val uBuffer: ByteBuffer = planes[1].buffer
        val vBuffer: ByteBuffer = planes[2].buffer
        val vPos: Int = vBuffer.position()
        val uLimit: Int = uBuffer.limit()
        vBuffer.position(vPos + 1)
        uBuffer.limit(uLimit - 1)
        vBuffer.position(vPos)
        uBuffer.limit(uLimit)
        if (vBuffer.remaining() == halfWh - 2 && vBuffer.compareTo(uBuffer) == 0) {
            planes[0].buffer.get(imageData, 0, wh)
            vBuffer.get(imageData, wh, 1)
            uBuffer.get(imageData, wh + 1, halfWh - 1)
        } else {
            planes[0].process(imageWidth, imageHeight, imageData, 0)
            planes[1].process(imageWidth, imageHeight, imageData, wh + 1)
            planes[2].process(imageWidth, imageHeight, imageData, wh)
        }

        return imageData
    }

    private fun Image.Plane.process(imageWidth: Int, imageHeight: Int, imageData: ByteArray, length: Int) {

        val pos: Int = buffer.position()
        val rows: Int = (buffer.remaining() + rowStride - 1) / rowStride
        val k = imageHeight / rows
        val m = imageWidth / k
        var n = length
        var row = 0

        repeat(rows) {

            var thisRow = row

            repeat(m) {
                imageData[n] = buffer.get(thisRow)
                n += pixelStride
                thisRow += pixelStride
            }

            row += rowStride
        }

        buffer.position(pos)
    }

    @Throws(IllegalArgumentException::class)
    fun yuvToRgb(yuvImageData: ByteArray, imageWidth: Int, imageHeight: Int): ByteArray {

        val yuvType: Type = Type.Builder(rs, Element.YUV(rs))
            .setX(imageWidth)
            .setY(imageHeight)
            .setYuvFormat(ImageFormat.NV21)
            .create()

        val yuvAllocation: Allocation =
            Allocation.createTyped(rs, yuvType, Allocation.USAGE_SCRIPT)
                .apply { copyFrom(yuvImageData) }

        val rgbType: Type = Type.Builder(rs, Element.RGBA_8888(rs))
            .setX(imageWidth)
            .setY(imageHeight)
            .create()

        val rgbAllocation: Allocation =
            Allocation.createTyped(rs, rgbType, Allocation.USAGE_SCRIPT)

        // Create script
        val script: ScriptIntrinsicYuvToRGB = ScriptIntrinsicYuvToRGB.create(rs, Element.U8_4(rs))
        // Set input for script
        script.setInput(yuvAllocation)
        // Call script for output allocation
        script.forEach(rgbAllocation)

        val rgbData = ByteArray(rgbAllocation.bytesSize)

        // Copy script result into byte array
        rgbAllocation.copyTo(rgbData)

        // Destroy allocations and types to free memory
        coroutineScope.launch {
            yuvAllocation.destroy()
            rgbAllocation.destroy()
            yuvType.destroy()
            rgbType.destroy()
            script.destroy()
        }

        return rgbData
    }
}