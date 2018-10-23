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

package com.priyankvasa.android.cameraview

import org.hamcrest.CoreMatchers.`is`
import org.junit.Assert.assertThat
import org.junit.Test
import java.util.HashSet

class AspectRatioTest {

    @Test
    fun testGcd() {
        var r: AspectRatio = AspectRatio.of(1, 2)
        assertThat(r.x, `is`(1))
        r = AspectRatio.of(2, 4)
        assertThat(r.x, `is`(1))
        assertThat(r.y, `is`(2))
        r = AspectRatio.of(391, 713)
        assertThat(r.x, `is`(17))
        assertThat(r.y, `is`(31))
    }

    @Test
    fun testMatches() {
        val ratio = AspectRatio.of(3, 4)
        assertThat(ratio.matches(Size(6, 8)), `is`(true))
        assertThat(ratio.matches(Size(1, 2)), `is`(false))
    }

    @Test
    fun testGetters() {
        val ratio = AspectRatio.of(2, 4) // Reduced to 1:2
        assertThat(ratio.x, `is`(1))
        assertThat(ratio.y, `is`(2))
    }

    @Test
    fun testToString() {
        val ratio = AspectRatio.of(1, 2)
        assertThat(ratio.toString(), `is`("1:2"))
    }

    @Test
    fun testEquals() {
        val a = AspectRatio.of(1, 2)
        val b = AspectRatio.of(2, 4)
        val c = AspectRatio.of(2, 3)
        assertThat(a == b, `is`(true))
        assertThat(a == c, `is`(false))
    }

    @Test
    fun testHashCode() {
        val max = 100
        val codes = HashSet<Int>()
        for (x in 1..100) {
            codes.add(AspectRatio.of(x, 1).hashCode())
        }
        assertThat(codes.size, `is`(max))
        codes.clear()
        for (y in 1..100) {
            codes.add(AspectRatio.of(1, y).hashCode())
        }
        assertThat(codes.size, `is`(max))
    }

    @Test
    fun testInverse() {
        val r = AspectRatio.of(4, 3)
        assertThat(r.x, `is`(4))
        assertThat(r.y, `is`(3))
        val s = r.inverse()
        assertThat(s.x, `is`(3))
        assertThat(s.y, `is`(4))
    }

    @Test
    fun testParse() {
        val r = AspectRatio.parse("23:31")
        assertThat(r.x, `is`(23))
        assertThat(r.y, `is`(31))
    }

    @Test(expected = IllegalArgumentException::class)
    fun testParseFailure() {
        AspectRatio.parse("MALFORMED")
    }
}
