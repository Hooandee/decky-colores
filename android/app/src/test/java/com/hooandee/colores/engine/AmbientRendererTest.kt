package com.hooandee.colores.engine

import com.hooandee.colores.ambient.AmbientCaptureStatus
import com.hooandee.colores.ambient.MutableAmbientFrameSource
import com.hooandee.colores.led.RgbColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AmbientRendererTest {
    @Test
    fun `renders latest sampled frame at device cadence`() {
        val source = MutableAmbientFrameSource()
        source.update(listOf(RgbColor(200, 20, 10), RgbColor(10, 20, 200)), AmbientCaptureStatus.CAPTURING, 1L)
        val renderer = AmbientRenderer(2, 80, vividness = { 0 }, smoothing = { 0 }, state = { source.state.value })

        val tick = renderer.render(0.0)

        assertEquals(80L, tick.nextDelayMs)
        assertEquals(source.state.value.colors, tick.colors)
    }

    @Test
    fun `keeps last frame when capture temporarily has no frames`() {
        val source = MutableAmbientFrameSource()
        val renderer = AmbientRenderer(2, 80, vividness = { 0 }, smoothing = { 0 }, state = { source.state.value })
        source.update(List(2) { RgbColor(50, 60, 70) }, AmbientCaptureStatus.CAPTURING, 1L)
        renderer.render(0.0)
        source.reset(AmbientCaptureStatus.NO_FRAMES)

        assertEquals(List(2) { RgbColor(50, 60, 70) }, renderer.render(0.08).colors)
    }

    @Test
    fun `smooths from the displayed frame using elapsed time`() {
        val source = MutableAmbientFrameSource()
        val renderer = AmbientRenderer(1, 80, vividness = { 0 }, smoothing = { 80 }, state = { source.state.value })
        source.update(listOf(RgbColor(0, 0, 0)), AmbientCaptureStatus.CAPTURING, 1L)
        renderer.render(0.0)
        source.update(listOf(RgbColor(255, 0, 0)), AmbientCaptureStatus.CAPTURING, 2L)

        val first = renderer.render(0.08).colors.single().red
        val second = renderer.render(0.16).colors.single().red

        assertTrue(first in 1..254)
        assertTrue(second > first)
    }
}
