package com.hooandee.colores.engine

import com.hooandee.colores.led.RgbColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RendererTest {
    private val red = RgbColor(255, 0, 0)
    private val blue = RgbColor(0, 0, 255)

    @Test
    fun `effect renderer produces one colour per zone at its own cadence`() {
        val renderer =
            EffectRenderer(
                effectId = "rainbow",
                zones = 4,
                frameIntervalMs = 80,
                speed = { 50 },
                palette = { EffectPalette(base = List(4) { red }, stops = listOf(red, blue)) },
            )
        val tick = renderer.render(0.0)
        assertEquals(4, tick.colors.size)
        assertEquals(80, tick.nextDelayMs)
        assertEquals(List(4) { red }, tick.colors)
    }

    @Test
    fun `indicator renderer eases toward a changed target then settles on the idle tick`() {
        var target: RgbColor? = red
        val renderer =
            IndicatorRenderer(
                zones = 2,
                frameIntervalMs = 80,
                idleIntervalMs = 500,
                target = { target },
                breathing = { false },
            )
        val first = renderer.render(0.0)
        assertEquals(List(2) { red }, first.colors)
        assertEquals(500, first.nextDelayMs)

        target = blue
        val moving = renderer.render(0.1)
        assertEquals(80, moving.nextDelayMs)
        assertNotEquals(blue, moving.colors.first())
        assertTrue(moving.colors.first().blue in 1..254)

        repeat(200) { renderer.render(0.2 + it * 0.1) }
        val settled = renderer.render(40.0)
        assertEquals(List(2) { blue }, settled.colors)
        assertEquals(500, settled.nextDelayMs)
    }

    @Test
    fun `indicator renderer holds the last frame when the target is unreadable`() {
        var target: RgbColor? = red
        val renderer =
            IndicatorRenderer(
                zones = 3,
                frameIntervalMs = 80,
                idleIntervalMs = 500,
                target = { target },
                breathing = { false },
            )
        renderer.render(0.0)
        target = null
        val held = renderer.render(1.0)
        assertEquals(List(3) { red }, held.colors)
        assertEquals(500, held.nextDelayMs)
    }

    @Test
    fun `performance renderer smooths toward the live value`() {
        var value: Double? = 100.0
        val renderer = PerformanceRenderer(zones = 4, frameIntervalMs = 80, idleIntervalMs = 500, value = { value })
        val first = renderer.render(0.0)
        assertTrue("meter should start climbing", first.colors.any { it != RgbColor(0, 0, 0) })

        value = 0.0
        repeat(300) { renderer.render(1.0 + it * 0.08) }
        val settled = renderer.render(60.0)
        assertEquals(500, settled.nextDelayMs)
        assertEquals(List(4) { RgbColor(0, 0, 0) }, settled.colors)
    }

    @Test
    fun `clock renderer updates on a coarse interval`() {
        val renderer = ClockRenderer(zones = 2, intervalMs = 30_000, hour = { 12.0 })
        val tick = renderer.render(0.0)
        assertEquals(30_000, tick.nextDelayMs)
        assertEquals(List(2) { Effects.clockColor(12.0) }, tick.colors)
    }
}
