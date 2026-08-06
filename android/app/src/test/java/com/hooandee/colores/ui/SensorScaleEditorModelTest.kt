package com.hooandee.colores.ui

import com.hooandee.colores.engine.BandSet
import com.hooandee.colores.engine.SensorKind
import com.hooandee.colores.led.RgbColor
import org.junit.Assert.assertEquals
import org.junit.Test

class SensorScaleEditorModelTest {
    @Test
    fun `threshold edits stay strictly between adjacent bands`() {
        val model = SensorScaleEditorModel.create(SensorKind.BATTERY, BandSet.FALLBACK.battery).select(2)

        val raised = model.updateThreshold(90)
        val lowered = model.updateThreshold(0)

        assertEquals(60.0, raised.bands[2].min, 0.0)
        assertEquals(22.0, lowered.bands[2].min, 0.0)
        assertEquals(0.0, lowered.bands.last().min, 0.0)
    }

    @Test
    fun `color edits affect only the selected band`() {
        val model = SensorScaleEditorModel.create(SensorKind.TEMPERATURE, BandSet.FALLBACK.temperature).select(1)
        val color = RgbColor(12, 34, 56)

        val changed = model.updateColor(color)

        assertEquals(color, changed.bands[1].color)
        assertEquals(BandSet.FALLBACK.temperature[0], changed.bands[0])
        assertEquals(BandSet.FALLBACK.temperature[2], changed.bands[2])
    }
}
