package com.hooandee.colores.control

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import com.hooandee.colores.led.LedDevice
import com.hooandee.colores.led.LedState
import com.hooandee.colores.led.RgbColor
import kotlinx.coroutines.test.runTest
import org.junit.Test

class LightingRuntimeRestoreTest {
    @Test
    fun `successful restore attaches profiles to the same device binding`() {
        val restored = RestoredLightingBinding("ayn-thor", zones = 8, gradientSupported = true)
        var attached: RestoredLightingBinding? = null

        assertTrue(attachProfileRuntime(restored) { attached = it })
        assertEquals(restored, attached)
    }

    @Test
    fun `failed restore does not attach profiles`() {
        var attached = false

        assertFalse(attachProfileRuntime(null) { attached = true })
        assertFalse(attached)
    }

    @Test
    fun `a device that is not bound is closed and the bound one is kept`() =
        runTest {
            val bound = ClosingDevice()
            val spare = ClosingDevice()

            assertFalse(closeIfNotBound(bound, bound))
            assertTrue(closeIfNotBound(spare, bound))
            assertFalse(closeIfNotBound(null, bound))

            assertFalse(bound.closed)
            assertTrue(spare.closed)
        }

    private class ClosingDevice : LedDevice {
        var closed = false
        override val available = true
        override val supportsPerZone = true

        override fun invalidate() = Unit

        override suspend fun readState(): LedState = LedState(listOf(RgbColor(0, 0, 0)), 100, true)

        override suspend fun applyZones(
            colors: List<RgbColor>,
            brightness: Int,
            power: Boolean,
        ): Boolean = true

        override suspend fun applySolid(
            color: RgbColor,
            brightness: Int,
            power: Boolean,
        ): Boolean = true

        override suspend fun close() {
            closed = true
        }
    }
}
