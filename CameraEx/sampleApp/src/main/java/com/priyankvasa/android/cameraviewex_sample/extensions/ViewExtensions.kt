package com.priyankvasa.android.cameraviewex_sample.extensions

import android.transition.TransitionManager
import android.view.View
import android.view.ViewGroup

fun View.show() {
    (parent as? ViewGroup)?.let { TransitionManager.beginDelayedTransition(it) }
    visibility = View.VISIBLE
}

fun View.hide() {
    (parent as? ViewGroup)?.let { TransitionManager.beginDelayedTransition(it) }
    visibility = View.GONE
}

fun View.invisible() {
    (parent as? ViewGroup)?.let { TransitionManager.beginDelayedTransition(it) }
    visibility = View.INVISIBLE
}