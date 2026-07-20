package com.hooandee.colores.engine

import com.hooandee.colores.led.RgbColor
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.round
import kotlin.math.sin

internal object FrameMath {
    fun clamp8(value: Double): Int = round(value).toInt().coerceIn(0, 255)

    fun lerp(
        a: Double,
        b: Double,
        f: Double,
    ): Double = a + (b - a) * f

    fun lerp(
        a: Int,
        b: Int,
        f: Double,
    ): Double = a + (b - a) * f

    fun freq(speed: Int): Double = 0.1 + (speed.toDouble().coerceIn(0.0, 100.0) / 100.0) * 1.9

    fun breatheFactor(
        t: Double,
        speed: Int,
    ): Double = 0.575 + 0.425 * sin(2 * Math.PI * freq(speed) * t)

    fun hsvToRgb(
        hue: Double,
        saturation: Double,
        value: Double,
    ): RgbColor {
        val h = ((hue % 360.0) + 360.0) % 360.0
        val c = value * saturation
        val x = c * (1 - abs((h / 60.0) % 2 - 1))
        val m = value - c
        val (r, g, b) =
            when {
                h < 60 -> Triple(c, x, 0.0)
                h < 120 -> Triple(x, c, 0.0)
                h < 180 -> Triple(0.0, c, x)
                h < 240 -> Triple(0.0, x, c)
                h < 300 -> Triple(x, 0.0, c)
                else -> Triple(c, 0.0, x)
            }
        return RgbColor(clamp8((r + m) * 255), clamp8((g + m) * 255), clamp8((b + m) * 255))
    }

    fun sampleStops(
        stops: List<RgbColor>,
        position: Double,
    ): RgbColor {
        if (stops.size == 1) return stops.first()
        val pos = position.coerceIn(0.0, 1.0)
        val scaled = pos * (stops.size - 1)
        val index = floor(scaled).toInt()
        if (index >= stops.size - 1) return stops.last()
        val f = scaled - index
        val a = stops[index]
        val b = stops[index + 1]
        return RgbColor(
            clamp8(lerp(a.red, b.red, f)),
            clamp8(lerp(a.green, b.green, f)),
            clamp8(lerp(a.blue, b.blue, f)),
        )
    }

    fun hash01(
        a: Double,
        b: Double,
    ): Double {
        val x = sin(a * 127.1 + b * 311.7) * 43758.5453
        return x - floor(x)
    }

    fun scale(
        color: RgbColor,
        factor: Double,
    ): RgbColor = RgbColor(clamp8(color.red * factor), clamp8(color.green * factor), clamp8(color.blue * factor))
}
