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
import android.os.Handler
import android.support.annotation.ColorInt
import android.support.annotation.ColorRes
import android.support.transition.TransitionManager
import android.support.v4.app.ActivityCompat
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

    @ColorInt
    private val defaultShutterColor: Int = ActivityCompat.getColor(context, android.R.color.black)

    internal var shutterTime = Modes.DEFAULT_SHUTTER

    init {
        visibility = View.GONE
        setBackgroundColor(defaultShutterColor)
    }

    internal fun setShutterColor(@ColorRes colorRes: Int) {
        @ColorInt val color: Int =
            runCatching { ActivityCompat.getColor(context, colorRes) }
                .getOrElse {
                    Timber.w(it, "Color resource $colorRes not found. Falling back to default color $defaultShutterColor")
                    defaultShutterColor
                }
        setBackgroundColor(color)
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