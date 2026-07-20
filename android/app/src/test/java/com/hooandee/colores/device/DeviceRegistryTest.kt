package com.hooandee.colores.device

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
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
                "driver": "htr3212",
                "transport": "pserver",
                "colorKey": "joystick_led_light_picker_color",
                "colorFormat": "argb_hex_csv",
                "brightnessKey": "led_light_brightness_percent",
                "brightnessRange": [0.0, 1.0],
                "enableKeys": ["joystick_light_enabled", "left_joystick_light_enabled", "right_joystick_light_enabled"],
                "zones": 8,
                "requiresPermission": null,
                "vendorService": "com.rp.gameassistant",
                "htr3212": {
                  "leftBus": 1,
                  "rightBus": 0,
                  "address": 60,
                  "leftOrder": [0, 1, 3, 2],
                  "rightOrder": [1, 2, 3, 0]
                }
              }
            },
            "linux": null,
            "capabilities": { "color": true, "brightness": true, "perZone": true },
            "previewProfile": "retroid-stick-ring-rp5-v1"
          },
          {
            "id": "shared-led-test-device",
            "friendlyName": "Shared LED Test Device",
            "android": {
              "model": ["Shared LED Test Device"],
              "device": ["shared-led-test"],
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
            "previewProfile": "retroid-stick-ring-rp5-v1"
          }
        ]
        """.trimIndent()

    private val previewProfilesJson =
        """
        [
          {
            "id": "retroid-stick-ring-rp5-v1",
            "calibration": {
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
            DeviceRegistry.parse(registryJson, previewProfilesJson).match(
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
        assertEquals(8, match.capabilities.zones)
        assertEquals("htr3212", match.led.driver)
        assertEquals("pserver", match.led.transport)
        assertNull(match.led.requiresPermission)
        assertEquals("joystick_led_light_picker_color", match.led.colorKey)
        assertEquals(0.0f, match.led.brightnessRange.start)
        assertEquals(1.0f, match.led.brightnessRange.endInclusive)
        requireNotNull(match.led.htr3212)
        assertEquals(1, match.led.htr3212.leftBus)
        assertEquals(0, match.led.htr3212.rightBus)
        assertEquals(60, match.led.htr3212.address)
        assertEquals(listOf(0, 1, 3, 2), match.led.htr3212.leftOrder)
        assertEquals(listOf(1, 2, 3, 0), match.led.htr3212.rightOrder)
    }

    @Test
    fun `matching is case insensitive and trims identity values`() {
        val match =
            DeviceRegistry.parse(registryJson, previewProfilesJson).match(
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
        val match = DeviceRegistry.parse(registryJson, previewProfilesJson).match(rp5Identity())

        requireNotNull(match)
        assertEquals("retroid-stick-ring-rp5-v1", match.previewProfileId)
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
    fun `unknown LED preview profile remains optional`() {
        val unknownProfile = registryJson.replace("retroid-stick-ring-rp5-v1", "missing-profile")
        val match = DeviceRegistry.parse(unknownProfile, previewProfilesJson).match(rp5Identity())

        assertNull(match?.previewCalibration)
    }

    @Test
    fun `invalid LED preview profile is discarded atomically`() {
        val malformed = previewProfilesJson.replace("\"whiteMix\"", "\"whiteMi\"")
        val match = DeviceRegistry.parse(registryJson, malformed).match(rp5Identity())

        assertNull(match?.previewCalibration)
    }

    @Test
    fun `two devices can share the same resolved LED preview profile`() {
        val registry = DeviceRegistry.parse(registryJson, previewProfilesJson)
        val rp5 = requireNotNull(registry.match(rp5Identity()))
        val shared =
            requireNotNull(
                registry.match(
                    AndroidDeviceIdentity(
                        model = "Shared LED Test Device",
                        device = "shared-led-test",
                        manufacturer = "Test",
                        productProperties = emptyMap(),
                    ),
                ),
            )

        assertEquals(rp5.previewProfileId, shared.previewProfileId)
        assertSame(rp5.previewCalibration, shared.previewCalibration)
    }

    @Test
    fun `does not identify another kona device as the RP5`() {
        val match =
            DeviceRegistry.parse(registryJson, previewProfilesJson).match(
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
        val registry = DeviceRegistry.parse("{not-json", previewProfilesJson)

        assertTrue(registry.devices.isEmpty())
        assertFalse(registry.hasControllableDevices)
    }

    @Test
    fun `production registry resolves the RP5 preview profile`() {
        val shared = File("../../shared")
        val registry =
            DeviceRegistry.parse(
                devicesJson = shared.resolve("devices.json").readText(),
                previewProfilesJson = shared.resolve("led-preview-profiles.json").readText(),
            )

        val match = registry.match(rp5Identity())

        assertEquals("retroid-stick-ring-rp5-v1", match?.previewProfileId)
        assertTrue(match?.previewCalibration != null)
    }

    @Test
    fun `production registry resolves the AYN Thor vendor settings descriptor`() {
        val shared = File("../../shared")
        val registry =
            DeviceRegistry.parse(
                devicesJson = shared.resolve("devices.json").readText(),
                previewProfilesJson = shared.resolve("led-preview-profiles.json").readText(),
            )

        val match =
            registry.match(
                AndroidDeviceIdentity(
                    model = "AYN Thor",
                    device = "kalama",
                    manufacturer = "AYN",
                    productProperties = emptyMap(),
                ),
            )

        requireNotNull(match)
        assertEquals("ayn-thor", match.id)
        assertEquals("settings_provider", match.led.driver)
        assertEquals("pserver", match.led.transport)
        assertEquals(2, match.capabilities.zones)
        assertTrue(match.capabilities.perZone)
        assertEquals("joystick_led_light_picker_color", match.led.colorKey)
        assertNull(match.led.requiresPermission)
        assertEquals(listOf("joystick_light_enabled"), match.led.enableKeys)
        assertEquals("com.odin.gameassistant", match.led.vendorService)
    }

    @Test
    fun `a plain kalama device is not identified as the AYN Thor`() {
        val shared = File("../../shared")
        val registry =
            DeviceRegistry.parse(
                devicesJson = shared.resolve("devices.json").readText(),
                previewProfilesJson = shared.resolve("led-preview-profiles.json").readText(),
            )

        val match =
            registry.match(
                AndroidDeviceIdentity(
                    model = "Some Phone",
                    device = "kalama",
                    manufacturer = "Other",
                    productProperties = emptyMap(),
                ),
            )

        assertNull(match)
    }

    private fun rp5Identity() =
        AndroidDeviceIdentity(
            model = "Retroid Pocket 5",
            device = "kona",
            manufacturer = "Moorechip",
            productProperties = emptyMap(),
        )
}
