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

package com.google.android.cameraview

import org.hamcrest.CoreMatchers.`is`
import org.junit.Assert.assertThat
import org.junit.Test
import java.util.HashSet

class SizeTest {

    @Test
    fun testGetters() {
        val size = Size(1, 2)
        assertThat(size.width, `is`(1))
        assertThat(size.height, `is`(2))
    }

    @Test
    fun testToString() {
        val size = Size(1, 2)
        assertThat(size.toString(), `is`("1x2"))
    }

    @Test
    fun testEquals() {
        val a = Size(1, 2)
        val b = Size(1, 2)
        val c = Size(3, 4)
        assertThat(a == b, `is`(true))
        assertThat(a == c, `is`(false))
    }

    @Test
    fun testHashCode() {
        val max = 100
        val codes = HashSet<Int>()
        (1..max).forEach { x -> (1..max).forEach { y -> codes.add(Size(x, y).hashCode()) } }
        assertThat(codes.size, `is`(max * max))
    }
}
