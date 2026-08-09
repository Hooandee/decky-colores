package com.hooandee.colores.control

import com.hooandee.colores.ambient.AmbientSamplingMode
import com.hooandee.colores.engine.BandSet
import com.hooandee.colores.engine.AudioScale
import com.hooandee.colores.engine.SensorBand
import com.hooandee.colores.led.RgbColor
import org.junit.Assert.assertEquals
import org.junit.Test

class LightingPreferencesTest {
    private fun preferences(): Pair<LightingPreferences, MutableMap<String, String>> {
        val store = mutableMapOf<String, String>()
        return LightingPreferences(read = { store[it] }, write = { key, value -> store[key] = value }) to store
    }

    @Test
    fun `round trips the full lighting intent per device`() {
        val (prefs, _) = preferences()
        val stored =
            StoredLighting(
                mode = AppMode.PERFORMANCE,
                effectId = "wave",
                speed = 73,
                gradientSpeed = 37,
                effectUsesGradient = true,
                solidColor = RgbColor(12, 34, 56),
                brightness = 64,
                power = false,
                chargerOnly = true,
                batteryBreathe = false,
                audioScale =
                    AudioScale(
                        lowColor = RgbColor(1, 2, 3),
                        mediumColor = RgbColor(4, 5, 6),
                        peakColor = RgbColor(7, 8, 9),
                        mediumAt = 35,
                        peakAt = 76,
                    ),
                audioSensitivityDb = -7,
                ambientCaptureFps = 30,
                ambientSamplingMode = AmbientSamplingMode.BOTTOM_EDGE,
                ambientVividness = 72,
                ambientSmoothing = 18,
            )
        prefs.save("rp5", stored)
        assertEquals(stored, prefs.load("rp5"))
        assertEquals("rp5", prefs.activeDeviceId())
    }

    @Test
    fun `an unseen device restores defaults`() {
        val (prefs, _) = preferences()
        assertEquals(StoredLighting(), prefs.load("unknown"))
    }

    @Test
    fun `a dynamic mode is restored, not collapsed to solid color`() {
        val (prefs, _) = preferences()
        prefs.save("rp5", StoredLighting(mode = AppMode.CLOCK, effectId = "spiral"))
        assertEquals(AppMode.CLOCK, prefs.load("rp5").mode)
        assertEquals("spiral", prefs.load("rp5").effectId)
    }

    @Test
    fun `audio mode round trips as user intent`() {
        val (prefs, _) = preferences()
        prefs.save("thor", StoredLighting(mode = AppMode.AUDIO))

        assertEquals(AppMode.AUDIO, prefs.load("thor").mode)
    }

    @Test
    fun `invalid ambient options are clamped and keep ambient intent`() {
        val raw = """{"mode":"AMBIENT","ambientCaptureFps":28,"ambientVividness":999,"ambientSmoothing":-4}"""
        val prefs = LightingPreferences(read = { raw }, write = { _, _ -> })

        val restored = prefs.load("thor")

        assertEquals(AppMode.AMBIENT, restored.mode)
        assertEquals(30, restored.ambientCaptureFps)
        assertEquals(100, restored.ambientVividness)
        assertEquals(0, restored.ambientSmoothing)
    }

    @Test
    fun `legacy lighting state leaves power and brightness unspecified`() {
        val prefs = LightingPreferences(read = { "{\"mode\":\"CLOCK\"}" }, write = { _, _ -> })

        val restored = prefs.load("thor")

        assertEquals(null, restored.power)
        assertEquals(null, restored.brightness)
        assertEquals(0, restored.audioSensitivityDb)
    }

    @Test
    fun `background restore respects explicit power off`() {
        val (prefs, _) = preferences()
        prefs.save("thor", StoredLighting(mode = AppMode.CLOCK, power = false))

        assertEquals(false, prefs.shouldRestoreInBackground())

        prefs.save("thor", StoredLighting(mode = AppMode.CLOCK, power = true))

        assertEquals(true, prefs.shouldRestoreInBackground())
    }

