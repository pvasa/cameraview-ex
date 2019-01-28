/*
 * Copyright 2019 Priyank Vasa
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.priyankvasa.android.cameraviewex

import android.content.Context
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View

/**
 * Touch listener for handling touch events on camera surface.
 * @param context
 * @param tapAction action to be performed if a single tap is detected
 * @param pinchAction action to be performed when a pinch gesture is detected
 */
internal class SurfaceTouchListener(
    context: Context,
    private val tapAction: (x: Float, y: Float) -> Boolean,
    private val pinchAction: (scaleFactor: Float) -> Boolean
) : View.OnTouchListener {

    private val scaleGestureListener = object : ScaleGestureDetector.SimpleOnScaleGestureListener() {

        override fun onScale(detector: ScaleGestureDetector): Boolean =
            if (detector.scaleFactor != 1f) pinchAction(detector.scaleFactor) else false
    }

    private val tapGestureListener = object : GestureDetector.SimpleOnGestureListener() {

        override fun onSingleTapConfirmed(e: MotionEvent?): Boolean =
            e?.let { tapAction(it.x, it.y) } ?: false
    }

    private val simpleGestureDetector = GestureDetector(context, tapGestureListener)

    private val scaleGestureDetector = ScaleGestureDetector(context, scaleGestureListener)

    override fun onTouch(v: View?, event: MotionEvent?): Boolean =
        simpleGestureDetector.onTouchEvent(event) ||
            scaleGestureDetector.onTouchEvent(event) ||
            event?.actionMasked != MotionEvent.ACTION_UP ||
            v?.performClick() ?: false
}