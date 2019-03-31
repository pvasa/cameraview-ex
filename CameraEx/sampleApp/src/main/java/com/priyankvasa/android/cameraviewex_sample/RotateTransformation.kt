package com.priyankvasa.android.cameraviewex_sample

import android.graphics.Bitmap
import android.graphics.Matrix
import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool
import com.bumptech.glide.load.resource.bitmap.BitmapTransformation
import java.security.MessageDigest

class RotateTransformation(private val rotation: Int) : BitmapTransformation() {

    override fun updateDiskCacheKey(messageDigest: MessageDigest) {
        messageDigest.update(ID_BYTES)
    }

    override fun transform(
        pool: BitmapPool,
        toTransform: Bitmap,
        outWidth: Int,
        outHeight: Int
    ): Bitmap = when (rotation) {

        0 -> toTransform

        180 -> Bitmap.createBitmap(
            toTransform,
            0,
            0,
            outWidth,
            outHeight,
            Matrix().apply { postRotate(180f) },
            true
        )

        else -> Bitmap.createBitmap(
            toTransform,
            0,
            0,
            outHeight,
            outWidth,
            Matrix().apply { postRotate(rotation.toFloat()) },
            true
        )
    }

    override fun equals(other: Any?): Boolean = other is RotateTransformation

    override fun hashCode(): Int = ID.hashCode()

    companion object {
        private val ID: String = RotateTransformation::class.java.run { canonicalName ?: name }
        private val ID_BYTES: ByteArray = ID.byteInputStream(Charsets.UTF_8).readBytes()
    }
}