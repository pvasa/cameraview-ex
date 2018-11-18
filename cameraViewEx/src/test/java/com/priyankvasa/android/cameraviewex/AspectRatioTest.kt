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
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.HashSet

class AspectRatioTest {

    @Test
    fun testGcd() {
        var r: AspectRatio = AspectRatio.of(1, 2)
        assertEquals(r.x, 1)
        r = AspectRatio.of(2, 4)
        assertEquals(r.x, 1)
        assertEquals(r.y, 2)
        r = AspectRatio.of(391, 713)
        assertEquals(r.x, 17)
        assertEquals(r.y, 31)
    }

    @Test
    fun testMatches() {
        val ratio = AspectRatio.of(3, 4)
        assertTrue(ratio.matches(Size(6, 8)))
        assertFalse(ratio.matches(Size(1, 2)))
    }

    @Test
    fun testGetters() {
        val ratio = AspectRatio.of(2, 4) // Reduced to 1:2
        assertEquals(ratio.x, 1)
        assertEquals(ratio.y, 2)
    }

    @Test
    fun testToString() {
        val ratio = AspectRatio.of(1, 2)
        assertEquals(ratio.toString(), "1:2")
    }

    @Test
    fun testEquals() {
        val a = AspectRatio.of(1, 2)
        val b = AspectRatio.of(2, 4)
        val c = AspectRatio.of(2, 3)
        assertEquals(a, b)
        assertNotEquals(a, c)
    }

    @Test
    fun testHashCode() {
        val max = 100
        val codes = HashSet<Int>()
        for (x in 1..100) codes.add(AspectRatio.of(x, 1).hashCode())
        assertEquals(codes.size, max)
        codes.clear()
        for (y in 1..100) codes.add(AspectRatio.of(1, y).hashCode())
        assertEquals(codes.size, max)
    }

    @Test
    fun testInverse() {
        val r = AspectRatio.of(4, 3)
        assertEquals(r.x, 4)
        assertEquals(r.y, 3)
        val s = r.inverse()
        assertEquals(s.x, 3)
        assertEquals(s.y, 4)
    }

    @Test
    fun testParse() {
        val r = AspectRatio.parse("23:31")
        assertEquals(r.x, 23)
        assertEquals(r.y, 31)
    }

    @Test
    fun testParseFailure() {
        assertThrows<IllegalArgumentException> { AspectRatio.parse("MALFORMED") }
    }
}