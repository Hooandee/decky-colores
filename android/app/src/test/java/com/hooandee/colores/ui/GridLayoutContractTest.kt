package com.hooandee.colores.ui

import com.hooandee.colores.device.AndroidDeviceIdentity
import com.hooandee.colores.device.DeviceRegistry
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class GridLayoutContractTest {
    private val registry =
        DeviceRegistry.parse(
            devicesJson = File("../../shared/devices.json").readText(),
            previewProfilesJson = File("../../shared/led-preview-profiles.json").readText(),
        )

    @Test
    fun `RP5 shared grid layout reproduces the calibrated cardinal grid`() {
        val match =
            requireNotNull(
                registry.match(AndroidDeviceIdentity("Retroid Pocket 5", "kona", "Moorechip", emptyMap())),
            )
        assertNotNull(match.gridLayout)

        val zones = gradientEditorZones(match.gridLayout, match.capabilities.zones)

        assertEquals(List(4) { 0 } + List(4) { 1 }, zones.map { it.stick })
        assertEquals(
            listOf(
                GradientZonePosition.TOP,
                GradientZonePosition.LEFT,
                GradientZonePosition.BOTTOM,
                GradientZonePosition.RIGHT,
            ).let { it + it },
            zones.map { it.position },
        )
    }

    @Test
    fun `AYN Thor shared grid layout reproduces the calibrated mirrored grid`() {
        val match =
            requireNotNull(
                registry.match(AndroidDeviceIdentity("AYN Thor", "kalama", "AYN", emptyMap())),
            )
        assertNotNull(match.gridLayout)

        val zones = gradientEditorZones(match.gridLayout, match.capabilities.zones)

        assertEquals(List(4) { 0 } + List(4) { 1 }, zones.map { it.stick })
        assertEquals(GradientZonePosition.TOP_LEFT, zones[0].position)
        assertEquals(GradientZonePosition.BOTTOM_LEFT, zones[1].position)
        assertEquals(GradientZonePosition.BOTTOM_LEFT, zones[4].position)
        assertEquals(GradientZonePosition.TOP_LEFT, zones[5].position)
    }
}
