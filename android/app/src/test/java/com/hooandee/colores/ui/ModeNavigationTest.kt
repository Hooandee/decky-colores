package com.hooandee.colores.ui

import com.hooandee.colores.control.AppMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ModeNavigationTest {
    private val sensorModes = listOf(AppMode.BATTERY, AppMode.TEMPERATURE, AppMode.PERFORMANCE)

    @Test
    fun `sensor button returns to the last sensor tab`() {
        assertEquals(AppMode.TEMPERATURE, navTarget(AppMode.BATTERY, AppMode.TEMPERATURE, sensorModes))
        assertEquals(AppMode.PERFORMANCE, navTarget(AppMode.BATTERY, AppMode.PERFORMANCE, sensorModes))
    }

    @Test
    fun `sensor button falls back to battery when the last sensor is unavailable`() {
        assertEquals(AppMode.BATTERY, navTarget(AppMode.BATTERY, AppMode.PERFORMANCE, listOf(AppMode.BATTERY)))
    }

    @Test
    fun `non sensor buttons keep their own mode`() {
        assertEquals(AppMode.CLOCK, navTarget(AppMode.CLOCK, AppMode.TEMPERATURE, sensorModes))
    }

    @Test
    fun `entering sensors honors the requested sensor`() {
        val state = ColoresUiState(mode = AppMode.CLOCK, temperatureAvailable = true)

        assertEquals(AppMode.TEMPERATURE, state.appModeTarget(AppMode.TEMPERATURE))
    }

    @Test
    fun `entering an unavailable sensor falls back to the first available one`() {
        val state = ColoresUiState(mode = AppMode.CLOCK, temperatureAvailable = false)

        assertEquals(AppMode.BATTERY, state.appModeTarget(AppMode.TEMPERATURE))
    }

    @Test
    fun `sensor button does nothing while a sensor is already shown`() {
        val state = ColoresUiState(mode = AppMode.TEMPERATURE, temperatureAvailable = true)

        assertNull(state.appModeTarget(AppMode.BATTERY))
    }
}
