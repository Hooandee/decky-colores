package com.hooandee.colores.led

data class RgbColor(
    val red: Int,
    val green: Int,
    val blue: Int,
)

data class LedState(
    val zoneColors: List<RgbColor>,
    val brightness: Int,
    val power: Boolean,
)

interface LedDevice {
    val available: Boolean
    val supportsPerZone: Boolean

    val recommendedFrameIntervalMs: Long
        get() = 80L

    suspend fun readState(): LedState

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
