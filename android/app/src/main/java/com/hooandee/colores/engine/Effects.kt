package com.hooandee.colores.engine

import com.hooandee.colores.engine.FrameMath.breatheFactor
import com.hooandee.colores.engine.FrameMath.clamp8
import com.hooandee.colores.engine.FrameMath.freq
import com.hooandee.colores.engine.FrameMath.hash01
import com.hooandee.colores.engine.FrameMath.hsvToRgb
import com.hooandee.colores.engine.FrameMath.lerp
import com.hooandee.colores.engine.FrameMath.sampleStops
import com.hooandee.colores.engine.FrameMath.scale
import com.hooandee.colores.gradient.GradientInterpolator
import com.hooandee.colores.led.RgbColor
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

object Effects {
    fun frame(
        effectId: String,
        timeSeconds: Double,
        speed: Int,
        zones: Int,
        base: List<RgbColor>,
        stops: List<RgbColor>,
    ): List<RgbColor> =
        when (effectId) {
            "breathing" -> breathing(base, timeSeconds, speed)
            "rainbow" -> rainbow(zones, timeSeconds, speed)
            "wave" -> wave(stops, zones, timeSeconds, speed)
            "spiral" -> spiral(GradientInterpolator.interpolate(stops, zones), timeSeconds, speed)
            "cycle" -> cycle(zones, timeSeconds, speed)
            "comet" -> comet(base, timeSeconds, speed)
            "sparkle" -> sparkle(base, timeSeconds, speed)
            "ripple" -> ripple(base, timeSeconds, speed)
            "aurora" -> aurora(zones, timeSeconds, speed)
            else -> List(zones.coerceAtLeast(0)) { RgbColor(0, 0, 0) }
        }

    fun breathing(
        base: List<RgbColor>,
        t: Double,
        speed: Int,
    ): List<RgbColor> {
        val factor = breatheFactor(t, speed)
        return base.map { scale(it, factor) }
    }

    fun rainbow(
        zones: Int,
        t: Double,
        speed: Int,
    ): List<RgbColor> {
        if (zones <= 0) return emptyList()
        val hue = (60.0 * freq(speed) * t) % 360.0
        val color = hsvToRgb(hue, 1.0, 1.0)
        return List(zones) { color }
    }

    fun wave(
        stops: List<RgbColor>,
        zones: Int,
        t: Double,
        speed: Int,
    ): List<RgbColor> {
        if (zones <= 0) return emptyList()
        val offset = (freq(speed) * t) % 1.0
        return List(zones) { i ->
            sampleStops(stops, ((i.toDouble() / zones) + offset) % 1.0)
        }
    }

    fun cycle(
        zones: Int,
        t: Double,
        speed: Int,
    ): List<RgbColor> {
        if (zones <= 0) return emptyList()
        val base = (60.0 * freq(speed) * t) % 360.0
        return List(zones) { i -> hsvToRgb((base + (360.0 * i / zones)) % 360.0, 1.0, 1.0) }
    }

    fun spiral(
        palette: List<RgbColor>,
        t: Double,
        speed: Int,
    ): List<RgbColor> {
        val zones = palette.size
        if (zones == 0) return emptyList()
        val shift = ((freq(speed) * t) % 1.0) * zones
        return List(zones) { i ->
            val src = (i + shift) % zones
            val lo = floor(src).toInt().mod(zones)
            val hi = (lo + 1) % zones
            val frac = src - floor(src)
            val a = palette[lo]
            val b = palette[hi]
            RgbColor(
                clamp8(lerp(a.red, b.red, frac)),
                clamp8(lerp(a.green, b.green, frac)),
                clamp8(lerp(a.blue, b.blue, frac)),
            )
        }
    }

    fun comet(
        base: List<RgbColor>,
        t: Double,
        speed: Int,
    ): List<RgbColor> {
        val zones = base.size
        if (zones <= 0) return emptyList()
        val span = if (zones > 1) zones - 1 else 1
        val phase = (freq(speed) * t) % 2.0
        val pos = if (phase <= 1.0) phase * span else (2.0 - phase) * span
        val tail = max(1.5, zones * 0.18)
        return List(zones) { i ->
            var b = max(0.0, 1.0 - abs(i - pos) / tail)
            b *= b
            scale(base[i], b)
        }
    }

