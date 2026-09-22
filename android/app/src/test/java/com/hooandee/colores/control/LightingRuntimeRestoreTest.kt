package com.hooandee.colores.control

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import com.hooandee.colores.led.LedDevice
import com.hooandee.colores.led.LedState
import com.hooandee.colores.led.RgbColor
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
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
    fun `refresh cancelled before binding closes the new device once`() =
        runTest {
            val created = ClosingDevice()
            val handoff = UnboundDeviceHandoff(created, bound = null)

            val refresh =
                launch {
                    try {
                        awaitCancellation()
                    } finally {
                        handoff.closeIfUnbound()
                    }
                }
            runCurrent()
            refresh.cancel()
            refresh.join()
            handoff.closeIfUnbound()

            assertTrue(created.closed)
            assertEquals(1, created.closes)
        }

    @Test
    fun `handed off or controller bound devices are never closed`() =
        runTest {
            val handedOff = ClosingDevice()
            val controllerBound = ClosingDevice()
            val reused = ClosingDevice()

            UnboundDeviceHandoff(handedOff, bound = null).apply { markBound() }.closeIfUnbound()
            UnboundDeviceHandoff(controllerBound, bound = null) { it === controllerBound }.closeIfUnbound()
            UnboundDeviceHandoff(reused, bound = reused).closeIfUnbound()

            assertFalse(handedOff.closed)
            assertFalse(controllerBound.closed)
            assertFalse(reused.closed)
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
        var closes = 0
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
            closes++
            closed = true
        }
    }
}
