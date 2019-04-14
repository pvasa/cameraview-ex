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
import android.renderscript.Script
import android.renderscript.ScriptIntrinsicYuvToRGB
import android.renderscript.Type
import android.support.annotation.RequiresApi
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer


/**
 * Decode receiver [Image] to a byte array based on format of the image
 *
 * @param outputFormat from [Modes.OutputFormat]
 * @param rs [RenderScript] instance to be used for native decoding
 * @return [ByteArray] image data
 */
@Throws(IllegalStateException::class, IllegalArgumentException::class)
@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
internal fun Image.decode(outputFormat: Int, rs: RenderScript): ByteArray {

    val image: Image = this@decode

    return when (image.format) {

        ImageFormat.JPEG -> ImageProcessor.jpegImageData(image)

        ImageFormat.YUV_420_888 -> when (outputFormat) {
            Modes.OutputFormat.YUV_420_888 -> ImageProcessor.yuvImageData(image)
            Modes.OutputFormat.RGBA_8888 -> ImageProcessor.yuvToRgbNative(image, rs)
            else -> throw IllegalArgumentException("Output format $outputFormat is invalid.")
        }

        else -> throw IllegalArgumentException("Image format ${image.format} is not supported.")
    }
}

@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
object ImageProcessor {

    fun jpegImageData(image: Image): ByteArray {

        val startTime: Long = SystemClock.elapsedRealtime()

        val adjustedWidth: Int = image.cropRect.width()
        val adjustedHeight: Int = image.cropRect.height()

        val buffer: ByteBuffer = image.planes[0].buffer.apply { rewind() }

        val imageData: ByteArray = ByteArray(buffer.remaining()).apply { buffer.get(this) }

        Timber.d("Normal processing time: ${SystemClock.elapsedRealtime() - startTime}")

        if (adjustedWidth == image.width && adjustedHeight == image.height) return imageData

        val croppedBitmap: Bitmap = BitmapRegionDecoder.newInstance(imageData, 0, imageData.size, true)
            ?.decodeRegion(image.cropRect, null)
            ?: throw IllegalArgumentException("Provided image data could not be decoded.")

        return ByteArrayOutputStream(croppedBitmap.byteCount)
            .apply { croppedBitmap.compress(Bitmap.CompressFormat.JPEG, 100, this) }
            .toByteArray()
            .also {
                croppedBitmap.recycle()
                Timber.d("Extra processing time: ${SystemClock.elapsedRealtime() - startTime}")
            }
    }

    private fun Image.requireValidYuv() {
        if ((format != ImageFormat.YUV_420_888 &&
                format != ImageFormat.NV21 &&
                format != ImageFormat.YV12) ||
            planes.size < 3) {
            throw IllegalArgumentException("This is not a valid YUV image.")
        }
    }

