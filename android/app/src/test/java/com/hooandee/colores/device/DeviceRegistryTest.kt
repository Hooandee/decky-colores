package com.hooandee.colores.device

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceRegistryTest {
    private val registryJson =
        """
        [
          {
            "id": "retroid-pocket-5",
            "friendlyName": "Retroid Pocket 5",
            "android": {
              "model": ["Retroid Pocket 5"],
              "device": ["kona"],
              "led": {
                "driver": "settings_provider",
                "transport": "pserver",
                "colorKey": "joystick_led_light_picker_color",
                "colorFormat": "argb_hex_csv",
                "brightnessKey": "led_light_brightness_percent",
                "brightnessRange": [0.0, 1.0],
                "enableKeys": ["joystick_light_enabled", "left_joystick_light_enabled", "right_joystick_light_enabled"],
                "zones": 2,
                "requiresPermission": null,
                "vendorService": "com.rp.gameassistant"
              }
            },
            "linux": null,
            "capabilities": { "color": true, "brightness": true, "perZone": true },
            "previewCalibration": {
              "saturationScale": 0.84,
              "whiteMix": 0.08,
              "redGain": 1.0,
              "greenGain": 1.08,
              "blueGain": 1.0,
              "valueGamma": 0.95,
              "glowAlpha": 0.22,
              "hueMap": [
                { "input": 36, "output": 56 },
                { "input": 347, "output": 315 }
              ]
            }
          }
        ]
        """.trimIndent()

    @Test
    fun `matches the RP5 and returns capabilities and LED descriptor`() {
        val match =
            DeviceRegistry.parse(registryJson).match(
                AndroidDeviceIdentity(
                    model = "Retroid Pocket 5",
                    device = "kona",
                    manufacturer = "Moorechip",
                    productProperties = emptyMap(),
                ),
            )

        requireNotNull(match)
        assertEquals("retroid-pocket-5", match.id)
        assertEquals("Retroid Pocket 5", match.friendlyName)
        assertTrue(match.capabilities.color)
        assertTrue(match.capabilities.brightness)
        assertTrue(match.capabilities.perZone)
        assertEquals(2, match.capabilities.zones)
        assertEquals("settings_provider", match.led.driver)
        assertEquals("pserver", match.led.transport)
        assertNull(match.led.requiresPermission)
        assertEquals("joystick_led_light_picker_color", match.led.colorKey)
        assertEquals(0.0f, match.led.brightnessRange.start)
        assertEquals(1.0f, match.led.brightnessRange.endInclusive)
    }

    @Test
    fun `matching is case insensitive and trims identity values`() {
        val match =
            DeviceRegistry.parse(registryJson).match(
                AndroidDeviceIdentity(
                    model = "  retroid pocket 5 ",
                    device = "KONA",
                    manufacturer = "Moorechip",
                    productProperties = emptyMap(),
                ),
            )

        assertEquals("retroid-pocket-5", match?.id)
    }

    @Test
    fun `returns optional LED preview calibration`() {
        val match = DeviceRegistry.parse(registryJson).match(rp5Identity())

        requireNotNull(match)
        requireNotNull(match.previewCalibration)
        assertEquals(0.84f, match.previewCalibration.saturationScale)
        assertEquals(0.08f, match.previewCalibration.whiteMix)
        assertEquals(1.08f, match.previewCalibration.greenGain)
        assertEquals(0.22f, match.previewCalibration.glowAlpha)
        assertEquals(
            listOf(
                LedPreviewHuePoint(36f, 56f),
                LedPreviewHuePoint(347f, 315f),
            ),
            match.previewCalibration.hueMap,
        )
    }

    @Test
    fun `missing LED preview calibration remains optional`() {
        val withoutProfile =
            registryJson.replace(
                Regex("""\s*,\s*"previewCalibration"\s*:\s*\{[^}]*}"""),
                "",
            )
        val match = DeviceRegistry.parse(withoutProfile).match(rp5Identity())

        assertNull(match?.previewCalibration)
    }

    @Test
    fun `does not identify another kona device as the RP5`() {
        val match =
            DeviceRegistry.parse(registryJson).match(
                AndroidDeviceIdentity(
                    model = "Other handheld",
                    device = "kona",
                    manufacturer = "Other",
                    productProperties = emptyMap(),
                ),
            )

        assertNull(match)
    }

    @Test
    fun `malformed registry degrades to an empty registry`() {
        val registry = DeviceRegistry.parse("{not-json")

        assertTrue(registry.devices.isEmpty())
        assertFalse(registry.hasControllableDevices)
    }

    private fun rp5Identity() =
        AndroidDeviceIdentity(
            model = "Retroid Pocket 5",
            device = "kona",
            manufacturer = "Moorechip",
            productProperties = emptyMap(),
        )
}
