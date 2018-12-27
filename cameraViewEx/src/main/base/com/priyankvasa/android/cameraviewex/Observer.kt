package com.priyankvasa.android.cameraviewex

import androidx.lifecycle.Observer

internal interface Observer<T> : Observer<T> {

    fun onChangedNonNull(t: T) = onChanged(t)
}