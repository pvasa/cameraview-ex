package com.priyankvasa.android.cameraviewex_sample.camera

import com.priyankvasa.android.cameraviewex.AspectRatio

interface AspectRatiosRepository {

    val supportedAspectRatios: Set<AspectRatio>

    val currentAspectRatio: AspectRatio
}