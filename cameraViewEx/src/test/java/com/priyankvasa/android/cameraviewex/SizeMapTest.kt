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

import org.hamcrest.CoreMatchers.`is`
import org.junit.Assert.assertThat
import org.junit.Test

class SizeMapTest {

    @Test
    fun testAdd_simple() {
        val map = SizeMap()
        map.add(Size(3, 4))
        map.add(Size(9, 16))
        assertThat(map.ratios().size, `is`(2))
    }

    @Test
    fun testAdd_duplicate() {
        val map = SizeMap()
        map.add(Size(3, 4))
        map.add(Size(6, 8))
        map.add(Size(9, 12))
        assertThat(map.ratios().size, `is`(1))
        val ratio = map.ratios().toTypedArray()[0]
        assertThat(ratio.toString(), `is`("3:4"))
        assertThat(map.sizes(ratio).size, `is`(3))
    }

    @Test
    fun testClear() {
        val map = SizeMap()
        map.add(Size(12, 34))
        assertThat(map.ratios().size, `is`(1))
        map.clear()
        assertThat(map.ratios().size, `is`(0))
    }
}
