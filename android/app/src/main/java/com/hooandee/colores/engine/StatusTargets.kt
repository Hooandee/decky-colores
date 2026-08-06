package com.hooandee.colores.engine

import com.hooandee.colores.led.RgbColor

object StatusTargets {
    fun batteryTarget(
        levelPercent: Int?,
        bands: List<SensorBand>,
    ): RgbColor? = levelPercent?.let { Effects.bandColor(it.toDouble(), bands) }

    fun batteryBreathing(
        charging: Boolean,
        breatheEnabled: Boolean,
        levelPercent: Int?,
    ): Boolean = charging && breatheEnabled && levelPercent != null && levelPercent < 100

    fun temperatureTarget(
        celsius: Double?,
        bands: List<SensorBand>,
    ): RgbColor? = celsius?.let { Effects.bandColor(it, bands) }

    fun temperatureBreathing(
        breatheEnabled: Boolean,
        celsius: Double?,
        bands: List<SensorBand>,
    ): Boolean = breatheEnabled && celsius != null && bands.firstOrNull()?.let { celsius >= it.min } == true
}
