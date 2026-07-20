package com.hooandee.colores.ui

import com.hooandee.colores.led.LedState
import com.hooandee.colores.led.RgbColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DashboardModelTest {
    private val red = RgbColor(255, 0, 0)
    private val blue = RgbColor(0, 0, 255)
    private val green = RgbColor(0, 255, 0)
    private val mixed = LedState(listOf(red, blue), brightness = 70, power = true)

    @Test
    fun `reading a target does not alter LED state`() {
        val before = mixed.copy()

        EditTarget.entries.forEach(mixed::colorForEditing)

        assertEquals(before, mixed)
        assertTrue(mixed.hasMixedColors)
        assertEquals(red, mixed.colorForEditing(EditTarget.BOTH))
        assertEquals(red, mixed.colorForEditing(EditTarget.LEFT))
        assertEquals(blue, mixed.colorForEditing(EditTarget.RIGHT))
    }

    @Test
    fun `both unifies zones only when a color edit occurs`() {
        val changed = mixed.withTargetColor(EditTarget.BOTH, green)

        assertEquals(listOf(green, green), changed.zoneColors)
        assertFalse(changed.hasMixedColors)
    }

    @Test
    fun `single target preserves the opposite zone`() {
        assertEquals(listOf(green, blue), mixed.withTargetColor(EditTarget.LEFT, green).zoneColors)
        assertEquals(listOf(red, green), mixed.withTargetColor(EditTarget.RIGHT, green).zoneColors)
    }

    @Test
    fun `RGB HSV conversion preserves primary colors`() {
        val hsv = red.toHsvColor()

        assertEquals(0f, hsv.hue, 0.01f)
        assertEquals(1f, hsv.saturation, 0.01f)
        assertEquals(1f, hsv.value, 0.01f)
        assertEquals(red, hsv.toRgbColor())
    }

    @Test
    fun `saturation change keeps hue and value`() {
        val changed = red.toHsvColor().copy(saturation = 0.5f).toRgbColor().toHsvColor()

        assertEquals(0f, changed.hue, 0.01f)
        assertEquals(0.5f, changed.saturation, 0.02f)
        assertEquals(1f, changed.value, 0.01f)
    }

    @Test
    fun `direction keys adjust hue and saturation in bounded steps`() {
        val start = HsvColor(hue = 359f, saturation = 0.98f, value = 1f)

        assertEquals(2f, start.adjustForDirection(ColorDirection.RIGHT).hue, 0.01f)
        assertEquals(356f, start.adjustForDirection(ColorDirection.LEFT).hue, 0.01f)
        assertEquals(1f, start.adjustForDirection(ColorDirection.UP).saturation, 0.01f)
        assertEquals(0.95f, start.adjustForDirection(ColorDirection.DOWN).saturation, 0.01f)
    }

    @Test
    fun `saturation edit on both uses left color then unifies zones`() {
        val edited = mixed.withTargetSaturation(EditTarget.BOTH, 0.25f)

        assertEquals(2, edited.zoneColors.size)
        assertEquals(edited.zoneColors[0], edited.zoneColors[1])
        assertEquals(0.25f, edited.zoneColors[0].toHsvColor().saturation, 0.02f)
    }

    @Test
    fun `right saturation edit preserves left zone`() {
        val edited = mixed.withTargetSaturation(EditTarget.RIGHT, 0.4f)

        assertEquals(red, edited.zoneColors[0])
        assertEquals(0.4f, edited.zoneColors[1].toHsvColor().saturation, 0.02f)
    }

    @Test
    fun `hex color is uppercase and channel padded`() {
        assertEquals("#01020F", RgbColor(1, 2, 15).toHexString())
    }

    @Test
    fun `gradient preview uses first and last colors on devices with many zones`() {
        val colors = listOf(red, green, RgbColor(255, 255, 0), blue)

        assertEquals(red to blue, LedState(colors, 100, true).previewEndpointColors(gradientMode = true))
        assertEquals(red to RgbColor(255, 255, 0), LedState(colors, 100, true).previewEndpointColors(gradientMode = false))
    }

    @Test
    fun `eight-zone solid preview uses the first zone of each stick`() {
        val colors = List(4) { red } + List(4) { blue }

        assertEquals(red to blue, LedState(colors, 100, true).previewEndpointColors(gradientMode = false))
    }

    @Test
    fun `eight-zone solid targets read and update whole physical sticks`() {
        val state = LedState(List(4) { red } + List(4) { blue }, brightness = 80, power = true)

        assertEquals(red, state.colorForEditing(EditTarget.LEFT))
        assertEquals(blue, state.colorForEditing(EditTarget.RIGHT))
        assertEquals(List(4) { green } + List(4) { blue }, state.withTargetColor(EditTarget.LEFT, green).zoneColors)
        assertEquals(List(4) { red } + List(4) { green }, state.withTargetColor(EditTarget.RIGHT, green).zoneColors)
    }

    @Test
    fun `eight-zone right saturation preserves every left-stick zone`() {
        val state = LedState(List(4) { red } + List(4) { blue }, brightness = 80, power = true)

        val changed = state.withTargetSaturation(EditTarget.RIGHT, 0.4f)

        assertEquals(List(4) { red }, changed.zoneColors.take(4))
        assertTrue(changed.zoneColors.drop(4).all { kotlin.math.abs(it.toHsvColor().saturation - 0.4f) < 0.02f })
    }
}
