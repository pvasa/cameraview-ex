/*
 * Copyright 2018 Priyank Vasa
 *
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.priyankvasa.android.cameraviewex

import android.graphics.Rect
import android.graphics.SurfaceTexture
import android.view.MotionEvent
import android.view.Surface
import android.view.SurfaceHolder
import android.view.View
import android.view.ViewGroup

/**
 * Encapsulates all the operations related to camera preview in a backward-compatible manner.
 */
internal abstract class PreviewImpl {

    var width: Int = 0
        private set

    var height: Int = 0
        private set

    internal abstract val surface: Surface?

    internal var surfaceChangeListener: (() -> Unit)? = null

    internal var surfaceTouchListener: ((event: MotionEvent?) -> Boolean)? = null

    internal abstract val view: View

    internal val overlayView: PreviewOverlayView by lazy { PreviewOverlayView(view.context) }

    internal val shutterView: ShutterView by lazy { ShutterView(view.context) }

    internal abstract val outputClass: Class<*>

    internal abstract val isReady: Boolean

    internal open val surfaceHolder: SurfaceHolder? get() = null

    internal open val surfaceTexture: SurfaceTexture? get() = null

    internal abstract fun setDisplayOrientation(displayOrientation: Int)

    internal open fun setBufferSize(width: Int, height: Int) {}

    internal fun setSize(width: Int, height: Int) {
        this.width = width
        this.height = height
    }

    internal fun markTouchArea(rects: Array<Rect>) {

        (view.parent as? ViewGroup)?.run {
            overlayView.rects = rects
            removeView(overlayView)
            addView(overlayView)
        }
    }

    internal fun removeOverlay() {
        (view.parent as? ViewGroup)?.apply { removeView(overlayView) }
    }
}