    fun yuvImageData(image: Image): ByteArray {

        image.requireValidYuv()

        val imageWidth: Int = image.cropRect.width()
        val imageHeight: Int = image.cropRect.height()

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

    // todo remove after finalizing above algo
    fun getDataFromImage(image: Image): ByteArray {
        val format = image.format
        val width = image.cropRect.width()
        val height = image.cropRect.height()
        var rowStride: Int
        var pixelStride: Int
        // Read image data
        val planes = image.planes
        // Check image validity
        var buffer: ByteBuffer
        var offset = 0
        val data = ByteArray(width * height * ImageFormat.getBitsPerPixel(format) / 8)
        val rowData = ByteArray(planes!![0].rowStride)
        for (i in planes.indices) {
            buffer = planes[i].buffer
            rowStride = planes[i].rowStride
            pixelStride = planes[i].pixelStride
            // For multi-planar yuv images, assuming yuv420 with 2x2 chroma subsampling.
            val w = if (i == 0) width else width / 2
            val h = if (i == 0) height else height / 2
            for (row in 0 until h) {
                val bytesPerPixel = ImageFormat.getBitsPerPixel(format) / 8
                if (pixelStride == bytesPerPixel) {
                    // Special case: optimized read of the entire row
                    val length = w * bytesPerPixel
                    buffer.get(data, offset, length)
                    // Advance buffer the remainder of the row stride
                    buffer.position(buffer.position() + rowStride - length)
                    offset += length
                } else {
                    // Generic case: should work for any pixelStride but slower.
                    // Use intermediate buffer to avoid read byte-by-byte from
                    // DirectByteBuffer, which is very bad for performance
                    buffer.get(rowData, 0, rowStride)
                    for (col in 0 until w) {
                        data[offset++] = rowData[col * pixelStride]
                    }
                }
            }
        }
        return data
    }

    fun yuvToRgbNative(image: Image, rs: RenderScript): ByteArray {

        image.requireValidYuv()

        val width: Int = image.cropRect.width()
        val height: Int = image.cropRect.height()

        // Get the three image planes
        val planes = image.planes
        var buffer = planes[0].buffer
        val y = ByteArray(buffer.remaining())
        buffer.get(y)

        buffer = planes[1].buffer
        val u = ByteArray(buffer.remaining())
        buffer.get(u)

        buffer = planes[2].buffer
        val v = ByteArray(buffer.remaining())
        buffer.get(v)

        // get the relevant RowStrides and PixelStrides
        // (we know from documentation that PixelStride is 1 for y)
        val yRowStride = planes[0].rowStride
        val uvRowStride = planes[1].rowStride // we know from documentation that RowStride is the same for u and v.
        assert(uvRowStride == planes[2].rowStride)
        val uvPixelStride = planes[1].pixelStride // we know from documentation that PixelStride is the same for u and v.
        assert(uvPixelStride == planes[2].pixelStride)

        val script = ScriptC_yuv420888(rs)

        // Y,U,V are defined as global allocations, the out-Allocation is the Bitmap.
        // Note also that uAlloc and vAlloc are 1-dimensional while yAlloc is 2-dimensional.
        val typeUcharY = Type.Builder(rs, Element.U8(rs))
        typeUcharY.setX(yRowStride).setY(height)
        val yAlloc = Allocation.createTyped(rs, typeUcharY.create())
        yAlloc.copyFrom(y)
        script._ypsIn = yAlloc

        val typeUcharUV = Type.Builder(rs, Element.U8(rs))
        // note that the size of the u's and v's are as follows:
        //      (  (width / 2) * PixelStride + padding  ) * (height / 2)
        // =    (RowStride                          ) * (height / 2)
        // but I noted that on the S7 it is 1 less...
        typeUcharUV.setX(u.size)
        val uAlloc = Allocation.createTyped(rs, typeUcharUV.create())
        uAlloc.copyFrom(u)
        script._uIn = uAlloc

        val vAlloc = Allocation.createTyped(rs, typeUcharUV.create())
        vAlloc.copyFrom(v)
        script._vIn = vAlloc

        // handover parameters
        script._picWidth = width.toLong()
        script._uvRowStride = uvRowStride.toLong()
        script._uvPixelStride = uvPixelStride.toLong()

        val outBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val outAlloc = Allocation.createFromBitmap(rs, outBitmap, Allocation.MipmapControl.MIPMAP_NONE, Allocation.USAGE_SCRIPT)
        val rgbData = ByteArray(outAlloc.bytesSize)

        val lo = Script.LaunchOptions().apply {
            setX(0, width)  // by this we ignore the y’s padding zone, i.e. the right side of x between width and yRowStride
            setY(0, height)
        }

        script.forEach_doConvert(outAlloc, lo)
        outAlloc.copyTo(rgbData)
        outAlloc.copyTo(outBitmap)

        return rgbData
    }

    fun yuvToRgb(image: Image, rs: RenderScript): ByteArray {

        image.requireValidYuv()

        val imageWidth: Int = image.cropRect.width()
        val imageHeight: Int = image.cropRect.height()

        val imageData = yuvImageData(image)

        val yuvType = Type.Builder(rs, Element.U8(rs))
            .setX(imageWidth)
            .setY(imageHeight)
            .setYuvFormat(ImageFormat.YUV_420_888)
            .create()
        val yuvAllocation: Allocation = Allocation.createTyped(
            rs,
            yuvType,
            Allocation.USAGE_SCRIPT
        ).apply { copyFrom(imageData) }

        val rgbType = Type.Builder(rs, Element.RGBA_8888(rs))
            .setX(imageWidth)
            .setY(imageHeight)
            .create()
        val rgbAllocation: Allocation = Allocation.createTyped(rs, rgbType, Allocation.USAGE_SCRIPT)

        // Create script
        ScriptIntrinsicYuvToRGB.create(rs, Element.RGBA_8888(rs)).apply {
            // Set input for script
            setInput(yuvAllocation)
            // Call script for output allocation
            forEach(rgbAllocation)
        }.also { it.destroy() }

        val rgbData = ByteArray(rgbAllocation.bytesSize)

        // Copy script result into byte array
        rgbAllocation.copyTo(rgbData)
        val bitmap = Bitmap.createBitmap(imageWidth, imageHeight, Bitmap.Config.ARGB_8888)
        rgbAllocation.copyTo(bitmap)

        rs.finish()

        //Destroy everything to free memory
        yuvAllocation.destroy()
        rgbAllocation.destroy()
        yuvType.destroy()
        rgbType.destroy()

        return rgbData
    }
}