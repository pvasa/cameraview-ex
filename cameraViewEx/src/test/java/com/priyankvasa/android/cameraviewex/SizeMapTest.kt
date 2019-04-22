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

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SizeMapTest {

    @Test
    fun testAdd_simple() {
        val map = SizeMap()
        map.add(3, 4)
        map.add(9, 16)
        assertEquals(map.ratios().size, 4)
    }

    @Test
    fun testAdd_duplicate() {
        val map = SizeMap()
        map.add(3, 4)
        map.add(6, 8)
        map.add(9, 12)
        assertEquals(map.ratios().size, 2)
        val ratio = map.ratios().toTypedArray()[0]
        assertEquals(ratio.toString(), "3:4")
        assertEquals(map.sizes(ratio).size, 3)
    }

    @Test
    fun testClear() {
        val map = SizeMap()
        map.add(12, 34)
        assertEquals(map.ratios().size, 2)
        map.clear()
        assertEquals(map.ratios().size, 0)
    }
}