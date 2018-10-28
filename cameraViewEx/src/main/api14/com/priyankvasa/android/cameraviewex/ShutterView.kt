package com.priyankvasa.android.cameraviewex

import android.content.Context
import android.content.res.Resources
import android.os.Handler
import android.support.annotation.ColorRes
import android.support.v4.app.ActivityCompat
import android.transition.TransitionManager
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import timber.log.Timber

/**
 * A shutter effect. It can be used when user captures a picture to give them some kind of capture feedback.
 */
internal class ShutterView @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyle: Int = 0
) : View(context, attrs, defStyle) {

    private val defaultShutterColor = ActivityCompat.getColor(context, android.R.color.black)

    internal fun setShutterColor(@ColorRes colorRes: Int) {
        val color = try {
            ActivityCompat.getColor(context, colorRes)
        } catch (e: Resources.NotFoundException) {
            Timber.w(e, "Color resource $colorRes not found. Falling back to default color $defaultShutterColor")
            defaultShutterColor
        }
        setBackgroundColor(color)
    }

    internal var shutterTime = Modes.DEFAULT_SHUTTER

    init {
        visibility = View.GONE
        setBackgroundColor(defaultShutterColor)
    }

    /**
     * Show the shutter effect
     */
    internal fun show() {
        if (shutterTime > 0) {
            (parent as? ViewGroup)?.let { TransitionManager.beginDelayedTransition(it) }
            visibility = View.VISIBLE
            (handler ?: Handler()).postDelayed({ visibility = View.GONE }, shutterTime.toLong())
        }
    }
}