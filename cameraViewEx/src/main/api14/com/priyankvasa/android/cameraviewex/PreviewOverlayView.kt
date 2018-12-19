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