package com.priyankvasa.android.cameraviewex

// TODO: Add docs
data class LegacyImage(val data: ByteArray, val width: Int, val height: Int, val format: Int) {

    override fun equals(other: Any?): Boolean = this === other ||
        (javaClass == other?.javaClass &&
            data.contentEquals((other as LegacyImage).data) &&
            width == other.width &&
            height == other.height &&
            format == other.format)

    override fun hashCode(): Int {
        var result = data.contentHashCode()
        result = 31 * result + width
        result = 31 * result + height
        result = 31 * result + format
        return result
    }
}