package com.priyankvasa.android.cameraviewex_sample.extensions

import android.content.Context
import android.widget.Toast

fun Context.toast(message: String) {
    Toast.makeText(this@toast, message, Toast.LENGTH_LONG).show()
}