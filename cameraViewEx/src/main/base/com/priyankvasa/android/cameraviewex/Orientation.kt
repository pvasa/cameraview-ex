package com.priyankvasa.android.cameraviewex

sealed class Orientation {

    object Portrait : Orientation()
    object PortraitInverted : Orientation()

    object Landscape : Orientation()
    object LandscapeInverted : Orientation()

    object Unknown : Orientation()

    companion object {

        private val portraitRange1 = IntRange(350, 359)
        private val portraitRange2 = IntRange(350, 10)
        private val portraitInvertedRange = IntRange(170, 190)

        private val landscapeRange = IntRange(260, 280)
        private val landscapeInvertedRange = IntRange(80, 100)

        fun parse(orientation: Int): Orientation = when {
            portraitRange1.contains(orientation) || portraitRange2.contains(orientation) -> Portrait
            portraitInvertedRange.contains(orientation) -> PortraitInverted
            landscapeRange.contains(orientation) -> Landscape
            landscapeInvertedRange.contains(orientation) -> LandscapeInverted
            else -> Unknown
        }
    }
}