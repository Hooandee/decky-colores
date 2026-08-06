package com.hooandee.colores.ui

import com.hooandee.colores.engine.AudioScale
import com.hooandee.colores.led.RgbColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioWaveformModelTest {
    @Test
    fun `each bar uses the color of its actual audio level`() {
        val green = RgbColor(0, 230, 90)
        val yellow = RgbColor(255, 200, 0)
        val blue = RgbColor(30, 90, 255)
        val scale = AudioScale(green, yellow, blue)

        assertEquals(green, audioBarColor(AudioBarPoint(0f, 0.2f), scale, active = true))
        assertEquals(yellow, audioBarColor(AudioBarPoint(0.5f, 0.7f), scale, active = true))
        assertEquals(blue, audioBarColor(AudioBarPoint(1f, 1f), scale, active = true))
    }

    @Test
    fun `inactive bars are neutral instead of implying a low audio level`() {
        val color = audioBarColor(AudioBarPoint(0f, 0f), AudioScale.DEFAULT, active = false)

        assertNotEquals(AudioScale.DEFAULT.lowColor, color)
    }

    @Test
    fun `recent bars are brighter than old bars`() {
        assertEquals(0.30f, audioBarAgeAlpha(index = 0, count = 24))
        assertEquals(1f, audioBarAgeAlpha(index = 23, count = 24))
        assertTrue(audioBarAgeAlpha(index = 12, count = 24) > audioBarAgeAlpha(index = 11, count = 24))
    }

    @Test
    fun `adaptive contrast lifts changes above a steady musical background`() {
        val levels = listOf(0.55f, 0.55f, 0.56f, 0.65f, 0.56f, 0.55f)

        val visual = adaptiveAudioBarLevels(levels)

        assertTrue(visual[3] - visual[0] > (levels[3] - levels[0]) * 2f)
        assertTrue(visual[0] > 0f)
        assertTrue(visual.all { it in 0f..1f })
    }

    @Test
    fun `a new peak never rescales bars already stored in the history`() {
        val tracker = AdaptiveAudioBarTracker()
        val history = mutableListOf<AudioBarPoint>()
        history.appendTrackedAudioBar(0.55f, tracker)
        history.appendTrackedAudioBar(0.65f, tracker)
        val previousBars = history.toList()

        history.appendTrackedAudioBar(0.9f, tracker)

        assertEquals(previousBars, history.take(previousBars.size))
    }

    @Test
    fun `history keeps the latest twenty four immutable audio bars`() {
        val tracker = AdaptiveAudioBarTracker()
        val history = mutableListOf<AudioBarPoint>()

        repeat(30) { index ->
            history.appendTrackedAudioBar(index / 100f, tracker)
        }

        assertEquals(24, history.size)
        assertEquals(0.06f, history.first().level)
        assertEquals(0.29f, history.last().level)
    }
}
