package com.hooandee.colores.profiles

import org.junit.Assert.assertEquals
import com.hooandee.colores.control.AppMode
import com.hooandee.colores.control.LightingIntent
import com.hooandee.colores.control.applying
import com.hooandee.colores.led.RgbColor
import org.junit.Test

class LightingProfileCoordinatorTest {
    @Test
    fun `preview beats detected app then returns to detected app`() {
        val preview = ProfileScope.Global
        val detected = "org.game"

        assertEquals(ProfileTarget.Preview(preview), resolveProfileTarget(preview, detected))
        assertEquals(ProfileTarget.ForegroundApp(detected), resolveProfileTarget(null, detected))
    }

    @Test
    fun `authoritative app on another display beats preview`() {
        assertEquals(
            ProfileTarget.ForegroundApp("app.gamenative"),
            resolveProfileTarget(
                preview = ProfileScope.Global,
                foregroundPackage = "app.gamenative",
                foregroundOverridesPreview = true,
            ),
        )
    }

    @Test
    fun `no foreground package resolves global`() {
        assertEquals(ProfileTarget.Global, resolveProfileTarget(null, null))
    }

    @Test
    fun `profile application keeps the static frame as the solid source and degrades gradients`() {
        val red = RgbColor(255, 0, 0)
        val green = RgbColor(0, 255, 0)
        val profile =
            LightingProfile(
                mode = AppMode.GRADIENT,
                solidColor = RgbColor(1, 2, 3),
                staticColors = listOf(red, red),
                gradientStops = listOf(red, green),
                speed = 10,
            )

        val degraded = profileApplication(profile, zones = 2, gradientSupported = false)
        val gradient = profileApplication(profile, zones = 2, gradientSupported = true)

        assertEquals(AppMode.COLOR, degraded.mode)
        assertEquals(listOf(red, red), degraded.staticColors)
        assertEquals(AppMode.GRADIENT, gradient.mode)
        assertEquals(listOf(red, green), gradient.staticColors)
        assertEquals(red, LightingIntent().applying(gradient).solidColor)
        assertEquals(10, LightingIntent().applying(gradient).speed)
    }
}