    @Test
    fun `audio and charger monitoring request a background restore`() {
        val (prefs, _) = preferences()
        prefs.save("thor", StoredLighting(mode = AppMode.AUDIO, power = true))
        assertEquals(true, prefs.shouldRestoreInBackground())

        prefs.save("thor", StoredLighting(mode = AppMode.COLOR, power = true, chargerOnly = true))
        assertEquals(true, prefs.shouldRestoreInBackground())
    }

    @Test
    fun `corrupt json degrades to defaults`() {
        val prefs = LightingPreferences(read = { "not json" }, write = { _, _ -> })
        assertEquals(StoredLighting(), prefs.load("rp5"))
    }

    @Test
    fun `devices keep independent lighting intents`() {
        val (prefs, _) = preferences()
        prefs.save("a", StoredLighting(mode = AppMode.EFFECT, effectId = "comet"))
        prefs.save("b", StoredLighting(mode = AppMode.BATTERY))
        assertEquals(AppMode.EFFECT, prefs.load("a").mode)
        assertEquals(AppMode.BATTERY, prefs.load("b").mode)
    }

    @Test
    fun `round trips custom sensor scales and temperature breathing per device`() {
        val (prefs, _) = preferences()
        val customBands =
            BandSet(
                battery = BandSet.FALLBACK.battery.mapIndexed { index, band -> band.copy(color = RgbColor(index, 10, 20)) },
                temperature =
                    listOf(
                        SensorBand(105.0, RgbColor(255, 0, 0)),
                        SensorBand(85.0, RgbColor(255, 80, 0)),
                        SensorBand(65.0, RgbColor(255, 200, 0)),
                        SensorBand(45.0, RgbColor(0, 200, 60)),
                        SensorBand(0.0, RgbColor(0, 120, 255)),
                    ),
            )
        val stored = StoredLighting(sensorBands = customBands, temperatureBreathe = false)

        prefs.save("thor", stored)

        assertEquals(stored, prefs.load("thor"))
    }

    @Test
    fun `an invalid saved sensor scale falls back without discarding other lighting settings`() {
        val defaults = BandSet.FALLBACK
        val raw =
            """{"mode":"TEMPERATURE","effectId":"wave","speed":71,"sensorBands":{"battery":[{"min":90,"color":{"r":1,"g":2,"b":3}}]}}"""
        val prefs = LightingPreferences(read = { raw }, write = { _, _ -> })

        val restored = prefs.load("thor", defaults)

        assertEquals(AppMode.TEMPERATURE, restored.mode)
        assertEquals("wave", restored.effectId)
        assertEquals(71, restored.speed)
        assertEquals(defaults, restored.sensorBands)
    }

    @Test
    fun `restored animation settings are clamped without losing their intent`() {
        val raw = """{"gradientSpeed":999,"effectUsesGradient":true}"""
        val prefs = LightingPreferences(read = { raw }, write = { _, _ -> })

        val restored = prefs.load("thor")

        assertEquals(100, restored.gradientSpeed)
        assertEquals(true, restored.effectUsesGradient)
    }

    @Test
    fun `invalid saved audio scale falls back without discarding lighting intent`() {
        val raw =
            """{"mode":"AUDIO","speed":71,"audioScale":{"low":{"r":1,"g":2,"b":3},"medium":{"r":4,"g":5,"b":6},"peak":{"r":7,"g":8,"b":9},"mediumAt":80,"peakAt":70}}"""
        val prefs = LightingPreferences(read = { raw }, write = { _, _ -> })

        val restored = prefs.load("thor")

        assertEquals(AppMode.AUDIO, restored.mode)
        assertEquals(71, restored.speed)
        assertEquals(AudioScale.DEFAULT, restored.audioScale)
    }

    @Test
    fun `saved audio sensitivity is clamped without discarding lighting intent`() {
        val raw = """{"mode":"AUDIO","speed":71,"audioSensitivityDb":99}"""
        val prefs = LightingPreferences(read = { raw }, write = { _, _ -> })

        val restored = prefs.load("thor")

        assertEquals(AppMode.AUDIO, restored.mode)
        assertEquals(71, restored.speed)
        assertEquals(12, restored.audioSensitivityDb)
    }
}