    fun sparkle(
        base: List<RgbColor>,
        t: Double,
        speed: Int,
    ): List<RgbColor> {
        val zones = base.size
        if (zones <= 0) return emptyList()
        val f = freq(speed)
        val floorLevel = 0.10
        return List(zones) { i ->
            val ph = (f * 0.8 * t + hash01(i.toDouble(), 1.0)) % 1.0
            val tw = max(0.0, 1.0 - abs(ph - 0.5) * 2.0).pow(3)
            scale(base[i], floorLevel + (1.0 - floorLevel) * tw)
        }
    }

    fun ripple(
        base: List<RgbColor>,
        t: Double,
        speed: Int,
    ): List<RgbColor> {
        val zones = base.size
        if (zones <= 0) return emptyList()
        val f = freq(speed)
        val waves = 2.0
        return List(zones) { i ->
            val phase = 2 * PI * (f * t - (i.toDouble() / zones) * waves)
            val b = 0.35 + 0.65 * (0.5 + 0.5 * sin(phase))
            scale(base[i], b)
        }
    }

    fun aurora(
        zones: Int,
        t: Double,
        speed: Int,
    ): List<RgbColor> {
        if (zones <= 0) return emptyList()
        val f = freq(speed)
        return List(zones) { i ->
            val h = 150.0 + 80.0 * sin(2 * PI * (f * 0.25 * t + i.toDouble() / zones)) +
                40.0 * sin(2 * PI * (f * 0.15 * t + i * 2.0 / zones))
            val v = 0.6 + 0.4 * (0.5 + 0.5 * sin(2 * PI * (f * 0.3 * t + i.toDouble() / zones * 1.5)))
            hsvToRgb(h % 360.0, 0.85, v)
        }
    }

    private val METER_RAMP =
        listOf(RgbColor(0, 230, 90), RgbColor(255, 200, 0), RgbColor(255, 40, 0))

    fun meter(
        value01: Double,
        zones: Int,
    ): List<RgbColor> {
        if (zones <= 0) return emptyList()
        val lit = value01.coerceIn(0.0, 1.0) * zones
        return List(zones) { i ->
            val fill = (lit - i).coerceIn(0.0, 1.0)
            val pos = if (zones > 1) i.toDouble() / (zones - 1) else 0.0
            scale(sampleStops(METER_RAMP, pos), fill)
        }
    }

    private val CLOCK_KEYS =
        listOf(
            0.0 to RgbColor(12, 22, 64),
            6.0 to RgbColor(255, 120, 40),
            9.0 to RgbColor(170, 205, 255),
            14.0 to RgbColor(255, 248, 230),
            18.0 to RgbColor(255, 105, 40),
            21.0 to RgbColor(40, 28, 92),
            24.0 to RgbColor(12, 22, 64),
        )

    fun clockColor(hour: Double): RgbColor {
        val h = ((hour % 24.0) + 24.0) % 24.0
        for (index in 0 until CLOCK_KEYS.lastIndex) {
            val (h0, c0) = CLOCK_KEYS[index]
            val (h1, c1) = CLOCK_KEYS[index + 1]
            if (h in h0..h1) {
                val f = if (h1 > h0) (h - h0) / (h1 - h0) else 0.0
                return RgbColor(
                    clamp8(c0.red + (c1.red - c0.red) * f),
                    clamp8(c0.green + (c1.green - c0.green) * f),
                    clamp8(c0.blue + (c1.blue - c0.blue) * f),
                )
            }
        }
        return CLOCK_KEYS.first().second
    }

    fun bandColor(
        value: Double,
        bands: List<SensorBand>,
    ): RgbColor {
        for (band in bands) {
            if (value >= band.min) return band.color
        }
        return bands.last().color
    }
}

data class SensorBand(
    val min: Double,
    val color: RgbColor,
)
