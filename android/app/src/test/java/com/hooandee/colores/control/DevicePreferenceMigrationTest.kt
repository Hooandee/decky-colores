package com.hooandee.colores.control

import com.hooandee.colores.gradient.DeviceGradientPreferences
import com.hooandee.colores.gradient.GradientPreferences
import com.hooandee.colores.gradient.LightingMode
import com.hooandee.colores.led.RgbColor
import com.hooandee.colores.profiles.LightingProfileStore
import com.hooandee.colores.profiles.ProfilePatch
import com.hooandee.colores.profiles.ProfileScope
import org.junit.Assert.assertEquals
import org.junit.Test

class DevicePreferenceMigrationTest {
    @Test
    fun `promotion migrates every device keyed store as one operation`() {
        val lightingValues = mutableMapOf<String, String>()
        val gradientValues = mutableMapOf<String, String>()
        val profileValues = mutableMapOf<String, String>()
        val lighting = LightingPreferences(lightingValues::get) { key, value -> lightingValues[key] = value }
        val gradients = GradientPreferences(gradientValues::get) { key, value -> gradientValues[key] = value }
        val profiles = LightingProfileStore(profileValues::get) { key, value -> profileValues[key] = value }
        val source = "learned-htr3212-portal"
        val target = "ayn-odin2-portal"
        val storedLighting = StoredLighting(mode = AppMode.GRADIENT, brightness = 61)
        val storedGradient =
            DeviceGradientPreferences(
                mode = LightingMode.GRADIENT,
                currentStops = listOf(RgbColor(255, 0, 0), RgbColor(0, 0, 255)),
            )
        lighting.save(source, storedLighting)
        gradients.save(source, storedGradient)
        profiles.patch(source, ProfileScope.Global, ProfilePatch(brightness = 42))

        DevicePreferenceMigration(lighting, gradients, profiles).migrate(source, target)

        assertEquals(storedLighting, lighting.load(target))
        assertEquals(storedGradient, gradients.load(target))
        assertEquals(42, profiles.global(target).brightness)
        assertEquals(target, lighting.activeDeviceId())
    }
}
