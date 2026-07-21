package com.hooandee.colores.device

import com.hooandee.colores.led.SysfsAccess
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SingleAdcJoypadDiscoveryTest {
    private val base = "/sys/bus/platform/devices/singleadc-joypad"

    @Test
    fun `detects the joypad when the color node and latch are writable`() {
        val access = fakeAccess(setOf("$base/custum_rgb_r", "$base/led_set"))

        val descriptor = SingleAdcJoypadDiscovery.scan(base, access)

        assertEquals(base, descriptor?.basePath)
    }

    @Test
    fun `absent when the color node is missing`() {
        val access = fakeAccess(setOf("$base/led_set"))
        assertNull(SingleAdcJoypadDiscovery.scan(base, access))
    }

    @Test
    fun `absent when the nodes are read only`() {
        val access =
            object : SysfsAccess {
                override fun read(path: String): String? = null

                override fun exists(path: String): Boolean = true

                override fun canWrite(path: String): Boolean = false

                override fun write(
                    path: String,
                    value: String,
                ): Boolean = false
            }
        assertNull(SingleAdcJoypadDiscovery.scan(base, access))
    }

    private fun fakeAccess(writable: Set<String>): SysfsAccess =
        object : SysfsAccess {
            override fun read(path: String): String? = null

            override fun exists(path: String): Boolean = path in writable

            override fun canWrite(path: String): Boolean = path in writable

            override fun write(
                path: String,
                value: String,
            ): Boolean = path in writable
        }
}
