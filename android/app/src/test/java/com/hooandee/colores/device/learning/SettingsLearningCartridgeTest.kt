package com.hooandee.colores.device.learning

import com.hooandee.colores.device.AndroidDeviceIdentity
import com.hooandee.colores.device.GenericLedResolver
import com.hooandee.colores.led.SystemSettingsStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsLearningCartridgeTest {
    @Test
    fun `missing color snapshot refuses every write`() {
        val store = FakeSettingsStore(mutableMapOf())
        val cartridge = SettingsLearningCartridge(store)
        val candidate = requireNotNull(GenericLedResolver.settingsCandidate(true, "#FF112233"))

        assertNull(cartridge.snapshot(candidate))
        assertFalse(cartridge.execute(candidate, ProbeStep.COLOR))
        assertTrue(store.writes.isEmpty())
    }

    @Test
    fun `probe uses limited values and restores all touched settings`() {
        val original =
            mutableMapOf(
                "joystick_led_light_picker_color" to "#FF010203,#FF040506",
                "led_light_brightness_percent" to "0.72",
                "joystick_light_enabled" to "1,1",
            )
        val store = FakeSettingsStore(original.toMutableMap())
        val cartridge = SettingsLearningCartridge(store)
        val candidate = requireNotNull(GenericLedResolver.settingsCandidate(true, original.getValue("joystick_led_light_picker_color")))
        val snapshot = requireNotNull(cartridge.snapshot(candidate))

        assertTrue(cartridge.execute(candidate, ProbeStep.COLOR))
        assertEquals("#FFFF00FF,#FFFF00FF", store.values["joystick_led_light_picker_color"])
        assertTrue(cartridge.execute(candidate, ProbeStep.BRIGHTNESS_HIGH))
        assertEquals("0.55", store.values["led_light_brightness_percent"])
        assertEquals(RollbackStatus.RESTORED_AND_READ_BACK, cartridge.restore(candidate, snapshot))
        assertEquals(original, store.values)
    }

    @Test
    fun `failed restore still attempts every snapshotted setting`() {
        val original =
            linkedMapOf(
                "joystick_led_light_picker_color" to "#FF010203,#FF040506",
                "led_light_brightness_percent" to "0.72",
                "joystick_light_enabled" to "1,1",
            )
        val failedKey = original.keys.first()
        val store = FakeSettingsStore(original.toMutableMap(), setOf(failedKey))
        val cartridge = SettingsLearningCartridge(store)
        val candidate = requireNotNull(GenericLedResolver.settingsCandidate(true, original.getValue(failedKey)))
        val snapshot = requireNotNull(cartridge.snapshot(candidate))

        assertEquals(RollbackStatus.RESTORE_FAILED, cartridge.restore(candidate, snapshot))
        assertEquals(snapshot.values.keys.toList(), store.writes.map { it.first })
    }

    @Test
    fun `restore rejects snapshot keys outside the audited settings`() {
        val original =
            mutableMapOf(
                "joystick_led_light_picker_color" to "#FF010203",
                "led_light_brightness_percent" to "0.72",
                "joystick_light_enabled" to "1",
                "unrelated_setting" to "original",
            )
        val store = FakeSettingsStore(original)
        val cartridge = SettingsLearningCartridge(store)
        val candidate = requireNotNull(GenericLedResolver.settingsCandidate(true, original.getValue("joystick_led_light_picker_color")))
        val snapshot = ProbeSnapshot(mapOf("joystick_led_light_picker_color" to "#FF010203", "unrelated_setting" to "changed"))

        assertEquals(RollbackStatus.RESTORE_FAILED, cartridge.restore(candidate, snapshot))
        assertTrue(store.writes.isEmpty())
    }

    private class FakeSettingsStore(
        val values: MutableMap<String, String>,
        private val failedWrites: Set<String> = emptySet(),
    ) : SystemSettingsStore {
        override val available = true
        val writes = mutableListOf<Pair<String, String>>()

        override fun get(key: String): String? = values[key]

        override fun put(
            key: String,
            value: String,
        ): Boolean {
            if (key !in values) return false
            writes += key to value
            if (key in failedWrites) return false
            values[key] = value
            return true
        }
    }
}
