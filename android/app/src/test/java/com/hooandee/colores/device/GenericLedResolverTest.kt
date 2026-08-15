package com.hooandee.colores.device

import com.hooandee.colores.device.learning.ProbeSurface
import com.hooandee.colores.led.SingleAdcJoypadDescriptor
import com.hooandee.colores.led.SysfsColorKind
import com.hooandee.colores.led.SysfsRgbDescriptor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GenericLedResolverTest {
    @Test
    fun `settings candidate needs both the service and a valid color value`() {
        assertNull(GenericLedResolver.settingsCandidate(pserverAvailable = false, colorKeyValue = "#FF00FF00"))
        assertNull(GenericLedResolver.settingsCandidate(pserverAvailable = true, colorKeyValue = null))
        assertNull(GenericLedResolver.settingsCandidate(pserverAvailable = true, colorKeyValue = "invalid"))
    }

    @Test
    fun `settings signal becomes a candidate without confirming its observed zone count`() {
        val candidate = requireNotNull(GenericLedResolver.settingsCandidate(pserverAvailable = true, colorKeyValue = "#FF112233,#FF445566"))

        assertEquals("settings-pserver-joystick", candidate.cartridgeId)
        assertEquals(ProbeSurface.SETTINGS_PSERVER, candidate.surface)
        assertEquals(setOf("observed_color_count"), candidate.signalKeys)
    }

    @Test
    fun `sysfs signal becomes a candidate without confirming zone capability`() {
        val descriptor = SysfsRgbDescriptor("/n", zones = 4, maxBrightness = 255, kind = SysfsColorKind.MULTI_INTENSITY_HEX)

        val candidate = requireNotNull(GenericLedResolver.sysfsCandidate(descriptor))

        assertEquals("android-sysfs-multicolor", candidate.cartridgeId)
        assertEquals(setOf("color_kind", "observed_index_count"), candidate.signalKeys)
        assertEquals(descriptor, candidate.descriptor)
    }

    @Test
    fun `null sysfs descriptor yields no device`() {
        assertNull(GenericLedResolver.sysfsCandidate(null))
    }

    @Test
    fun `joypad signal becomes a single zone candidate`() {
        val candidate = requireNotNull(GenericLedResolver.joypadCandidate(SingleAdcJoypadDescriptor("/n")))

        assertEquals("singleadc-joypad", candidate.cartridgeId)
        assertEquals(setOf("singleadc_surface"), candidate.signalKeys)
        assertTrue(candidate.descriptor is SingleAdcJoypadDescriptor)
    }

    @Test
    fun `null joypad descriptor yields no device`() {
        assertNull(GenericLedResolver.joypadCandidate(null))
    }
}
