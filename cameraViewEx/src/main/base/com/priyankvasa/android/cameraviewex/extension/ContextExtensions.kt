package com.priyankvasa.android.cameraviewex.extension

import android.content.Context
import android.util.TypedValue

internal fun Context.convertDpToPixel(dp: Float) = convertDpToPixelF(dp).toInt()
internal fun Context.convertDpToPixelF(dp: Float) =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, resources.displayMetrics)
