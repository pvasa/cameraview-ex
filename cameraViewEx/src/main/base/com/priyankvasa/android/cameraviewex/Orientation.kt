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

sealed class Orientation(val value: Int) {

    object Portrait : Orientation(0)
    object PortraitInverted : Orientation(180)

    object Landscape : Orientation(270)
    object LandscapeInverted : Orientation(90)

    object Unknown : Orientation(-1)

    companion object {

        private val portraitRange1 = IntRange(350, 359)
        private val portraitRange2 = IntRange(0, 10)
        private val portraitInvertedRange = IntRange(170, 190)

        private val landscapeRange = IntRange(260, 280)
        private val landscapeInvertedRange = IntRange(80, 100)

        /**
         * Parse sensor orientation read from [android.view.OrientationEventListener] to one of
         * [Portrait], [PortraitInverted], [Landscape], or [LandscapeInverted]
         *
         * @param orientation available from [android.view.OrientationEventListener.onOrientationChanged]
         */
        fun parse(orientation: Int): Orientation = when {
            portraitRange1.contains(orientation) || portraitRange2.contains(orientation) -> Portrait
            portraitInvertedRange.contains(orientation) -> PortraitInverted
            landscapeRange.contains(orientation) -> Landscape
            landscapeInvertedRange.contains(orientation) -> LandscapeInverted
            else -> Unknown
        }
    }
}