package com.hooandee.colores.profiles

import com.hooandee.colores.control.AppMode
import com.hooandee.colores.led.RgbColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LightingProfileStoreTest {
    private fun profileStore(initial: MutableMap<String, String> = mutableMapOf()) =
        LightingProfileStore(
            read = initial::get,
            write = { key, value -> initial[key] = value },
        )

    @Test
    fun `unseen package inherits global`() {
        val store = profileStore()

        assertEquals(store.global("rp5"), store.effective("rp5", "org.example.game"))
    }

    @Test
    fun `following global preserves own default profile`() {
        val store = profileStore()
        store.patch("rp5", ProfileScope.App("org.example.game"), ProfilePatch(brightness = 25))
        store.setFollowGlobal("rp5", "org.example.game", true)

        assertEquals(100, store.effective("rp5", "org.example.game").brightness)

        store.setFollowGlobal("rp5", "org.example.game", false)
        assertEquals(25, store.effective("rp5", "org.example.game").brightness)
    }

    @Test
    fun `profiles remain independent by device`() {
        val store = profileStore()

        store.patch("rp5", ProfileScope.Global, ProfilePatch(brightness = 30))

        assertEquals(30, store.global("rp5").brightness)
        assertEquals(100, store.global("thor").brightness)
    }

    @Test
    fun `device promotion copies the complete profile envelope without overwriting native profiles`() {
        val store = profileStore()
        store.patch("learned-portal", ProfileScope.Global, ProfilePatch(brightness = 37))
        store.patch("learned-portal", ProfileScope.App("org.example.game"), ProfilePatch(mode = AppMode.EFFECT))

        store.migrateDevice("learned-portal", "ayn-odin2-portal")

        assertEquals(37, store.global("ayn-odin2-portal").brightness)
        assertEquals(AppMode.EFFECT, store.effective("ayn-odin2-portal", "org.example.game").mode)
        store.patch("ayn-odin2-portal", ProfileScope.Global, ProfilePatch(brightness = 72))
        store.patch("learned-portal", ProfileScope.Global, ProfilePatch(brightness = 12))
        store.migrateDevice("learned-portal", "ayn-odin2-portal")
        assertEquals(72, store.global("ayn-odin2-portal").brightness)
    }

    @Test
    fun `values are bounded and nested fields are preserved`() {
        val store = profileStore()
        store.patch(
            "rp5",
            ProfileScope.Global,
            ProfilePatch(
                brightness = -5,
                speed = 140,
                gradientSpeed = 120,
                effectUsesGradient = true,
                solidColor = RgbColor(300, -2, 120),
                effectId = "wave",
                temperatureBreathe = false,
            ),
        )

        val profile = store.global("rp5")
        assertEquals(0, profile.brightness)
        assertEquals(100, profile.speed)
        assertEquals(100, profile.gradientSpeed)
        assertTrue(profile.effectUsesGradient)
        assertEquals(RgbColor(255, 0, 120), profile.solidColor)
        assertEquals("wave", profile.effectId)
        assertFalse(profile.temperatureBreathe)
    }

    @Test
    fun `forget restores inheritance`() {
        val store = profileStore()
        store.patch("rp5", ProfileScope.App("org.game"), ProfilePatch(mode = AppMode.EFFECT))

        assertTrue(store.scopeState("rp5", "org.game").hasAppProfile)
        store.forget("rp5", "org.game")

        assertFalse(store.scopeState("rp5", "org.game").hasAppProfile)
        assertEquals(store.global("rp5"), store.effective("rp5", "org.game"))
    }

    @Test
    fun `corrupt device entry does not affect another device`() {
        val data = mutableMapOf("profiles:rp5" to "not json")
        val store = profileStore(data)
        store.patch("thor", ProfileScope.Global, ProfilePatch(brightness = 44))

        assertEquals(100, store.global("rp5").brightness)
        assertEquals(44, store.global("thor").brightness)
    }

    @Test
    fun `automation setting is explicit and defaults off`() {
        val store = profileStore()

        assertFalse(store.isAutomationEnabled())
        store.setAutomationEnabled(true)

        assertTrue(store.isAutomationEnabled())
    }

    @Test
    fun `configured profiles only returns apps with their own active values`() {
        val store = profileStore()
        store.patch("thor", ProfileScope.App("org.own"), ProfilePatch(brightness = 42, mode = AppMode.EFFECT))
        store.patch("thor", ProfileScope.App("org.follow"), ProfilePatch(brightness = 18))
        store.setFollowGlobal("thor", "org.follow", true)

        assertEquals(
            listOf(ConfiguredProfile("org.own", store.effective("thor", "org.own"))),
            store.configuredProfiles("thor"),
        )
    }
}
