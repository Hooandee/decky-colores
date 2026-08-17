package com.hooandee.colores

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectionCaptureScopeTest {
    @Test
    fun `ambilight captures the complete display on Android 14 and newer`() {
        assertTrue(shouldCaptureDefaultDisplay(ProjectionRequest.AMBIENT, sdk = 34))
    }

    @Test
    fun `ambilight keeps compatible user consent before Android 14`() {
        assertFalse(shouldCaptureDefaultDisplay(ProjectionRequest.AMBIENT, sdk = 33))
    }

    @Test
    fun `audio keeps user selected capture on Android 14`() {
        assertFalse(shouldCaptureDefaultDisplay(ProjectionRequest.AUDIO, sdk = 34))
    }
}
