package com.priyankvasa.android.cameraviewex_sample.extensions

import android.graphics.Bitmap
import android.graphics.Matrix

private val matrix: Matrix = Matrix()

fun Bitmap.rotate(rotation: Int): Bitmap = when (rotation) {

    0 -> this

    else -> Bitmap.createBitmap(
        this,
        0,
        0,
        width,
        height,
        matrix.apply { setRotate(rotation.toFloat()) },
        true
    )
}