package com.priyankvasa.android.cameraviewex_sample.extensions

import android.content.Context
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

fun Context.toast(message: String) = GlobalScope.launch(Dispatchers.Main) {
    Toast.makeText(this@toast, message, Toast.LENGTH_LONG).show()
}