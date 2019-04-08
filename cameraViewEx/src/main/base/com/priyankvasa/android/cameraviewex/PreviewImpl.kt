/*
 * Copyright 2019 Priyank Vasa
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

import android.content.Context
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.SurfaceTexture
import android.support.transition.TransitionManager
import android.support.v4.math.MathUtils
import android.view.Surface
import android.view.SurfaceHolder
import android.view.View
import android.view.ViewGroup
import com.priyankvasa.android.cameraviewex.extension.convertDpToPixelF
import kotlin.math.roundToInt

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

    internal var surfaceTapListener: ((x: Float, y: Float) -> Boolean)? = null

    internal var surfacePinchListener: ((scaleFactor: Float) -> Boolean)? = null

    internal abstract val view: View

    internal val context: Context get() = view.context

    val displaySize: Size by lazy { view.resources.displayMetrics.run { Size(widthPixels, heightPixels) } }

    private val overlayView: PreviewOverlayView by lazy { PreviewOverlayView(view.context) }

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

    internal fun markTouchAreas(rects: Array<Rect>) {

        (view.parent as? ViewGroup)?.run {
            overlayView.rects = rects
            removeView(overlayView)
            addView(overlayView)
        }
    }

    internal fun calculateTouchAreaRect(
        surfaceWidth: Int,
        surfaceHeight: Int,
        centerX: Float,
        centerY: Float,
        sideLengthInDp: Float = 80f
    ): Rect {

        val areaSize: Float = context.convertDpToPixelF(sideLengthInDp)

        val left: Float = MathUtils.clamp(centerX - areaSize / 2, 0f, surfaceWidth - areaSize)
        val top: Float = MathUtils.clamp(centerY - areaSize / 2, 0f, surfaceHeight - areaSize)

        val rectF = RectF(left, top, left + areaSize, top + areaSize)

        return Rect(
            rectF.left.roundToInt(),
            rectF.top.roundToInt(),
            rectF.right.roundToInt(),
            rectF.bottom.roundToInt()
        )
    }

    internal fun removeOverlay() {
        (view.parent as? ViewGroup)?.run {
            TransitionManager.beginDelayedTransition(this)
            removeView(overlayView)
        }
    }

    internal fun measure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        view.measure(widthMeasureSpec, heightMeasureSpec)
        shutterView.measure(widthMeasureSpec, heightMeasureSpec)
    }
}