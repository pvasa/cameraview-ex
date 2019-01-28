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

import android.annotation.TargetApi
import android.content.Context
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.os.Build
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import kotlinx.android.synthetic.main.texture_view.view.*

@TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
internal class TextureViewPreview(context: Context, parent: ViewGroup) : PreviewImpl() {

    private val textureView: TextureView =
            View.inflate(context, R.layout.texture_view, parent).textureView

    private var displayOrientation: Int = 0

    override val surface: Surface get() = Surface(textureView.surfaceTexture)

    override val surfaceTexture: SurfaceTexture? get() = textureView.surfaceTexture

    override val view: View get() = textureView

    override val outputClass: Class<*> get() = SurfaceTexture::class.java

    override val isReady: Boolean get() = textureView.surfaceTexture != null

    init {
        textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {

            override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                setSize(width, height)
                configureTransform()
                surfaceChangeListener?.invoke()
            }

            override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
                setSize(width, height)
                configureTransform()
                surfaceChangeListener?.invoke()
            }

            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                setSize(0, 0)
                return true
            }

            override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit
        }

        textureView.setOnTouchListener(SurfaceTouchListener(
                context,
                { x, y -> surfaceTapListener?.invoke(x, y) ?: false },
                { scaleFactor -> surfacePinchListener?.invoke(scaleFactor) ?: false }
        ))
    }

    // This method is called only from Camera2.
    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1)
    override fun setBufferSize(width: Int, height: Int) {
        textureView.surfaceTexture.setDefaultBufferSize(width, height)
    }

    override fun setDisplayOrientation(displayOrientation: Int) {
        this.displayOrientation = displayOrientation
        configureTransform()
    }

    /**
     * Configures the transform matrix for TextureView based on [displayOrientation] and
     * the surface size.
     */
    fun configureTransform() {

        val matrix = Matrix()

        if (displayOrientation % 180 == 90) {

            val width = width
            val height = height

            val src = floatArrayOf(
                    0f, 0f, // top left
                    width.toFloat(), 0f, // top right
                    0f, height.toFloat(), // bottom left
                    width.toFloat(), height.toFloat() // bottom right
            )

            val dst = if (displayOrientation == 90) // Clockwise
                floatArrayOf(
                        0f, height.toFloat(), // top left
                        0f, 0f, // top right
                        width.toFloat(), height.toFloat(), // bottom left
                        width.toFloat(), 0f // bottom right
                )
            else
            // displayOrientation == 270
            // Counter-clockwise
                floatArrayOf(
                        width.toFloat(), 0f, // top left
                        width.toFloat(), height.toFloat(), // top right
                        0f, 0f, // bottom left
                        0f, height.toFloat() // bottom right
                )

            // Rotate the camera preview when the screen is landscape.
            matrix.setPolyToPoly(src, 0, dst, 0, 4)
        } else if (displayOrientation == 180) {
            matrix.postRotate(180f, (width / 2).toFloat(), (height / 2).toFloat())
        }
        textureView.setTransform(matrix)
    }
}