package com.priyankvasa.android.cameraviewex.extension

import android.media.Image
import android.os.Build
import android.support.annotation.RequiresApi

val Image.cropWidth: Int
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    get() = cropRect.width()

val Image.cropHeight: Int
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    get() = cropRect.height()