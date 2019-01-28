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

package com.priyankvasa.android.cameraviewex.extension

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