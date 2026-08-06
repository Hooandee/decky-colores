package com.hooandee.colores.ui

import com.hooandee.colores.engine.AudioScale
import com.hooandee.colores.led.RgbColor
import org.junit.Assert.assertEquals
import org.junit.Test

class AudioScaleEditorModelTest {
    @Test
    fun `medium and peak thresholds cannot cross`() {
        val medium = AudioScaleEditorModel(AudioScale.DEFAULT, AudioScaleStop.MEDIUM)
        val movedMedium = medium.updateThreshold(100)

        assertEquals(99, movedMedium.scale.mediumAt)
        assertEquals(100, movedMedium.scale.peakAt)

        val peak = movedMedium.select(AudioScaleStop.PEAK).updateThreshold(1)

        assertEquals(99, peak.scale.mediumAt)
        assertEquals(100, peak.scale.peakAt)
    }

    @Test
    fun `color editing changes only the selected scale stop`() {
        val custom = RgbColor(12, 34, 56)
        val changed = AudioScaleEditorModel(AudioScale.DEFAULT, AudioScaleStop.MEDIUM).updateColor(custom)

        assertEquals(AudioScale.DEFAULT.lowColor, changed.scale.lowColor)
        assertEquals(custom, changed.scale.mediumColor)
        assertEquals(AudioScale.DEFAULT.peakColor, changed.scale.peakColor)
    }

    @Test
    fun `reset restores classic VU and keeps the selected stop`() {
        val edited =
            AudioScaleEditorModel(AudioScale.DEFAULT, AudioScaleStop.PEAK)
                .updateColor(RgbColor(1, 2, 3))
                .updateThreshold(70)

        assertEquals(AudioScale.DEFAULT, edited.reset().scale)
        assertEquals(AudioScaleStop.PEAK, edited.reset().selected)
    }
}
