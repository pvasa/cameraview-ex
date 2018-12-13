package com.priyankvasa.android.cameraviewex

import kotlin.reflect.KMutableProperty0
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.KProperty
import kotlin.reflect.KProperty0
import kotlin.reflect.KProperty1

internal operator fun <R> KProperty0<R>.getValue(instance: Any?, metadata: KProperty<*>): R = get()

internal operator fun <R> KMutableProperty0<R>.setValue(
        instance: Any?,
        metadata: KProperty<*>,
        value: R
) = set(value)

internal operator fun <T, R> KProperty1<T, R>.getValue(
        instance: T,
        metadata: KProperty<*>
): R = get(instance)

internal operator fun <T, R> KMutableProperty1<T, R>.setValue(
        instance: T,
        metadata: KProperty<*>,
        value: R
) = set(instance, value)
