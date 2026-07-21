package com.hooandee.colores.gradient

import com.hooandee.colores.led.RgbColor
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GradientResumePolicyTest {
    @Test
    fun `active writable persisted gradient is reapplied`() {
        assertTrue(
            GradientResumePolicy.shouldReapply(
                mode = LightingMode.GRADIENT,
                stops = listOf(RgbColor(255, 0, 0), RgbColor(0, 0, 255)),
                gradientAvailable = true,
                canWrite = true,
            ),
        )
    }

    @Test
    fun `solid mode is never reapplied as a gradient`() {
        assertFalse(
            GradientResumePolicy.shouldReapply(
                mode = LightingMode.COLOR,
                stops = listOf(RgbColor(255, 0, 0)),
                gradientAvailable = true,
                canWrite = true,
            ),
        )
    }

    @Test
    fun `missing stops capability or control prevents recovery`() {
        assertFalse(GradientResumePolicy.shouldReapply(LightingMode.GRADIENT, emptyList(), true, true))
        assertFalse(GradientResumePolicy.shouldReapply(LightingMode.GRADIENT, listOf(RgbColor(1, 2, 3)), false, true))
        assertFalse(GradientResumePolicy.shouldReapply(LightingMode.GRADIENT, listOf(RgbColor(1, 2, 3)), true, false))
    }
}
