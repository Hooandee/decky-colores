package com.hooandee.colores.device.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HardwareInventoryCollectorTest {
    @Test
    fun `inventory describes the LED surface without personal identifiers`() {
        val source = FakeInventorySource()

        val inventory = HardwareInventoryCollector(source).collect()
        val raw = listOf(inventory.kernel, inventory.sysfs, inventory.hardware).joinToString()

        val build = inventory.hardware.getJSONObject("build")
        assertEquals("AYN", build.getString("manufacturer"))
        assertEquals("sm8550", build.getString("board_platform"))
        assertFalse(build.has("serial"))
        val led = inventory.sysfs.getJSONArray("leds").getJSONObject(0)
        assertEquals("rgb:stick", led.getString("name"))
        assertTrue(led.getBoolean("brightness_writable"))
        assertFalse(led.getBoolean("multi_intensity_writable"))
        assertEquals("red green blue", led.getString("multi_index"))
        assertEquals("timer", led.getString("trigger"))
        assertEquals("htr3212l", inventory.sysfs.getJSONObject("i2c").getJSONArray("devices").getJSONObject(0).getString("name"))
        assertTrue(inventory.sysfs.getJSONObject("singleadc_joypad").getBoolean("present"))
        assertEquals(listOf("htr3212-leds", "joypad"), inventory.sysfs.getJSONArray("platform_devices").let { a -> (0 until a.length()).map(a::getString) })
        val packages = inventory.hardware.getJSONArray("vendor_packages")
        assertEquals("1.2", packages.getJSONObject(0).getString("version"))
        assertFalse(packages.getJSONObject(1).getBoolean("installed"))
        assertFalse(raw.contains("SERIAL123"))
        assertFalse(raw.contains("owner@example.com"))
    }

    @Test
    fun `privileged snapshot keeps only lighting settings and redacts free text`() {
        val source = FakeInventorySource()

        val pserver = HardwareInventoryCollector(source).collect().hardware.getJSONObject("pserver")

        assertEquals("ok", pserver.getString("status"))
        val system = pserver.getJSONObject("settings").getJSONArray("system")
        assertEquals(2, system.length())
        assertEquals("joystick_led_light_picker_color=#FF00FF00", system.getString(0))
        assertTrue(system.getString(1).startsWith("led_note=[email]"))
        assertEquals(0, pserver.getJSONObject("settings").getJSONArray("secure").length())
        assertEquals("vendor.led: [pserver]", pserver.getJSONArray("services").getString(0))
        assertEquals(1, pserver.getJSONArray("services").length())
    }

    @Test
    fun `settings filter rejects user components and package lists`() {
        val rejected =
            mapOf(
                "enabled_accessibility_services" to "com.example.reader/com.example.reader.LightService",
                "enabled_input_methods" to "com.example.keys/.LightIme",
                "enabled_notification_listeners" to "com.example.led/com.example.led.Listener",
                "enabled_notification_policy_access_packages" to "com.example.light",
                "enabled_print_services" to "com.example.print/.LedPrint",
                "disabled_system_input_methods" to "com.example.stick/.Ime",
                "led_light_services" to "1",
                "joystick_light_account" to "1",
                "led_device_name" to "Mi consola",
                "rgb_wifi_light" to "1",
                "light_target" to "com.example.app/.MainActivity",
                "led_owner_package" to "com.example.lights.app",
                "flashlight_mode" to "1",
                "screen_brightness_mode" to "led",
            )
        rejected.forEach { (key, value) -> assertFalse(key, HardwareInventoryCollector.isLightingSetting(key, value)) }

        val accepted =
            mapOf(
                "joystick_led_light_picker_color" to "#FF00FF00",
                "led_light_brightness_percent" to "80",
                "left_joystick_light_enabled" to "1",
            )
        accepted.forEach { (key, value) -> assertTrue(key, HardwareInventoryCollector.isLightingSetting(key, value)) }
    }

    @Test
    fun `privileged script excludes component settings before they leave the shell`() {
        val script = HardwareInventoryCollector.PSERVER_SCRIPT

        listOf("enabled_", "disabled_", "_services", "input_method", "listeners", "packages", "component", "accessibility", "notification")
            .forEach { assertTrue(it, script.contains(it)) }
        assertFalse(script.contains("grep -iE 'led|light|rgb|joystick"))
    }

    @Test
    fun `privileged script only reads`() {
        val script = HardwareInventoryCollector.PSERVER_SCRIPT

        listOf("settings put", "i2cset", "rm ", "echo 1", "chmod", "setprop").forEach { assertFalse(it, script.contains(it)) }
        assertFalse(Regex("[^2]>").containsMatchIn(script))
    }

    @Test
    fun `slow reads stay inside the collection budget`() {
        val source = FakeInventorySource(readDelayMs = 2_000)
        val started = System.nanoTime()

        val inventory = HardwareInventoryCollector(source, budgetMs = 600, readTimeoutMs = 50, pserverTimeoutMs = 100).collect()

        val elapsedMs = (System.nanoTime() - started) / 1_000_000
        assertTrue("elapsed $elapsedMs", elapsedMs < 1_500)
        assertTrue(inventory.hardware.getJSONObject("collection").getInt("timeouts") > 0)
        assertEquals("AYN", inventory.hardware.getJSONObject("build").getString("manufacturer"))
    }

    @Test
    fun `oversized surfaces are capped`() {
        val source = FakeInventorySource(ledCount = 400, hugeSettings = true)

        val inventory = HardwareInventoryCollector(source).collect()

        val total = listOf(inventory.kernel, inventory.sysfs, inventory.hardware).sumOf { it.toString().toByteArray().size }
        assertTrue("total $total", total <= HardwareInventoryCollector.TOTAL_CAP)
        assertTrue(inventory.hardware.getJSONObject("collection").getJSONArray("truncated").length() > 0)
    }

    private class FakeInventorySource(
        private val readDelayMs: Long = 0,
        private val ledCount: Int = 1,
        private val hugeSettings: Boolean = false,
    ) : HardwareInventorySource {
        override fun buildFields(): Map<String, String> = mapOf("manufacturer" to "AYN", "model" to "Thor", "serial" to "SERIAL123")

        override fun systemProperty(name: String): String? = if (name == "ro.board.platform") "sm8550" else null

        override fun kernelVersion(): String = "5.15.0"

        override fun list(path: String): List<String>? =
            when (path) {
                "/sys/class/leds" -> if (ledCount == 1) listOf("rgb:stick") else (0 until ledCount).map { "led-$it-with-a-rather-long-name" }
                "/sys/bus/i2c/devices" -> listOf("3-003c", "i2c-3")
                "/sys/bus/platform/devices" -> listOf("joypad", "soc", "htr3212-leds")
                "/sys/bus/platform/devices/singleadc-joypad" -> listOf("led_set", "custum_rgb_r")
                else ->
                    if (path.startsWith("/sys/class/leds/")) {
                        listOf("brightness", "max_brightness", "multi_index", "multi_intensity", "trigger")
                    } else {
                        null
                    }
            }

        override fun read(path: String): String? {
            if (readDelayMs > 0) Thread.sleep(readDelayMs)
            return when {
                path.endsWith("/multi_index") -> "red green blue"
                path.endsWith("/max_brightness") -> "255"
                path.endsWith("/trigger") -> "none [timer] heartbeat"
                path.endsWith("/name") -> "htr3212l"
                else -> null
            }
        }

        override fun exists(path: String): Boolean = path == "/sys/bus/platform/devices/singleadc-joypad"

        override fun canWrite(path: String): Boolean = path.endsWith("/brightness")

        override fun pserverAvailable(): Boolean = true

        override fun runPServerScript(
            script: String,
            timeoutMs: Long,
        ): String {
            if (readDelayMs > 0) Thread.sleep(readDelayMs)
            val extra = if (hugeSettings) (0 until 3_000).joinToString("\n") { "led_filler_$it=${"x".repeat(100)}" } else ""
            return listOf(
                "## settings_system",
                "joystick_led_light_picker_color=#FF00FF00",
                "led_note=owner@example.com +34 612 345 678",
                "screen_brightness_mode=led",
                extra,
                "## settings_secure",
                "android_id=led",
                "enabled_accessibility_services=com.example.reader/com.example.reader.LightService",
                "enabled_notification_listeners=com.example.led/com.example.led.Listener",
                "## services",
                "vendor.led: [pserver]",
                "activity: [android.app.IActivityManager]",
            ).joinToString("\n")
        }

        override fun packageVersion(packageName: String): PackagePresence =
            if (packageName == "com.rp.gameassistant") PackagePresence.Installed("1.2") else PackagePresence.Absent
    }
}
