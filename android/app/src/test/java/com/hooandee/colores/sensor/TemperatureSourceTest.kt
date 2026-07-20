package com.hooandee.colores.sensor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TemperatureSourceTest {
    @Test
    fun `prefers a cpu-like zone and normalizes millidegrees`() {
        val source =
            SysfsThermalSource {
                listOf(
                    ThermalZoneRaw("battery", "31000"),
                    ThermalZoneRaw("cpu-0-0-usr", "58000"),
                )
            }
        assertTrue(source.available)
        assertEquals(58.0, source.readCelsius()!!, 0.001)
    }

    @Test
    fun `falls back to the hottest plausible zone when no cpu zone exists`() {
        val source =
            SysfsThermalSource {
                listOf(
                    ThermalZoneRaw("pmic", "40"),
                    ThermalZoneRaw("skin", "47"),
                )
            }
        assertEquals(47.0, source.readCelsius()!!, 0.001)
    }

    @Test
    fun `is unavailable when nothing reads a plausible value`() {
        assertFalse(SysfsThermalSource { emptyList() }.available)
        assertNull(SysfsThermalSource { listOf(ThermalZoneRaw("x", null)) }.readCelsius())
        assertNull(SysfsThermalSource { listOf(ThermalZoneRaw("x", "900000")) }.readCelsius())
    }
}
