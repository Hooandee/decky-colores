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

    @Test
    fun `a second launcher copy on top of the task is recognised as duplicate`() {
        assertTrue(isDuplicateLauncherEntry(taskRoot = false, action = "android.intent.action.MAIN", launcherCategory = true))
        assertFalse(isDuplicateLauncherEntry(taskRoot = true, action = "android.intent.action.MAIN", launcherCategory = true))
        assertFalse(isDuplicateLauncherEntry(taskRoot = false, action = null, launcherCategory = false))
    }
}
