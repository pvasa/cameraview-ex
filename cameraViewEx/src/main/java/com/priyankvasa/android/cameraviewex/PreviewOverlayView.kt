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
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.support.v4.app.ActivityCompat
import android.view.View
import com.priyankvasa.android.cameraviewex.extension.convertDpToPixelF

internal class PreviewOverlayView(context: Context) : View(context) {

    internal var rects: Array<Rect>? = null

    init {
        setBackgroundColor(ActivityCompat.getColor(context, android.R.color.transparent))
    }

    private val paint: Paint by lazy {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = context.convertDpToPixelF(2f)
        }
    }

    override fun onDraw(canvas: Canvas?) {
        super.onDraw(canvas)
        rects?.forEach { canvas?.drawCircle(it.exactCenterX(), it.exactCenterY(), it.width() / 2f, paint) }
    }
}