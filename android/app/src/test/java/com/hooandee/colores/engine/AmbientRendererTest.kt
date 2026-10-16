package com.hooandee.colores.engine

import com.hooandee.colores.ambient.AmbientCaptureStatus
import com.hooandee.colores.ambient.AmbientColorMath
import com.hooandee.colores.ambient.MutableAmbientFrameSource
import com.hooandee.colores.led.RgbColor
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
        assertEquals(source.state.value.colors.map(AmbientColorMath::ledIntensity), tick.colors)
    }

    @Test
    fun `keeps last frame when capture temporarily has no frames`() {
        val source = MutableAmbientFrameSource()
        val renderer = AmbientRenderer(2, 80, vividness = { 0 }, smoothing = { 0 }, state = { source.state.value })
        source.update(List(2) { RgbColor(50, 60, 70) }, AmbientCaptureStatus.CAPTURING, 1L)
        renderer.render(0.0)
        source.reset(AmbientCaptureStatus.NO_FRAMES)

        assertEquals(List(2) { AmbientColorMath.ledIntensity(RgbColor(50, 60, 70)) }, renderer.render(0.08).colors)
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

    @Test
    fun `a scene cut follows quickly until it settles even when the render wakes early`() {
        val source = MutableAmbientFrameSource()
        val renderer = AmbientRenderer(1, 80, vividness = { 0 }, smoothing = { 45 }, state = { source.state.value })
        source.update(listOf(RgbColor(255, 0, 0)), AmbientCaptureStatus.CAPTURING, 1L)
        renderer.render(0.0)
        source.update(listOf(RgbColor(0, 0, 255)), AmbientCaptureStatus.CAPTURING, 2L)

        val first = renderer.render(0.01).colors.single()
        val second = renderer.render(0.09).colors.single()

        assertTrue("first=$first", first.blue >= AmbientColorMath.ledIntensity(RgbColor(0, 0, (0.85 * 255).toInt())).blue)
        assertTrue("second=$second", second.blue >= AmbientColorMath.ledIntensity(RgbColor(0, 0, (0.97 * 255).toInt())).blue && second.red <= 8)
    }

    @Test
    fun `small scene drift keeps the configured smoothing`() {
        val source = MutableAmbientFrameSource()
        val renderer = AmbientRenderer(1, 80, vividness = { 0 }, smoothing = { 45 }, state = { source.state.value })
        source.update(listOf(RgbColor(100, 100, 100)), AmbientCaptureStatus.CAPTURING, 1L)
        renderer.render(0.0)
        source.update(listOf(RgbColor(140, 100, 100)), AmbientCaptureStatus.CAPTURING, 2L)

        val next = renderer.render(0.08).colors.single()

        val screenRed = (100 + 40 * (1 - Math.pow(0.45, 0.8))).toInt()
        assertEquals(AmbientColorMath.ledIntensity(RgbColor(screenRed, 100, 100)).red.toDouble(), next.red.toDouble(), 1.0)
    }

    @Test
    fun `only the start of a scene cut wakes the render early`() =
        runBlocking {
            val source = MutableAmbientFrameSource()
            val renderer =
                AmbientRenderer(1, 80, vividness = { 0 }, smoothing = { 45 }, state = { source.state.value }, frames = source.state)
            source.update(listOf(RgbColor(255, 0, 0)), AmbientCaptureStatus.CAPTURING, 1L)
            renderer.render(0.0)
            val wake = requireNotNull(renderer.wake)

            assertNull(withTimeoutOrNull(50) { wake.first() })

            source.update(listOf(RgbColor(0, 0, 255)), AmbientCaptureStatus.CAPTURING, 2L)
            assertNotNull(withTimeoutOrNull(50) { wake.first() })

            renderer.render(0.01)
            source.update(listOf(RgbColor(0, 255, 0)), AmbientCaptureStatus.CAPTURING, 3L)
            assertNull(withTimeoutOrNull(50) { wake.first() })
        }
}
