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

import android.annotation.SuppressLint
import android.content.Context
import android.support.v4.view.ViewCompat
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import kotlinx.android.synthetic.main.surface_view.view.*

@SuppressLint("ClickableViewAccessibility")
internal class SurfaceViewPreview(context: Context, parent: ViewGroup) : PreviewImpl() {

    val surfaceView: SurfaceView = View.inflate(context, R.layout.surface_view, parent).surfaceView

    override val surface: Surface get() = surfaceHolder.surface

    override val surfaceHolder: SurfaceHolder get() = surfaceView.holder

    override val view: View get() = surfaceView

    override val outputClass: Class<*> get() = SurfaceHolder::class.java

    override val isReady: Boolean get() = width != 0 && height != 0

    init {

        surfaceView.holder.addCallback(object : SurfaceHolder.Callback {

            override fun surfaceCreated(h: SurfaceHolder) {}

            override fun surfaceChanged(h: SurfaceHolder, format: Int, width: Int, height: Int) {
                setSize(width, height)
                if (!ViewCompat.isInLayout(surfaceView)) surfaceChangeListener?.invoke()
            }

            override fun surfaceDestroyed(h: SurfaceHolder) {
                setSize(0, 0)
            }
        })

        surfaceView.setOnTouchListener(SurfaceTouchListener(
            context,
            { x, y -> surfaceTapListener?.invoke(x, y) ?: false },
            { scaleFactor -> surfacePinchListener?.invoke(scaleFactor) ?: false }
        ))
    }

    override fun setDisplayOrientation(displayOrientation: Int) {}
}