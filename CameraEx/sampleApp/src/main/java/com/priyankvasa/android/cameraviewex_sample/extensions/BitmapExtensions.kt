package com.priyankvasa.android.cameraviewex_sample.extensions

import android.graphics.Bitmap
import android.graphics.Matrix

fun Bitmap.rotate(rotation: Int): Bitmap = when (rotation) {

    0 -> this

    180 -> Bitmap.createBitmap(
        this,
        0,
        0,
        width,
        height,
        Matrix().apply { postRotate(180f) },
        true
    )

    else -> Bitmap.createBitmap(
        this,
        0,
        0,
        width,
        height,
        Matrix().apply { postRotate(rotation.toFloat()) },
        true
    )
}