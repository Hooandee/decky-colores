package com.hooandee.colores.led

data class RgbColor(
    val red: Int,
    val green: Int,
    val blue: Int,
)

interface LedDevice {
    val available: Boolean
    val supportsPerZone: Boolean

    suspend fun applyZones(
        colors: List<RgbColor>,
        brightness: Int,
        power: Boolean,
    ): Boolean

    suspend fun applySolid(
        color: RgbColor,
        brightness: Int,
        power: Boolean,
    ): Boolean

    fun invalidate()
}
