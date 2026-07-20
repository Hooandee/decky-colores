package com.hooandee.colores.led

import android.content.Context

data class SettingsProviderDescriptor(
    val colorKey: String,
    val colorFormat: String,
    val brightnessKey: String,
    val brightnessRange: ClosedFloatingPointRange<Float>,
    val enableKeys: List<String>,
    val zones: Int,
    val vendorService: String,
)

class SettingsProviderLedDevice(
    private val context: Context,
    private val descriptor: SettingsProviderDescriptor,
) : LedDevice {
    override val available: Boolean
        get() = TODO("Check WRITE_SETTINGS and the descriptor vendor service")

    override val supportsPerZone: Boolean
        get() = TODO("Read the zone count from the device descriptor")

    override suspend fun applyZones(
        colors: List<RgbColor>,
        brightness: Int,
        power: Boolean,
    ): Boolean = TODO("Write descriptor keys through Settings.System")

    override suspend fun applySolid(
        color: RgbColor,
        brightness: Int,
        power: Boolean,
    ): Boolean = TODO("Expand the solid color and write it through Settings.System")

    override fun invalidate() {
        TODO("Drop cached Settings.System values")
    }
}
