package com.hooandee.colores.gradient

import com.hooandee.colores.led.LedDevice
import com.hooandee.colores.led.LedState
import com.hooandee.colores.led.RgbColor

class GradientApplier(
    private val device: LedDevice,
) {
    suspend fun apply(
        stops: List<RgbColor>,
        zones: Int,
        current: LedState,
    ): Boolean =
        device.applyZones(
            colors = GradientInterpolator.interpolate(stops, zones),
            brightness = current.brightness,
            power = current.power,
        )
}
