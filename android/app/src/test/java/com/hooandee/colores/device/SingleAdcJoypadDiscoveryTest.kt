package com.hooandee.colores.device

import com.hooandee.colores.led.FakeSysfsAccess
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SingleAdcJoypadDiscoveryTest {
    private val base = "/sys/bus/platform/devices/singleadc-joypad"

    @Test
    fun `detects the joypad when the color node and latch are writable`() {
        val access = FakeSysfsAccess(setOf("$base/custum_rgb_r", "$base/led_set"))

        val descriptor = SingleAdcJoypadDiscovery.scan(base, access)

        assertEquals(base, descriptor?.basePath)
    }

    @Test
    fun `absent when the color node is missing`() {
        val access = FakeSysfsAccess(setOf("$base/led_set"))
        assertNull(SingleAdcJoypadDiscovery.scan(base, access))
    }

    @Test
    fun `absent when the nodes are read only`() {
        val access =
            FakeSysfsAccess(
                writable = emptySet(),
                values = mutableMapOf("$base/custum_rgb_r" to "0", "$base/led_set" to "0"),
            )
        assertNull(SingleAdcJoypadDiscovery.scan(base, access))
    }
}
