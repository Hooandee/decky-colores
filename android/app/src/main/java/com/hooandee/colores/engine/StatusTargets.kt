package com.hooandee.colores.engine

import com.hooandee.colores.led.RgbColor

/**
 * Pure decision helpers for the status-indicator modes, mirroring the closures in
 * py_modules/effects.py `start_battery` / `start_temperature`. A null target means
 * the value is unreadable so the renderer holds its last frame.
 */
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
    ): Boolean = breatheEnabled && celsius != null && celsius >= TEMPERATURE_CRITICAL
}
