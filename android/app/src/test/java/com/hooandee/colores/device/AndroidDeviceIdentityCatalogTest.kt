package com.hooandee.colores.device

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidDeviceIdentityCatalogTest {
    private val catalog = AndroidDeviceIdentityCatalog.parse(File("../../shared/android-device-identities.json").readText())

    @Test
    fun `production presentation catalog does not duplicate the supported Portal profile`() {
        val identity = AndroidDeviceIdentity("Odin2 Portal", "kalama", "AYN", emptyMap())

        val presentation = catalog.resolve(identity, exact = null)

        assertEquals(null, presentation.id)
        assertEquals("Odin2 Portal", presentation.friendlyName)
        assertEquals(DevicePresentationSource.BUILD_MODEL, presentation.source)
    }

    @Test
    fun `matching ignores separators and case but still requires the manufacturer`() {
        val presentationCatalog =
            AndroidDeviceIdentityCatalog.parse(
                """{"schemaVersion":1,"devices":[{"id":"portal","friendlyName":"Portal","manufacturers":["AYN"],"models":["Odin2 Portal"]}]}""",
            )
        val normalized = presentationCatalog.resolve(AndroidDeviceIdentity("odin2_portal", "kalama", "ayn", emptyMap()), exact = null)
        val wrongManufacturer = presentationCatalog.resolve(AndroidDeviceIdentity("Odin2 Portal", "kalama", "Other", emptyMap()), exact = null)

        assertEquals("portal", normalized.id)
        assertEquals(DevicePresentationSource.BUILD_MODEL, wrongManufacturer.source)
        assertEquals("Odin2 Portal", wrongManufacturer.friendlyName)
    }

    @Test
    fun `an exact controllable profile always wins over the identity catalog`() {
        val exact =
            DetectedAndroidDevice(
                id = "exact-test",
                friendlyName = "Exact Device",
                capabilities = DeviceCapabilities(true, true, false, 1),
                led = com.hooandee.colores.led.SysfsRgbDescriptor(
                    "/sys/class/leds/test",
                    1,
                    255,
                    com.hooandee.colores.led.SysfsColorKind.MULTI_INTENSITY_HEX,
                ),
                previewProfileId = null,
                previewCalibration = null,
            )

        val presentation = catalog.resolve(AndroidDeviceIdentity("Odin2 Portal", "kalama", "AYN", emptyMap()), exact)

        assertEquals("exact-test", presentation.id)
        assertEquals("Exact Device", presentation.friendlyName)
        assertEquals(DevicePresentationSource.EXACT_PROFILE, presentation.source)
    }

    @Test
    fun `invalid catalog and blank identity degrade safely`() {
        val invalid = AndroidDeviceIdentityCatalog.parse("not-json")
        val modelFallback = invalid.resolve(AndroidDeviceIdentity("  Mystery_Device  ", "", "", emptyMap()), exact = null)
        val emptyFallback = invalid.resolve(AndroidDeviceIdentity("", "", "", emptyMap()), exact = null)

        assertEquals("Mystery Device", modelFallback.friendlyName)
        assertEquals(DevicePresentationSource.BUILD_MODEL, modelFallback.source)
        assertFalse(modelFallback.isKnown)
        assertEquals("", emptyFallback.friendlyName)
        assertEquals(DevicePresentationSource.UNKNOWN, emptyFallback.source)
        assertTrue(emptyFallback.id == null)
    }
}
