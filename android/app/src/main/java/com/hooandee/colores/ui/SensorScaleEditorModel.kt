package com.hooandee.colores.ui

import com.hooandee.colores.engine.SensorBand
import com.hooandee.colores.engine.SensorKind
import com.hooandee.colores.led.RgbColor

data class SensorScaleEditorModel(
    val kind: SensorKind,
    val bands: List<SensorBand>,
    val selectedIndex: Int,
) {
    val selectedBand: SensorBand
        get() = bands[selectedIndex]

    val thresholdRange: IntRange?
        get() {
            if (selectedIndex == bands.lastIndex) return null
            val upper = if (selectedIndex == 0) kind.maximum else bands[selectedIndex - 1].min.toInt() - 1
            val lower = bands[selectedIndex + 1].min.toInt() + 1
            return lower..upper
        }

    fun select(index: Int): SensorScaleEditorModel = copy(selectedIndex = index.coerceIn(bands.indices))

    fun updateThreshold(value: Int): SensorScaleEditorModel {
        val range = thresholdRange ?: return this
        return replaceSelected(selectedBand.copy(min = value.coerceIn(range).toDouble()))
    }

    fun updateColor(color: RgbColor): SensorScaleEditorModel = replaceSelected(selectedBand.copy(color = color))

    private fun replaceSelected(band: SensorBand): SensorScaleEditorModel =
        copy(bands = bands.toMutableList().also { it[selectedIndex] = band })

    companion object {
        fun create(
            kind: SensorKind,
            bands: List<SensorBand>,
        ): SensorScaleEditorModel = SensorScaleEditorModel(kind, bands, selectedIndex = 0)
    }
}
