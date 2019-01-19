package com.priyankvasa.android.cameraviewex.extension

import android.os.Looper

val Thread.isUiThread: Boolean get() = this == Looper.getMainLooper().thread