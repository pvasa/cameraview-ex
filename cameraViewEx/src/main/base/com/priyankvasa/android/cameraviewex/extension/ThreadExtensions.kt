package com.priyankvasa.android.cameraviewex.extension

import android.os.Looper

internal val Thread.isUiThread: Boolean get() = this == Looper.getMainLooper().thread