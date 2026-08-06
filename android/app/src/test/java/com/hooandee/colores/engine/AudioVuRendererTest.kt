package com.hooandee.colores.engine

import com.hooandee.colores.audio.AudioCaptureStatus
import com.hooandee.colores.audio.MutableAudioLevelSource
import com.hooandee.colores.led.RgbColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioVuRendererTest {
    @Test
    fun `renders black while capture authorization is missing`() {
        val source = MutableAudioLevelSource()
        val renderer = AudioVuRenderer(8, 80) { source.state.value }

        val tick = renderer.render(0.0)

        assertEquals(80L, tick.nextDelayMs)
        assertEquals(List(8) { RgbColor(0, 0, 0) }, tick.colors)
    }

    @Test
    fun `renders the latest captured level without accumulating frames`() {
        val source = MutableAudioLevelSource()
        val renderer = AudioVuRenderer(8, 80) { source.state.value }
        source.update(0.1, AudioCaptureStatus.CAPTURING)

        val tick = renderer.render(1.0)

        assertTrue(tick.colors[3].green > 0)
        assertTrue(tick.colors[4].green > 0)
        assertEquals(RgbColor(0, 0, 0), tick.colors.first())
    }

    @Test
    fun `renders with the live custom audio scale`() {
        val source = MutableAudioLevelSource()
        val blue = RgbColor(0, 80, 255)
        val scale = AudioScale(blue, blue, blue, mediumAt = 40, peakAt = 80)
        val renderer = AudioVuRenderer(8, 80, scale = { scale }, state = { source.state.value })
        source.update(0.1, AudioCaptureStatus.CAPTURING)

        val tick = renderer.render(1.0)

        assertEquals(RgbColor(0, 32, 102), tick.colors[3])
        assertEquals(RgbColor(0, 32, 102), tick.colors[4])
    }

    @Test
    fun `sensitivity shifts the next frame without changing captured pcm`() {
        val quietSource = MutableAudioLevelSource().apply { update(0.1, AudioCaptureStatus.CAPTURING) }
        val referenceSource = MutableAudioLevelSource().apply { update(0.4, AudioCaptureStatus.CAPTURING) }
        val sensitive =
            AudioVuRenderer(
                zones = 8,
                frameIntervalMs = 80,
                sensitivityDb = { 12 },
                state = { quietSource.state.value },
            )
        val reference = AudioVuRenderer(8, 80) { referenceSource.state.value }

        assertEquals(reference.render(0.0).colors, sensitive.render(0.0).colors)
        assertEquals(0.1, quietSource.state.value.level, 0.0)
    }
}
