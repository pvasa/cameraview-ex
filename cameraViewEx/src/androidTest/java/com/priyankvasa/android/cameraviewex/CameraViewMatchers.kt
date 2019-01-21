/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.priyankvasa.android.cameraviewex

import android.view.View

import org.hamcrest.Description
import org.hamcrest.Matcher
import org.hamcrest.TypeSafeMatcher

internal object CameraViewMatchers {

    fun hasAspectRatio(ratio: AspectRatio): Matcher<View> = object : TypeSafeMatcher<View>() {

        override fun describeTo(description: Description) {
            description.appendText("has aspect ratio of $ratio")
        }

        override fun matchesSafely(view: View): Boolean =
            ratio == (view as CameraView).aspectRatio
    }
}