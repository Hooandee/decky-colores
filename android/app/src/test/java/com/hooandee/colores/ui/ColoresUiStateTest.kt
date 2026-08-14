package com.hooandee.colores.ui

import com.hooandee.colores.ambient.AmbientCaptureStatus
import com.hooandee.colores.ambient.AmbientFrameState
import com.hooandee.colores.audio.AudioCaptureStatus
import com.hooandee.colores.audio.AudioLevelState
import com.hooandee.colores.control.AppMode
import com.hooandee.colores.device.AndroidDeviceIdentity
import com.hooandee.colores.device.DevicePresentation
import com.hooandee.colores.device.DevicePresentationSource
import com.hooandee.colores.device.DeviceRegistry
import com.hooandee.colores.engine.EffectNeed
import com.hooandee.colores.engine.EffectPreset
import com.hooandee.colores.gradient.GradientPresentation
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ColoresUiStateTest {
    private val breathing = EffectPreset("breathing", EffectNeed.COLOR, defaultSpeed = 50, colors = emptyList())
    private val thor =
        DeviceRegistry.parse(
            File("../../shared/devices.json").readText(),
            File("../../shared/led-preview-profiles.json").readText(),
        ).match(AndroidDeviceIdentity("AYN Thor", "kalama", "AYN", emptyMap()))!!

    @Test
    fun `gradient presentation derives a consistent availability state`() {
        val animated = ColoresUiState(gradientPresentation = GradientPresentation.ANIMATED)
        val unavailable = ColoresUiState()

        assertTrue(animated.gradientAvailable)
        assertTrue(animated.gradientAnimated)
        assertFalse(unavailable.gradientAvailable)
        assertFalse(unavailable.gradientAnimated)
    }

    @Test
    fun `software color effect can opt into the current gradient`() {
        val state =
            ColoresUiState(
                mode = AppMode.EFFECT,
                effects = listOf(breathing),
                effectId = "breathing",
                gradientPresentation = GradientPresentation.SPATIAL,
                effectUsesGradient = true,
                softwareEffectIds = setOf("breathing"),
            )

        assertTrue(state.canUseGradientForEffect)
        assertTrue(state.effectNeedsGradient)
        assertTrue(state.editingGradientStops)
    }

    @Test
    fun `hardware-only effect never advertises a software gradient overlay`() {
        val state =
            ColoresUiState(
                mode = AppMode.EFFECT,
                effects = listOf(breathing),
                effectId = "breathing",
                gradientPresentation = GradientPresentation.SPATIAL,
                effectUsesGradient = true,
                softwareEffectIds = emptySet(),
            )

        assertFalse(state.canUseGradientForEffect)
        assertFalse(state.effectNeedsGradient)
        assertFalse(state.editingGradientStops)
    }

    @Test
    fun `audio appears only when a color device is detected`() {
        val connected = ColoresUiState(detected = thor)
        val unavailable = ColoresUiState()

        assertTrue(AppMode.AUDIO in connected.availableModes())
        assertFalse(AppMode.AUDIO in unavailable.availableModes())
        assertTrue(AppMode.AMBIENT in connected.availableModes())
        assertFalse(AppMode.AMBIENT in unavailable.availableModes())
    }

    @Test
    fun `known identity presentation does not enable LED controls`() {
        val state =
            ColoresUiState(
                devicePresentation =
                    DevicePresentation(
                        id = "ayn-odin2-portal",
                        friendlyName = "AYN Odin 2 Portal",
                        source = DevicePresentationSource.KNOWN_IDENTITY,
                    ),
            )

        assertEquals("AYN Odin 2 Portal", state.devicePresentation.friendlyName)
        assertFalse(state.colorEnabled)
        assertFalse(state.brightnessEnabled)
        assertFalse(state.canWrite)
        assertFalse(AppMode.COLOR in state.availableModes())
    }

    @Test
    fun `selected audio without a live grant asks for authorization`() {
        val state =
            ColoresUiState(
                detected = thor,
                mode = AppMode.AUDIO,
                audio = AudioLevelState(status = AudioCaptureStatus.AUTHORIZATION_REQUIRED),
            )

        assertTrue(state.audioNeedsAuthorization)
        assertEquals(0.0, state.audio.level, 0.0)
    }

    @Test
    fun `selected ambient without a live grant asks for authorization`() {
        val state =
            ColoresUiState(
                detected = thor,
                mode = AppMode.AMBIENT,
                ambient = AmbientFrameState(status = AmbientCaptureStatus.REVOKED),
            )

        assertTrue(state.ambientNeedsAuthorization)
    }
}
