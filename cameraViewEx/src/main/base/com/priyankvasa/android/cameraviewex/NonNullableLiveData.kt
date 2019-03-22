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

import android.arch.lifecycle.LifecycleOwner
import android.arch.lifecycle.MutableLiveData
import android.arch.lifecycle.Observer
import com.priyankvasa.android.cameraviewex.extension.isUiThread

internal class NonNullableLiveData<T>(private val defaultValue: T) : MutableLiveData<T>() {

    internal var value: T = defaultValue
        get() = super.getValue() ?: defaultValue
        internal set(value) {
            if (field == value) return
            lastValue = field
            field = value
            if (Thread.currentThread().isUiThread) super.setValue(value) else super.postValue(value)
        }

    init {
        value = defaultValue
    }

    private var lastValue: T = defaultValue

    override fun getValue(): T = value

    override fun setValue(value: T) {
        this.value = value
    }

    internal fun revert() {
        value = lastValue
    }

    fun observe(owner: LifecycleOwner, observer: (t: T) -> Unit) {
        super.observe(owner, Observer { observer(it ?: defaultValue) })
    }

    fun observeForeverNullSafe(observer: (t: T) -> Unit) {
        super.observeForever { observer(it ?: defaultValue) }
    }
}