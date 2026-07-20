package com.hooandee.colores.gradient

import com.hooandee.colores.led.RgbColor
import kotlin.math.floor
import kotlin.math.round

data class Gradient(
    val stops: List<RgbColor>,
)

object GradientInterpolator {
    fun interpolate(
        stops: List<RgbColor>,
        zones: Int,
    ): List<RgbColor> {
        if (zones <= 0 || stops.isEmpty()) return emptyList()
        val safeStops = stops.map(RgbColor::clamped)
        if (safeStops.size == 1) return List(zones) { safeStops.first() }
        if (zones == 1) return listOf(safeStops.first())
        return List(zones) { index ->
            sample(safeStops, index.toDouble() / (zones - 1))
        }
    }

    private fun sample(
        stops: List<RgbColor>,
        position: Double,
    ): RgbColor {
        val scaled = position.coerceIn(0.0, 1.0) * (stops.size - 1)
        val lower = floor(scaled).toInt()
        if (lower >= stops.lastIndex) return stops.last()
        val fraction = scaled - lower
        val start = stops[lower]
        val end = stops[lower + 1]
        return RgbColor(
            red = interpolateChannel(start.red, end.red, fraction),
            green = interpolateChannel(start.green, end.green, fraction),
            blue = interpolateChannel(start.blue, end.blue, fraction),
        )
    }

    private fun interpolateChannel(
        start: Int,
        end: Int,
        fraction: Double,
    ): Int = round(start + (end - start) * fraction).toInt().coerceIn(0, 255)
}

private fun RgbColor.clamped() =
    RgbColor(
        red = red.coerceIn(0, 255),
        green = green.coerceIn(0, 255),
        blue = blue.coerceIn(0, 255),
    )
