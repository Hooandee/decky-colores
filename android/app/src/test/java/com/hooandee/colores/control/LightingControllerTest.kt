package com.hooandee.colores.control

import com.hooandee.colores.ambient.AmbientCaptureStatus
import com.hooandee.colores.ambient.AmbientFrameSource
import com.hooandee.colores.ambient.MutableAmbientFrameSource
import com.hooandee.colores.audio.AudioCaptureStatus
import com.hooandee.colores.audio.AudioLevelSource
import com.hooandee.colores.audio.MutableAudioLevelSource
import com.hooandee.colores.engine.BandSet
import com.hooandee.colores.engine.AudioScale
import com.hooandee.colores.engine.EffectCatalog
import com.hooandee.colores.engine.SensorBand
import com.hooandee.colores.engine.SensorKind
import com.hooandee.colores.gradient.GradientPresentation
import com.hooandee.colores.led.HardwareEffect
import com.hooandee.colores.led.LedDevice
import com.hooandee.colores.led.LedState
import com.hooandee.colores.led.RgbColor
import com.hooandee.colores.sensor.BatteryReading
import com.hooandee.colores.sensor.BatterySource
import com.hooandee.colores.sensor.PerformanceMetric
import com.hooandee.colores.sensor.PerformanceSource
import com.hooandee.colores.sensor.TemperatureSource
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LightingControllerTest {
    private val catalog = EffectCatalog.parse(File("../../shared/effects.json").readText())
    private val bands = BandSet.parse(File("../../shared/bands.json").readText())

    private class FakeDevice(
        override val recommendedFrameIntervalMs: Long = 80,
        override val supportsPerZone: Boolean = true,
        override val hardwareEffects: List<HardwareEffect> = emptyList(),
    ) : LedDevice {
        data class Write(val colors: List<RgbColor>, val brightness: Int, val power: Boolean)

        val writes = mutableListOf<Write>()
        var invalidations = 0
        var hardwareEffectWrites = 0
        var closed = false

        @Volatile
        var failOnce = false

        override val available = true
        override suspend fun readState(): LedState = LedState(listOf(RgbColor(0, 0, 0)), 100, true)

        override suspend fun applyZones(
            colors: List<RgbColor>,
            brightness: Int,
            power: Boolean,
        ): Boolean {
            if (failOnce) {
                failOnce = false
                throw RuntimeException("boom")
            }
            writes.add(Write(colors, brightness, power))
            return true
        }

        override suspend fun applySolid(
            color: RgbColor,
            brightness: Int,
            power: Boolean,
        ): Boolean = applyZones(List(2) { color }, brightness, power)

        override suspend fun applyHardwareEffect(
            effectId: String,
            colors: List<RgbColor>,
            brightness: Int,
            speed: Int,
            power: Boolean,
        ): Boolean {
            hardwareEffectWrites++
            return true
        }

        override fun invalidate() {
            invalidations++
        }

        override suspend fun close() {
            closed = true
        }
    }

    private class FakeBattery(
        @Volatile var reading: BatteryReading,
    ) : BatterySource {
        override fun read(): BatteryReading = reading
    }

    private class FakeTemperature(
        @Volatile var celsius: Double?,
    ) : TemperatureSource {
        override val available: Boolean get() = celsius != null

        override fun readCelsius(): Double? = celsius
    }

    private class FakePerformance(
        @Volatile var value: Double?,
        override val metric: PerformanceMetric = PerformanceMetric.CPU,
    ) : PerformanceSource {
        override val available: Boolean get() = value != null

        override fun read(): Double? = value
    }

    private class RecordingGate : ServiceGate {
        var starts = 0
        var stops = 0
        var running = false

        override fun start() {
            starts++
            running = true
        }

        override fun stop() {
            stops++
            running = false
        }
    }

    private fun binding(
        device: LedDevice,
        battery: BatterySource = FakeBattery(BatteryReading(80, charging = true, present = true)),
        temperature: TemperatureSource? = null,
        performance: PerformanceSource? = null,
        zones: Int = 2,
        audio: AudioLevelSource = MutableAudioLevelSource(),
        ambient: AmbientFrameSource = MutableAmbientFrameSource(),
    ) = LightingBinding("dev", device, zones, catalog, bands, battery, temperature, performance, audio, ambient)

    @Test
    fun `ambient capture and led writes run at independent cadences`() =
        runTest {
            val device = FakeDevice(recommendedFrameIntervalMs = 80)
            val gate = RecordingGate()
            val ambient = MutableAmbientFrameSource().apply {
                update(List(8) { RgbColor(it * 20, 10, 5) }, AmbientCaptureStatus.CAPTURING, 1L)
            }
            val controller = LightingController(backgroundScope, gate, clockMs = { testScheduler.currentTime })
            controller.bind(
                binding(device, zones = 8, ambient = ambient),
                LightingIntent(mode = AppMode.AMBIENT, ambientSmoothing = 0, ambientVividness = 0),
            )

            advanceTimeBy(1_000)
            runCurrent()

            assertTrue(gate.running)
            assertTrue(device.writes.size in 10..14)
            assertEquals(ambient.state.value.colors, device.writes.last().colors)
            assertEquals(AmbientCaptureStatus.CAPTURING, controller.snapshot.value.ambient.status)
        }

    @Test
    fun `audio mode waits black without authorization and stops when leaving the mode`() =
        runTest {
            val device = FakeDevice(recommendedFrameIntervalMs = 80)
            val gate = RecordingGate()
            val audio = MutableAudioLevelSource()
            val controller = LightingController(backgroundScope, gate, clockMs = { testScheduler.currentTime })
            controller.bind(binding(device, zones = 8, audio = audio), LightingIntent(mode = AppMode.AUDIO))
            advanceTimeBy(100)
            runCurrent()

            assertFalse(gate.running)
            assertTrue(device.writes.last().colors.all { it == RgbColor(0, 0, 0) })

            audio.update(0.0, AudioCaptureStatus.STARTING)
            controller.setMode(AppMode.AUDIO)
            advanceTimeBy(100)
            runCurrent()
            assertTrue(gate.running)

            audio.update(0.1, AudioCaptureStatus.CAPTURING)
            advanceTimeBy(100)
            runCurrent()
            assertTrue(device.writes.last().colors[3].green > 0)
            assertEquals(AudioCaptureStatus.CAPTURING, controller.snapshot.value.audio.status)

            controller.setMode(AppMode.COLOR)
            advanceTimeBy(100)
            runCurrent()
            val afterSwitch = device.writes.size
            advanceTimeBy(500)
            runCurrent()
            assertEquals(afterSwitch, device.writes.size)
            assertFalse(gate.running)
        }

    @Test
    fun `late audio stop reconciliation preserves the mode selected by the UI`() =
        runTest {
            val red = RgbColor(255, 0, 0)
            val device = FakeDevice(recommendedFrameIntervalMs = 80)
            val gate = RecordingGate()
            val audio = MutableAudioLevelSource().apply { update(0.4, AudioCaptureStatus.CAPTURING) }
            val controller = LightingController(backgroundScope, gate, clockMs = { testScheduler.currentTime })
            controller.bind(
                binding(device, zones = 8, audio = audio),
                LightingIntent(mode = AppMode.AUDIO, staticColors = List(8) { red }),
            )
            advanceTimeBy(100)
            runCurrent()

            controller.setMode(AppMode.COLOR)
            audio.reset(AudioCaptureStatus.AUTHORIZATION_REQUIRED)
            controller.onAudioStateChanged()
            advanceTimeBy(100)
            runCurrent()

            assertEquals(AppMode.COLOR, controller.snapshot.value.mode)
            assertEquals(List(8) { red }, device.writes.last().colors)
            assertFalse(gate.running)
        }

    @Test
    fun `explicit power off stops an active audio service and render loop`() =
        runTest {
            val device = FakeDevice(recommendedFrameIntervalMs = 80)
            val gate = RecordingGate()
            val audio = MutableAudioLevelSource().apply { update(0.5, AudioCaptureStatus.CAPTURING) }
            val controller = LightingController(backgroundScope, gate, clockMs = { testScheduler.currentTime })
            controller.bind(binding(device, zones = 8, audio = audio), LightingIntent(mode = AppMode.AUDIO))
            advanceTimeBy(100)
            runCurrent()
            assertTrue(gate.running)

            controller.setPower(false)
            advanceTimeBy(100)
            runCurrent()
            val writesAfterPowerOff = device.writes.size

            assertFalse(gate.running)
            assertFalse(device.writes.last().power)
            advanceTimeBy(500)
            runCurrent()
            assertEquals(writesAfterPowerOff, device.writes.size)
        }

    @Test
    fun `audio scale changes the next frame without rebinding capture`() =
        runTest {
            val device = FakeDevice(recommendedFrameIntervalMs = 80)
            val audio = MutableAudioLevelSource().apply { update(0.1, AudioCaptureStatus.CAPTURING) }
            val controller = LightingController(backgroundScope, RecordingGate(), clockMs = { testScheduler.currentTime })
            controller.bind(binding(device, zones = 8, audio = audio), LightingIntent(mode = AppMode.AUDIO))
            advanceTimeBy(100)
            runCurrent()
            assertTrue(device.writes.last().colors[3].green > 0)

            val blue = RgbColor(0, 80, 255)
            val custom = AudioScale(blue, blue, blue, mediumAt = 40, peakAt = 80)
            controller.setAudioScale(custom)
            advanceTimeBy(100)
            runCurrent()

            assertEquals(RgbColor(0, 32, 102), device.writes.last().colors[3])
            assertEquals(custom, controller.snapshot.value.audioScale)
            assertEquals("dev", controller.snapshot.value.deviceId)
        }

    @Test
    fun `audio sensitivity changes the next frame without rebinding capture`() =
        runTest {
            val device = FakeDevice(recommendedFrameIntervalMs = 80)
            val audio = MutableAudioLevelSource().apply { update(0.1, AudioCaptureStatus.CAPTURING) }
            val controller = LightingController(backgroundScope, RecordingGate(), clockMs = { testScheduler.currentTime })
            controller.bind(binding(device, zones = 8, audio = audio), LightingIntent(mode = AppMode.AUDIO))
            advanceTimeBy(100)
            runCurrent()
            val normalFrame = device.writes.last().colors

            controller.setAudioSensitivity(12)
            advanceTimeBy(100)
            runCurrent()
            val sensitiveFrame = device.writes.last().colors

            assertTrue(sensitiveFrame.sumOf { it.red + it.green + it.blue } > normalFrame.sumOf { it.red + it.green + it.blue })
            assertEquals(12, controller.snapshot.value.audioSensitivityDb)
            assertEquals("dev", controller.snapshot.value.deviceId)
        }

    @Test
    fun `unbinding stops a dynamic service`() =
        runTest {
            val gate = RecordingGate()
            val controller = LightingController(backgroundScope, gate, clockMs = { testScheduler.currentTime })
            controller.bind(binding(FakeDevice()), LightingIntent(mode = AppMode.CLOCK))
            runCurrent()
            assertTrue(gate.running)

            controller.unbind()
            runCurrent()

            assertFalse(gate.running)
            assertFalse(controller.snapshot.value.bound)
        }

    @Test
    fun `awaited unbind releases the device before returning`() =
        runTest {
            val device = FakeDevice()
            val controller = LightingController(backgroundScope)
            controller.bind(binding(device), LightingIntent())
            runCurrent()

            controller.unbindAndAwait()

            assertFalse(controller.snapshot.value.bound)
            assertNull(controller.boundDevice("dev"))
            assertTrue(device.closed)
        }

    @Test
    fun `static color applies once without a render loop or service`() =
        runTest {
            val device = FakeDevice()
            val gate = RecordingGate()
            val controller = LightingController(backgroundScope, gate, clockMs = { testScheduler.currentTime })
            controller.bind(binding(device), LightingIntent(mode = AppMode.COLOR, staticColors = listOf(RgbColor(10, 20, 30), RgbColor(10, 20, 30))))
            controller.setStaticFrame(listOf(RgbColor(40, 50, 60), RgbColor(40, 50, 60)))
            advanceTimeBy(1000)
            runCurrent()

            assertTrue(device.writes.isNotEmpty())
            val last = device.writes.last()
            assertEquals(RgbColor(40, 50, 60), last.colors.first())
            assertTrue(last.power)
            assertFalse(gate.running)
        }

    @Test
    fun `dynamic effect paces writes and switching mode stops the effect loop`() =
        runTest {
            val device = FakeDevice()
            val gate = RecordingGate()
            val controller = LightingController(backgroundScope, gate, clockMs = { testScheduler.currentTime })
            controller.bind(binding(device), LightingIntent(mode = AppMode.EFFECT, effectId = "rainbow"))
            advanceTimeBy(1000)
            runCurrent()
            val duringEffect = device.writes.size
            assertTrue("effect should tick roughly every 80ms", duringEffect in 6..16)
            assertTrue(gate.running)

            controller.setMode(AppMode.COLOR)
            advanceTimeBy(50)
            runCurrent()
            val afterSwitch = device.writes.size
            advanceTimeBy(2000)
            runCurrent()
            assertEquals("no late effect frames after switching to a static mode", afterSwitch, device.writes.size)
            assertFalse(gate.running)
        }

    @Test
    fun `spatial gradient remains a single static per-zone write`() =
        runTest {
            val red = RgbColor(255, 0, 0)
            val blue = RgbColor(0, 0, 255)
            val device = FakeDevice(supportsPerZone = true)
            val gate = RecordingGate()
            val controller = LightingController(backgroundScope, gate, clockMs = { testScheduler.currentTime })

            controller.bind(
                binding(device, zones = 2),
                LightingIntent(
                    mode = AppMode.GRADIENT,
                    staticColors = listOf(red, blue),
                    gradientStops = listOf(red, blue),
                    gradientSpeed = 0,
                ),
            )
            advanceTimeBy(1_000)
            runCurrent()

            assertEquals(1, device.writes.size)
            assertEquals(listOf(red, blue), device.writes.single().colors)
            assertFalse(gate.running)
        }

    @Test
    fun `single-color gradient crossfades through the palette at device cadence`() =
        runTest {
            val red = RgbColor(255, 0, 0)
            val blue = RgbColor(0, 0, 255)
            val device = FakeDevice(recommendedFrameIntervalMs = 80, supportsPerZone = false)
            val gate = RecordingGate()
            val controller = LightingController(backgroundScope, gate, clockMs = { testScheduler.currentTime })

            controller.bind(
                binding(device, zones = 1),
                LightingIntent(
                    mode = AppMode.GRADIENT,
                    staticColors = listOf(red),
                    gradientStops = listOf(red, blue),
                    gradientSpeed = 0,
                ),
            )
            advanceTimeBy(5_100)
            runCurrent()

            assertTrue(gate.running)
            assertTrue(device.writes.size in 55..70)
            assertTrue(device.writes.minOf { it.colors.single().blue } < 10)
            assertTrue(device.writes.maxOf { it.colors.single().blue } > 240)
        }

    @Test
    fun `detected animated presentation overrides an optimistic per-zone transport`() =
        runTest {
            val red = RgbColor(255, 0, 0)
            val blue = RgbColor(0, 0, 255)
            val device = FakeDevice(supportsPerZone = true)
            val gate = RecordingGate()
            val controller = LightingController(backgroundScope, gate, clockMs = { testScheduler.currentTime })

            controller.bind(
                binding(device, zones = 1),
                LightingIntent(
                    mode = AppMode.GRADIENT,
                    staticColors = listOf(red),
                    gradientStops = listOf(red, blue),
                    gradientPresentation = GradientPresentation.ANIMATED,
                ),
            )
            advanceTimeBy(250)
            runCurrent()

            assertTrue(gate.running)
            assertTrue(device.writes.size >= 2)
        }

    @Test
    fun `editing a gradient effect freezes live changes and resumes afterward`() =
        runTest {
            val red = RgbColor(255, 0, 0)
            val blue = RgbColor(0, 0, 255)
            val green = RgbColor(0, 255, 0)
            val device = FakeDevice(recommendedFrameIntervalMs = 80, supportsPerZone = false)
            val gate = RecordingGate()
            val controller = LightingController(backgroundScope, gate, clockMs = { testScheduler.currentTime })

            controller.bind(
                binding(device, zones = 1),
                LightingIntent(
                    mode = AppMode.EFFECT,
                    effectId = "wave",
                    staticColors = listOf(red),
                    gradientStops = listOf(red, blue),
                    gradientPresentation = GradientPresentation.ANIMATED,
                    gradientSpeed = 70,
                ),
            )
            advanceTimeBy(240)
            runCurrent()
            assertTrue(gate.running)

            controller.setGradientEditing(true)
            advanceTimeBy(100)
            runCurrent()
            val writesWhenFrozen = device.writes.size
            advanceTimeBy(800)
            runCurrent()

            assertEquals(writesWhenFrozen, device.writes.size)
            assertFalse(gate.running)

            controller.setGradientEditingPreview(green)
            controller.setPaletteSources(red, listOf(red, green))
            advanceTimeBy(100)
            runCurrent()
            assertEquals(listOf(green), device.writes.last().colors)
            assertFalse(gate.running)

            controller.setGradientEditing(false)
            advanceTimeBy(240)
            runCurrent()

            assertTrue(device.writes.size > writesWhenFrozen + 1)
            assertTrue(gate.running)
            assertEquals(70, controller.snapshot.value.gradientSpeed)
        }

    @Test
    fun `rebinding while editing cannot leave the next device frozen`() =
        runTest {
            val red = RgbColor(255, 0, 0)
            val blue = RgbColor(0, 0, 255)
            val first = FakeDevice(recommendedFrameIntervalMs = 80, supportsPerZone = false)
            val second = FakeDevice(recommendedFrameIntervalMs = 80, supportsPerZone = false)
            val gate = RecordingGate()
            val controller = LightingController(backgroundScope, gate, clockMs = { testScheduler.currentTime })
            val intent =
                LightingIntent(
                    mode = AppMode.GRADIENT,
                    gradientStops = listOf(red, blue),
                    gradientPresentation = GradientPresentation.ANIMATED,
                )

            controller.bind(binding(first, zones = 1), intent)
            controller.setGradientEditing(true)
            advanceTimeBy(160)
            runCurrent()
            assertFalse(gate.running)

            controller.bind(binding(second, zones = 1), intent)
            advanceTimeBy(240)
            runCurrent()

            assertTrue(gate.running)
            assertTrue(second.writes.size >= 2)
        }

    @Test
    fun `gradient overlay uses software while the same effect otherwise stays hardware`() =
        runTest {
            val red = RgbColor(255, 0, 0)
            val blue = RgbColor(0, 0, 255)
            val hardware = listOf(HardwareEffect("breathing", colorStops = 1, defaultSpeed = 50, colors = listOf(red)))

            val nativeDevice = FakeDevice(hardwareEffects = hardware)
            val nativeController = LightingController(backgroundScope, RecordingGate(), clockMs = { testScheduler.currentTime })
            nativeController.bind(
                binding(nativeDevice, zones = 2),
                LightingIntent(mode = AppMode.EFFECT, effectId = "breathing", effectUsesGradient = false),
            )
            runCurrent()
            assertEquals(1, nativeDevice.hardwareEffectWrites)

            val softwareDevice = FakeDevice(hardwareEffects = hardware)
            val softwareController = LightingController(backgroundScope, RecordingGate(), clockMs = { testScheduler.currentTime })
            softwareController.bind(
                binding(softwareDevice, zones = 2),
                LightingIntent(
                    mode = AppMode.EFFECT,
                    effectId = "breathing",
                    gradientStops = listOf(red, blue),
                    effectUsesGradient = true,
                ),
            )
            advanceTimeBy(200)
            runCurrent()

            assertEquals(0, softwareDevice.hardwareEffectWrites)
            assertTrue(softwareDevice.writes.isNotEmpty())
            val colors = softwareDevice.writes.last().colors
            assertTrue(colors.first().red > 0 && colors.first().blue == 0)
            assertTrue(colors.last().blue > 0 && colors.last().red == 0)
        }

    @Test
    fun `charger only gate powers off on unplug and restores intent on replug`() =
        runTest {
            val device = FakeDevice()
            val battery = FakeBattery(BatteryReading(60, charging = true, present = true))
            val controller = LightingController(backgroundScope, RecordingGate(), clockMs = { testScheduler.currentTime })
            controller.bind(
                binding(device, battery = battery),
                LightingIntent(mode = AppMode.COLOR, staticColors = listOf(RgbColor(9, 9, 9), RgbColor(9, 9, 9)), chargerOnly = true),
            )
            advanceTimeBy(100)
            runCurrent()
            assertTrue("charging -> effective power on", device.writes.last().power)

            battery.reading = BatteryReading(60, charging = false, present = true)
            advanceTimeBy(3_100)
            runCurrent()
            assertFalse("unplug -> LEDs off", device.writes.last().power)
            assertEquals("mode preserved while gated off", AppMode.COLOR, controller.snapshot.value.mode)

            battery.reading = BatteryReading(60, charging = true, present = true)
            advanceTimeBy(3_100)
            runCurrent()
            val restored = device.writes.last()
            assertTrue("replug -> restored", restored.power)
            assertEquals(RgbColor(9, 9, 9), restored.colors.first())
        }

    @Test
    fun `battery mode colours a real zero percent level`() =
        runTest {
            val device = FakeDevice()
            val battery = FakeBattery(BatteryReading(0, charging = false, present = true))
            val controller = LightingController(backgroundScope, RecordingGate(), clockMs = { testScheduler.currentTime })
            controller.bind(binding(device, battery = battery), LightingIntent(mode = AppMode.BATTERY))
            advanceTimeBy(300)
            runCurrent()
            assertEquals(RgbColor(255, 30, 20), device.writes.last().colors.first())
        }

    @Test
    fun `battery mode consumes a custom scale without rebinding the device`() =
        runTest {
            val device = FakeDevice()
            val battery = FakeBattery(BatteryReading(70, charging = false, present = true))
            val controller = LightingController(backgroundScope, RecordingGate(), clockMs = { testScheduler.currentTime })
            controller.bind(binding(device, battery = battery), LightingIntent(mode = AppMode.BATTERY))
            advanceTimeBy(300)
            runCurrent()
            assertEquals(RgbColor(0, 200, 60), device.writes.last().colors.first())

            val custom =
                bands.replace(
                    SensorKind.BATTERY,
                    listOf(
                        SensorBand(60.0, RgbColor(4, 5, 6)),
                        SensorBand(40.0, RgbColor(7, 8, 9)),
                        SensorBand(25.0, RgbColor(10, 11, 12)),
                        SensorBand(10.0, RgbColor(13, 14, 15)),
                        SensorBand(0.0, RgbColor(16, 17, 18)),
                    ),
                )!!
            controller.setSensorBands(custom)
            advanceTimeBy(8_000)
            runCurrent()

            assertEquals(RgbColor(4, 5, 6), device.writes.last().colors.first())
            assertEquals("dev", controller.snapshot.value.deviceId)
        }

    @Test
    fun `temperature mode holds and reports unavailable when there is no source`() =
        runTest {
            val device = FakeDevice()
            val controller = LightingController(backgroundScope, RecordingGate(), clockMs = { testScheduler.currentTime })
            controller.bind(binding(device, temperature = FakeTemperature(null)), LightingIntent(mode = AppMode.TEMPERATURE))
            advanceTimeBy(500)
            runCurrent()
            assertFalse(controller.snapshot.value.temperatureAvailable)
            assertTrue("holds a safe frame, never a fake temperature", device.writes.all { it.colors.all { c -> c == RgbColor(0, 0, 0) } })
        }

    @Test
    fun `performance mode reports its metric label`() =
        runTest {
            val device = FakeDevice()
            val controller = LightingController(backgroundScope, RecordingGate(), clockMs = { testScheduler.currentTime })
            controller.bind(
                binding(device, performance = FakePerformance(75.0, PerformanceMetric.GPU)),
                LightingIntent(mode = AppMode.PERFORMANCE),
            )
            advanceTimeBy(500)
            runCurrent()
            assertEquals(PerformanceMetric.GPU, controller.snapshot.value.performanceMetric)
        }

    @Test
    fun `a write failure does not crash or stall the render loop`() =
        runTest {
            val device = FakeDevice()
            val controller = LightingController(backgroundScope, RecordingGate(), clockMs = { testScheduler.currentTime })
            controller.bind(binding(device), LightingIntent(mode = AppMode.EFFECT, effectId = "rainbow"))
            device.failOnce = true
            advanceTimeBy(1000)
            runCurrent()
            assertTrue("loop keeps running after a failed write", device.writes.size > 3)
        }

    @Test
    fun `commands without a binding are ignored safely`() =
        runTest {
            val controller = LightingController(backgroundScope, RecordingGate(), clockMs = { testScheduler.currentTime })
            controller.setMode(AppMode.EFFECT)
            controller.setBrightness(50)
            advanceTimeBy(500)
            runCurrent()
            assertFalse(controller.snapshot.value.bound)
            assertNull(controller.snapshot.value.batteryLevelPercent)
        }

    @Test
    fun `reassert invalidates the device and resends a static frame`() =
        runTest {
            val device = FakeDevice()
            val controller = LightingController(backgroundScope, RecordingGate(), clockMs = { testScheduler.currentTime })
            controller.bind(binding(device), LightingIntent(mode = AppMode.COLOR, staticColors = listOf(RgbColor(7, 7, 7), RgbColor(7, 7, 7))))
            advanceTimeBy(100)
            runCurrent()
            val before = device.writes.size

            controller.reassert()
            advanceTimeBy(100)
            runCurrent()
            assertTrue(device.invalidations >= 1)
            assertTrue("static frame resent after reassert", device.writes.size > before)
            assertEquals(RgbColor(7, 7, 7), device.writes.last().colors.first())
        }

    @Test
    fun `unbinding stops all writes`() =
        runTest {
            val device = FakeDevice()
            val gate = RecordingGate()
            val controller = LightingController(backgroundScope, gate, clockMs = { testScheduler.currentTime })
            controller.bind(binding(device), LightingIntent(mode = AppMode.EFFECT, effectId = "rainbow"))
            advanceTimeBy(300)
            runCurrent()
            controller.unbind()
            advanceTimeBy(50)
            runCurrent()
            val settled = device.writes.size
            advanceTimeBy(2000)
            runCurrent()
            assertEquals(settled, device.writes.size)
            assertFalse(controller.snapshot.value.bound)
            assertFalse(gate.running)
        }
}
