package com.hooandee.colores.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class AudioSensitivityTest {
    @Test
    fun `normal sensitivity preserves the captured level`() {
        assertEquals(0.42, AudioSensitivity.adjust(0.42, AudioSensitivity.NORMAL_DB), 0.0001)
    }

    @Test
    fun `sensitivity shifts the forty decibel meter domain`() {
        assertEquals(0.72, AudioSensitivity.adjust(0.42, 12), 0.0001)
        assertEquals(0.12, AudioSensitivity.adjust(0.42, -12), 0.0001)
    }

    @Test
    fun `sensitivity clamps gain and output`() {
        assertEquals(1.0, AudioSensitivity.adjust(0.9, 100), 0.0001)
        assertEquals(0.0, AudioSensitivity.adjust(0.1, -100), 0.0001)
        assertEquals(1.0, AudioSensitivity.adjust(2.0, 0), 0.0001)
    }
}
