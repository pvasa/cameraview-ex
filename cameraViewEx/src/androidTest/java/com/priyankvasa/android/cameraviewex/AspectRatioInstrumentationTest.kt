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

import android.os.Parcel
import android.support.test.runner.AndroidJUnit4
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.sameInstance
import org.hamcrest.core.Is.`is`
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AspectRatioInstrumentationTest {

    @Test
    fun testParcel() {
        val original = AspectRatio.of(4, 3)
        val parcel = Parcel.obtain()
        try {
            parcel.writeParcelable(original, 0)
            parcel.setDataPosition(0)
            val restored = parcel.readParcelable<AspectRatio>(javaClass.classLoader)
            assertNotNull(restored)
            assertThat(restored?.x, `is`(4))
            assertThat(restored?.y, `is`(3))
            // As the first instance is alive, the parceled result should still be the same instance
            assertThat(restored, `is`(sameInstance(original)))
        } finally {
            parcel.recycle()
        }
    }
}