package com.hooandee.colores.profiles

import org.junit.Assert.assertEquals
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
}
