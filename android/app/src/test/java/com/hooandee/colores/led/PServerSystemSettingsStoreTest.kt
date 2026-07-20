package com.hooandee.colores.led

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PServerSystemSettingsStoreTest {
    @Test
    fun `writes system settings through PServer with shell-safe arguments`() {
        val values = mutableMapOf("color" to "current")
        val executor =
            FakePServerCommandExecutor(available = true) {
                values["joystick_led_light_picker_color"] = "#FFFF0000,#FF0000FF"
            }
        val store = PServerSystemSettingsStore(values::get, executor)

        assertTrue(store.available)
        assertEquals("current", store.get("color"))
        assertTrue(store.put("joystick_led_light_picker_color", "#FFFF0000,#FF0000FF"))
        assertEquals(
            "settings put system 'joystick_led_light_picker_color' '#FFFF0000,#FF0000FF'",
            executor.commands.single(),
        )
    }

    @Test
    fun `is unavailable when PServer is missing`() {
        val store = PServerSystemSettingsStore({ null }, FakePServerCommandExecutor(available = false))

        assertFalse(store.available)
        assertFalse(store.put("color", "value"))
    }

    @Test
    fun `rejects a command whose value is not written`() {
        val store = PServerSystemSettingsStore({ "old" }, FakePServerCommandExecutor(available = true))

        assertFalse(store.put("color", "new"))
    }

    @Test
    fun `quotes apostrophes in settings values`() {
        var written: String? = null
        val executor = FakePServerCommandExecutor(available = true) { written = "player's color" }
        val store = PServerSystemSettingsStore({ written }, executor)

        assertTrue(store.put("label", "player's color"))
        assertEquals("settings put system 'label' 'player'\"'\"'s color'", executor.commands.single())
    }
}

private class FakePServerCommandExecutor(
    override val available: Boolean,
    private val onExecute: () -> Unit = {},
) : PServerCommandExecutor {
    val commands = mutableListOf<String>()

    override fun execute(command: String): Boolean {
        if (!available) return false
        commands += command
        onExecute()
        return true
    }
}
