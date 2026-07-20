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

    /**
     * Recommended delay between dynamic-mode frames. The RP5 transport conflates
     * writes at roughly 80 ms, so the render loop paces itself to this instead of
     * generating frames that would be dropped. Centralized here so it stays
     * per-device tunable and measurable.
     */
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
