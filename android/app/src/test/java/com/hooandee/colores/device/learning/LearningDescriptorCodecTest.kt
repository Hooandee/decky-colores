package com.hooandee.colores.device.learning

import com.hooandee.colores.led.SysfsColorKind
import com.hooandee.colores.led.SysfsRgbDescriptor
import org.junit.Assert.assertEquals
import org.junit.Test

class LearningDescriptorCodecTest {
    @Test
    fun `sysfs bindings saved by earlier releases still decode`() {
        val legacy = """{"type":"sysfs","node_path":"/sys/class/leds/gamepad","zones":2,"max_brightness":255,"kind":"MULTI_INTENSITY_HEX"}"""

        assertEquals(
            SysfsRgbDescriptor("/sys/class/leds/gamepad", 2, 255, SysfsColorKind.MULTI_INTENSITY_HEX),
            decodeLearningDescriptor(legacy),
        )
    }

    @Test
    fun `legacy sysfs shape encodes without new optional fields`() {
        val encoded = encodeLearningDescriptor(SysfsRgbDescriptor("/sys/class/leds/rgb", 1, 255, SysfsColorKind.MULTI_INTENSITY_DECIMAL))

        assertEquals(setOf("type", "node_path", "zones", "max_brightness", "kind"), org.json.JSONObject(encoded).keys().asSequence().toSet())
    }

    @Test
    fun `composite channel and multi index sysfs descriptors round trip`() {
        val channels =
            SysfsRgbDescriptor(
                "/sys/class/leds/s:red",
                1,
                31,
                SysfsColorKind.CHANNEL_NODES,
                channelNodes = listOf("/sys/class/leds/s:red", "/sys/class/leds/s:green", "/sys/class/leds/s:blue"),
            )
        val ordered =
            SysfsRgbDescriptor("/sys/class/leds/rgb", 1, 255, SysfsColorKind.MULTI_INTENSITY_DECIMAL, multiIndex = listOf("green", "red", "blue"))
        val composite = SysfsRgbDescriptor(ordered.nodePath, 2, 255, SysfsColorKind.COMPOSITE, members = listOf(ordered, channels))

        assertEquals(composite, decodeLearningDescriptor(encodeLearningDescriptor(composite)))
    }
}
