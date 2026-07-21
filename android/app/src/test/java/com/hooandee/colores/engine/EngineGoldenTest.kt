package com.hooandee.colores.engine

import com.hooandee.colores.led.RgbColor
import java.io.File
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EngineGoldenTest {
    @Test
    fun `matches every shared effect_frame golden vector`() {
        val vectors = JSONArray(File("../../shared/golden/effect-frame.json").readText())
        assertTrue(vectors.length() >= 8)
        repeat(vectors.length()) { index ->
            val vector = vectors.getJSONObject(index)
            assertEquals("effect_frame", vector.getString("operation"))
            val input = vector.getJSONObject("input")
            val expected = vector.getJSONObject("expected").getJSONArray("colors").colors()

            val actual =
                Effects.frame(
                    effectId = input.getString("effectId"),
                    timeSeconds = input.getDouble("timeSeconds"),
                    speed = input.getInt("speed"),
                    zones = input.getInt("zones"),
                    base = input.optJSONArray("base")?.colors().orEmpty(),
                    stops = input.optJSONArray("stops")?.colors().orEmpty(),
                )

            assertEquals("vector ${vector.getString("id")}", expected, actual)
        }
    }

    @Test
    fun `matches every shared clock_color golden vector`() {
        val vectors = JSONArray(File("../../shared/golden/clock.json").readText())
        assertTrue(vectors.length() > 0)
        repeat(vectors.length()) { index ->
            val vector = vectors.getJSONObject(index)
            assertEquals("clock_color", vector.getString("operation"))
            val hour = vector.getJSONObject("input").getDouble("hour")
            val expected = vector.getJSONObject("expected").getJSONObject("color").toColor()

            assertEquals("vector ${vector.getString("id")}", expected, Effects.clockColor(hour))
        }
    }

    @Test
    fun `matches every shared performance_meter golden vector`() {
        val vectors = JSONArray(File("../../shared/golden/meter.json").readText())
        assertTrue(vectors.length() > 0)
        repeat(vectors.length()) { index ->
            val vector = vectors.getJSONObject(index)
            assertEquals("performance_meter", vector.getString("operation"))
            val input = vector.getJSONObject("input")
            val expected = vector.getJSONObject("expected").getJSONArray("colors").colors()

            assertEquals(
                "vector ${vector.getString("id")}",
                expected,
                Effects.meter(input.getDouble("value"), input.getInt("zones")),
            )
        }
    }

    @Test
    fun `every effect keeps channels within range across a time sweep`() {
        val base = List(8) { RgbColor(200, 100, 50) }
        val stops = listOf(RgbColor(255, 0, 0), RgbColor(0, 255, 0), RgbColor(0, 0, 255))
        val effects = listOf("breathing", "rainbow", "wave", "spiral", "cycle", "comet", "sparkle", "ripple", "aurora")
        for (effect in effects) {
            for (speed in listOf(0, 25, 50, 75, 100)) {
                var step = 0
                while (step <= 200) {
                    val t = step * 0.05
                    val frame = Effects.frame(effect, t, speed, 8, base, stops)
                    assertEquals("$effect produced wrong zone count", 8, frame.size)
                    frame.forEach { color ->
                        assertTrue("$effect out of range", color.red in 0..255 && color.green in 0..255 && color.blue in 0..255)
                    }
                    step++
                }
            }
        }
    }

    @Test
    fun `zero speed still advances deterministically without pinning to a single value`() {
        val stops = listOf(RgbColor(255, 0, 0), RgbColor(0, 0, 255))
        val early = Effects.wave(stops, 4, 0.0, 0)
        val later = Effects.wave(stops, 4, 3.0, 0)
        assertTrue(early != later)
    }

    @Test
    fun `band color scans thresholds high to low`() {
        val bands =
            listOf(
                SensorBand(81.0, RgbColor(0, 120, 255)),
                SensorBand(61.0, RgbColor(0, 200, 60)),
                SensorBand(0.0, RgbColor(255, 30, 20)),
            )
        assertEquals(RgbColor(0, 120, 255), Effects.bandColor(90.0, bands))
        assertEquals(RgbColor(0, 200, 60), Effects.bandColor(61.0, bands))
        assertEquals(RgbColor(255, 30, 20), Effects.bandColor(0.0, bands))
    }
}

private fun JSONArray.colors(): List<RgbColor> =
    (0 until length()).map { index -> getJSONObject(index).toColor() }

private fun JSONObject.toColor(): RgbColor = RgbColor(getInt("r"), getInt("g"), getInt("b"))
