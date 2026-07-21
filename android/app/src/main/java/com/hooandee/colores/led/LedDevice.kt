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

data class HardwareEffect(
    val id: String,
    val needsColor: Boolean,
    val defaultSpeed: Int,
    val colors: List<RgbColor>,
)

interface LedDevice {
    val available: Boolean
    val supportsPerZone: Boolean

    val hardwareEffects: List<HardwareEffect>
        get() = emptyList()

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

    suspend fun applyHardwareEffect(
        effectId: String,
        color: RgbColor,
        brightness: Int,
        speed: Int,
        power: Boolean,
    ): Boolean = false

    fun invalidate()
}
