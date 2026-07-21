package com.hooandee.colores.device

import com.hooandee.colores.led.SettingsProviderDescriptor
import com.hooandee.colores.led.SingleAdcJoypadDescriptor
import com.hooandee.colores.led.SysfsColorKind
import com.hooandee.colores.led.SysfsRgbDescriptor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GenericLedResolverTest {
    private val identity = AndroidDeviceIdentity("ODIN2 Portal", "kalama", "AYN", emptyMap())

    @Test
    fun `vendor route needs both the service and the color key`() {
        assertNull(GenericLedResolver.vendor(identity, pserverAvailable = false, colorKeyValue = "#FF00FF00"))
        assertNull(GenericLedResolver.vendor(identity, pserverAvailable = true, colorKeyValue = null))
        assertNull(GenericLedResolver.vendor(identity, pserverAvailable = true, colorKeyValue = "  "))
    }

    @Test
    fun `vendor route builds a two zone pserver descriptor from the product name`() {
        val detected = requireNotNull(GenericLedResolver.vendor(identity, pserverAvailable = true, colorKeyValue = "#FF112233"))

        assertEquals("generic-vendor", detected.id)
        assertEquals("ODIN2 Portal", detected.friendlyName)
        assertEquals(2, detected.capabilities.zones)
        assertTrue(detected.capabilities.perZone)
        val led = detected.led as SettingsProviderDescriptor
        assertEquals("settings_provider", led.driver)
        assertEquals("pserver", led.transport)
        assertNull(led.requiresPermission)
        assertEquals("joystick_led_light_picker_color", led.colorKey)
        assertTrue(led.enableKeys.containsAll(listOf("left_handle_light_enabled", "right_handle_light_enabled")))
    }

    @Test
    fun `sysfs route mirrors the discovered descriptor and zone count`() {
        val descriptor = SysfsRgbDescriptor("/n", zones = 4, maxBrightness = 255, kind = SysfsColorKind.MULTI_INTENSITY_HEX)

        val detected = requireNotNull(GenericLedResolver.sysfs(identity, descriptor))

        assertEquals("generic-sysfs", detected.id)
        assertEquals(4, detected.capabilities.zones)
        assertTrue(detected.capabilities.perZone)
        assertEquals(descriptor, detected.led)
    }

    @Test
    fun `sysfs route reports a single zone without per zone control`() {
        val descriptor = SysfsRgbDescriptor("/n", zones = 1, maxBrightness = 255, kind = SysfsColorKind.RGB_CHANNELS)

        val detected = requireNotNull(GenericLedResolver.sysfs(identity, descriptor))

        assertEquals(false, detected.capabilities.perZone)
    }

    @Test
    fun `falls back to manufacturer then device for the friendly name`() {
        val blankModel = AndroidDeviceIdentity(model = "", device = "kalama", manufacturer = "AYN", productProperties = emptyMap())
        val detected = requireNotNull(GenericLedResolver.vendor(blankModel, pserverAvailable = true, colorKeyValue = "#FFFFFFFF"))
        assertEquals("AYN", detected.friendlyName)
    }

    @Test
    fun `null sysfs descriptor yields no device`() {
        assertNull(GenericLedResolver.sysfs(identity, null))
    }

    @Test
    fun `joypad route builds a single zone device`() {
        val detected = requireNotNull(GenericLedResolver.joypad(identity, SingleAdcJoypadDescriptor("/n")))

        assertEquals("generic-joypad", detected.id)
        assertEquals(1, detected.capabilities.zones)
        assertEquals(false, detected.capabilities.perZone)
        assertTrue(detected.capabilities.color)
        assertTrue(detected.led is SingleAdcJoypadDescriptor)
    }

    @Test
    fun `null joypad descriptor yields no device`() {
        assertNull(GenericLedResolver.joypad(identity, null))
    }
}
