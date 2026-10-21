package com.hooandee.colores.control

import com.hooandee.colores.ambient.AmbientCaptureStatus
import com.hooandee.colores.ambient.AmbientColorMath
import com.hooandee.colores.ambient.AmbientFrameSource
import com.hooandee.colores.ambient.MutableAmbientFrameSource
import com.hooandee.colores.audio.AudioCaptureStatus
import com.hooandee.colores.audio.AudioLevelSource
import com.hooandee.colores.audio.AudioLevelState
import com.hooandee.colores.audio.MutableAudioLevelSource
import com.hooandee.colores.engine.BandSet
import com.hooandee.colores.engine.ColorCalibration
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
        override val softwareEffects: Boolean = true,
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
        ambientAudio: AudioLevelSource = MutableAudioLevelSource(),
    ) = LightingBinding("dev", device, zones, catalog, bands, battery, temperature, performance, audio, ambient, ambientAudio)

    @Test
    fun `effect mode falls back to color when the device offers no effects`() =
        runTest {
            val device = FakeDevice(softwareEffects = false)
            val gate = RecordingGate()
            val controller = LightingController(backgroundScope, gate, clockMs = { testScheduler.currentTime })

            controller.bind(binding(device), LightingIntent(mode = AppMode.EFFECT))
            advanceTimeBy(500)
            runCurrent()
            assertEquals(AppMode.COLOR, controller.snapshot.value.mode)
            assertFalse(gate.running)

            controller.setMode(AppMode.EFFECT)
            advanceTimeBy(500)
            runCurrent()
            assertEquals(AppMode.COLOR, controller.snapshot.value.mode)
            assertFalse(gate.running)
            val writes = device.writes.size
            advanceTimeBy(1_000)
            runCurrent()
            assertEquals(writes, device.writes.size)
        }

    @Test
    fun `confirmed hardware effects keep effect mode without software effects`() =
        runTest {
            val device = FakeDevice(softwareEffects = false, hardwareEffects = listOf(HardwareEffect("breathing", 2, 50, emptyList())))
            val controller = LightingController(backgroundScope, RecordingGate(), clockMs = { testScheduler.currentTime })

            controller.bind(binding(device), LightingIntent(mode = AppMode.EFFECT, effectId = "breathing"))
            advanceTimeBy(100)
            runCurrent()

            assertEquals(AppMode.EFFECT, controller.snapshot.value.mode)
            assertTrue(device.hardwareEffectWrites > 0)
        }

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
            assertEquals(ambient.state.value.colors.map(AmbientColorMath::ledIntensity), device.writes.last().colors)
            assertEquals(ambient.state.value.colors, controller.snapshot.value.currentFrame)
            assertEquals(AmbientCaptureStatus.CAPTURING, controller.snapshot.value.ambient.status)
        }

    @Test
    fun `ambient renders a scene cut without waiting for the next tick`() =
        runTest {
            val device = FakeDevice(recommendedFrameIntervalMs = 80)
            val ambient = MutableAmbientFrameSource().apply {
                update(List(8) { RgbColor(0, 0, 0) }, AmbientCaptureStatus.CAPTURING, 1L)
            }
            val controller = LightingController(backgroundScope, RecordingGate(), clockMs = { testScheduler.currentTime })
            controller.bind(
                binding(device, zones = 8, ambient = ambient),
                LightingIntent(mode = AppMode.AMBIENT, ambientSmoothing = 0, ambientVividness = 0),
            )
            advanceTimeBy(1_010)
            runCurrent()

            val writesBefore = device.writes.size
            ambient.update(List(8) { RgbColor(10, 10, 10) }, AmbientCaptureStatus.CAPTURING, 2L)
            advanceTimeBy(5)
            runCurrent()
            assertEquals(writesBefore, device.writes.size)

            val cut = List(8) { RgbColor(0, 0, 255) }
            ambient.update(cut, AmbientCaptureStatus.CAPTURING, 3L)
            advanceTimeBy(5)
            runCurrent()

            assertEquals(cut, device.writes.last().colors)
        }

    @Test
    fun `ambient stops writing while the screen is off and resumes when it wakes`() =
        runTest {
            val device = FakeDevice(recommendedFrameIntervalMs = 80)
            val ambient = MutableAmbientFrameSource().apply {
                update(List(8) { RgbColor(0, 0, 0) }, AmbientCaptureStatus.CAPTURING, 1L)
            }
            var interactive = true
            val controller =
                LightingController(
                    backgroundScope,
                    RecordingGate(),
                    clockMs = { testScheduler.currentTime },
                    screenInteractive = { interactive },
                )
            controller.bind(
                binding(device, zones = 8, ambient = ambient),
                LightingIntent(mode = AppMode.AMBIENT, ambientSmoothing = 0, ambientVividness = 0),
            )
            advanceTimeBy(400)
            runCurrent()

            interactive = false
            advanceTimeBy(600)
            runCurrent()
            val whileAwake = device.writes.size
            advanceTimeBy(5_000)
            runCurrent()
            assertEquals(whileAwake, device.writes.size)

            interactive = true
            advanceTimeBy(1_000)
            runCurrent()
            assertTrue(device.writes.size > whileAwake)
        }

    @Test
    fun `only ambient pauses with the screen off`() {
        assertFalse(rendersWhileScreenOff(AppMode.AMBIENT))
        AppMode.entries.filterNot { it == AppMode.AMBIENT }.forEach { assertTrue(rendersWhileScreenOff(it)) }
    }

    @Test
    fun `audio mode waits on the saved color without authorization and stops when leaving the mode`() =
        runTest {
            val device = FakeDevice(recommendedFrameIntervalMs = 80)
            val gate = RecordingGate()
            val audio = MutableAudioLevelSource()
            val controller = LightingController(backgroundScope, gate, clockMs = { testScheduler.currentTime })
            controller.bind(binding(device, zones = 8, audio = audio), LightingIntent(mode = AppMode.AUDIO))
            advanceTimeBy(100)
            runCurrent()

            assertFalse(gate.running)
            assertTrue(device.writes.last().colors.all { it == LightingIntent().solidColor })
            assertTrue(device.writes.last().power)

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
    fun `a transient missing temperature reading keeps the sensor available`() =
        runTest {
            val temperature = FakeTemperature(50.0)
            val controller = LightingController(backgroundScope, RecordingGate(), clockMs = { testScheduler.currentTime })
            controller.bind(binding(FakeDevice(), temperature = temperature), LightingIntent(mode = AppMode.COLOR))
            advanceTimeBy(100)
            runCurrent()
            assertTrue(controller.snapshot.value.temperatureAvailable)

            temperature.celsius = null
            advanceTimeBy(3_000)
            runCurrent()
            assertTrue(controller.snapshot.value.temperatureAvailable)

            temperature.celsius = 51.0
            advanceTimeBy(3_000)
            runCurrent()
            temperature.celsius = null
            advanceTimeBy(9_000)
            runCurrent()
            assertFalse(controller.snapshot.value.temperatureAvailable)

            temperature.celsius = 49.0
            advanceTimeBy(3_000)
            runCurrent()
            assertTrue(controller.snapshot.value.temperatureAvailable)
        }

    @Test
    fun `temperature availability withdraws after consecutive misses or a missing source`() {
        val availability = TemperatureAvailability(missesBeforeWithdrawal = 3)

        assertTrue(availability.onBind(true))
        assertTrue(availability.onReading(sourcePresent = true, celsius = null))
        assertTrue(availability.onReading(sourcePresent = true, celsius = null))
        assertTrue(availability.onReading(sourcePresent = true, celsius = 40.0))
        assertTrue(availability.onReading(sourcePresent = true, celsius = null))
        assertTrue(availability.onReading(sourcePresent = true, celsius = null))
        assertFalse(availability.onReading(sourcePresent = true, celsius = null))
        assertTrue(availability.onReading(sourcePresent = true, celsius = 41.0))
        assertFalse(availability.onReading(sourcePresent = false, celsius = null))
        assertFalse(availability.onBind(false))
        assertFalse(availability.onReading(sourcePresent = true, celsius = null))
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

    @Test
    fun `a revoked ambient capture falls back to the saved solid color instead of black`() =
        runTest {
            val saved = RgbColor(12, 200, 40)
            val device = FakeDevice()
            val ambient = MutableAmbientFrameSource().apply { reset(AmbientCaptureStatus.REVOKED) }
            val controller = LightingController(backgroundScope, RecordingGate(), clockMs = { testScheduler.currentTime })
            controller.bind(
                binding(device, zones = 4, ambient = ambient),
                LightingIntent(mode = AppMode.AMBIENT, solidColor = saved, staticColors = List(4) { saved }),
            )
            advanceTimeBy(100)
            runCurrent()

            assertEquals(List(4) { saved }, device.writes.last().colors)
            assertEquals(List(4) { saved }, controller.snapshot.value.currentFrame)
        }

    @Test
    fun `applying a profile reconciles the service once`() =
        runTest {
            val blue = RgbColor(0, 0, 255)
            val device = FakeDevice()
            val gate = RecordingGate()
            val controller = LightingController(backgroundScope, gate, clockMs = { testScheduler.currentTime })
            controller.bind(binding(device), LightingIntent(mode = AppMode.EFFECT, effectId = "rainbow"))
            advanceTimeBy(100)
            runCurrent()
            val before = gate.starts + gate.stops

            controller.applyProfile(
                ProfileApplication(
                    mode = AppMode.COLOR,
                    solidColor = RgbColor(1, 1, 1),
                    gradientStops = listOf(blue, blue),
                    staticColors = listOf(blue, blue),
                    effectId = "breathing",
                    speed = 40,
                    gradientSpeed = 20,
                    effectUsesGradient = false,
                    brightness = 70,
                    batteryBreathe = false,
                    temperatureBreathe = false,
                ),
            )
            controller.awaitIdle()
            runCurrent()

            assertEquals(1, gate.starts + gate.stops - before)
            assertFalse(gate.running)
            assertEquals(AppMode.COLOR, controller.snapshot.value.mode)
            assertEquals(70, controller.snapshot.value.brightness)
            assertEquals(listOf(blue, blue), device.writes.last().colors)
        }

    @Test
    fun `temperature availability is cached instead of rescanning on every frame`() =
        runTest {
            var probes = 0
            val temperature =
                object : TemperatureSource {
                    override val available: Boolean
                        get() {
                            probes++
                            return true
                        }

                    override fun readCelsius(): Double = 45.0
                }
            val controller = LightingController(backgroundScope, RecordingGate(), clockMs = { testScheduler.currentTime })
            controller.bind(binding(FakeDevice(), temperature = temperature), LightingIntent(mode = AppMode.EFFECT, effectId = "rainbow"))
            advanceTimeBy(1_000)
            runCurrent()

            assertTrue(controller.snapshot.value.temperatureAvailable)
            assertEquals(0, probes)
        }
    @Test
    fun `smart brightness off keeps the user brightness`() =
        runTest {
            val device = FakeDevice()
            val controller = LightingController(backgroundScope, RecordingGate(), clockMs = { testScheduler.currentTime })
            controller.bind(binding(device), LightingIntent(brightness = 80))
            controller.setScreenLevel(0.2f)
            advanceTimeBy(1_000)
            runCurrent()
            assertEquals(80, device.writes.last().brightness)
        }

    @Test
    fun `smart brightness eases a static color down to the screen level`() =
        runTest {
            val device = FakeDevice()
            val controller = LightingController(backgroundScope, RecordingGate(), clockMs = { testScheduler.currentTime })
            controller.bind(binding(device), LightingIntent(brightness = 80, smartBrightness = true))
            controller.setScreenLevel(1f)
            advanceTimeBy(1_000)
            runCurrent()
            val before = device.writes.size
            assertEquals(80, device.writes.last().brightness)

            controller.setScreenLevel(0.5f)
            advanceTimeBy(1_000)
            runCurrent()

            val ramp = device.writes.drop(before).map { it.brightness }
            assertTrue(ramp.size >= 3)
            assertEquals(ramp.sortedDescending(), ramp)
            assertEquals(40, ramp.last())
            assertEquals(40, controller.snapshot.value.ledBrightness)
        }

    @Test
    fun `smart brightness applies at once when the screen level is already known`() =
        runTest {
            val device = FakeDevice()
            val controller = LightingController(backgroundScope, RecordingGate(), clockMs = { testScheduler.currentTime })
            controller.setScreenLevel(0.25f)
            controller.bind(binding(device), LightingIntent(brightness = 80, smartBrightness = true))
            advanceTimeBy(20)
            runCurrent()
            assertEquals(20, device.writes.first().brightness)
        }

    @Test
    fun `smart brightness rewrites a hardware effect only once the ramp settles`() =
        runTest {
            val device = FakeDevice(hardwareEffects = listOf(HardwareEffect("breathing", 1, 50, emptyList())), softwareEffects = false)
            val controller = LightingController(backgroundScope, RecordingGate(), clockMs = { testScheduler.currentTime })
            controller.setScreenLevel(1f)
            controller.bind(binding(device), LightingIntent(mode = AppMode.EFFECT, effectId = "breathing", smartBrightness = true))
            advanceTimeBy(1_000)
            runCurrent()
            val before = device.hardwareEffectWrites

            controller.setScreenLevel(0.3f)
            advanceTimeBy(1_000)
            runCurrent()

            assertEquals(before + 1, device.hardwareEffectWrites)
        }

    @Test
    fun `software effects pick up the screen level on their next frames`() =
        runTest {
            val device = FakeDevice(recommendedFrameIntervalMs = 80)
            val controller = LightingController(backgroundScope, RecordingGate(), clockMs = { testScheduler.currentTime })
            controller.setScreenLevel(1f)
            controller.bind(binding(device), LightingIntent(mode = AppMode.EFFECT, effectId = "rainbow", brightness = 60, smartBrightness = true))
            advanceTimeBy(500)
            runCurrent()

            controller.setScreenLevel(0.5f)
            advanceTimeBy(1_000)
            runCurrent()

            assertEquals(30, device.writes.last().brightness)
        }

    @Test
    fun `turning smart brightness off eases back to the user brightness`() =
        runTest {
            val device = FakeDevice()
            val controller = LightingController(backgroundScope, RecordingGate(), clockMs = { testScheduler.currentTime })
            controller.setScreenLevel(0.5f)
            controller.bind(binding(device), LightingIntent(brightness = 80, smartBrightness = true))
            advanceTimeBy(1_000)
            runCurrent()

            controller.setSmartBrightness(false)
            advanceTimeBy(1_000)
            runCurrent()

            assertEquals(80, device.writes.last().brightness)
            assertFalse(controller.snapshot.value.smartBrightness)
        }

    @Test
    fun `unknown screen level holds the last applied brightness`() =
        runTest {
            val device = FakeDevice()
            val controller = LightingController(backgroundScope, RecordingGate(), clockMs = { testScheduler.currentTime })
            controller.setScreenLevel(0.5f)
            controller.bind(binding(device), LightingIntent(brightness = 80, smartBrightness = true))
            advanceTimeBy(1_000)
            runCurrent()

            controller.setScreenLevel(null)
            advanceTimeBy(1_000)
            runCurrent()

            assertEquals(40, device.writes.last().brightness)
        }

    @Test
    fun `slow indicator loops are woken for the final ramp value`() =
        runTest {
            val device = FakeDevice(recommendedFrameIntervalMs = 80)
            val controller = LightingController(backgroundScope, RecordingGate(), clockMs = { testScheduler.currentTime })
            controller.setScreenLevel(1f)
            controller.bind(binding(device), LightingIntent(mode = AppMode.BATTERY, brightness = 80, smartBrightness = true))
            advanceTimeBy(3_000)
            runCurrent()

            controller.setScreenLevel(0.5f)
            advanceTimeBy(460)
            runCurrent()

            assertEquals(40, device.writes.last().brightness)
        }

    @Test
    fun `color calibration scales static writes`() =
        runTest {
            val device = FakeDevice()
            val controller = LightingController(backgroundScope, RecordingGate(), clockMs = { testScheduler.currentTime })
            controller.bind(
                binding(device),
                LightingIntent(staticColors = List(2) { RgbColor(255, 255, 0) }, colorCalibration = ColorCalibration(yellow = 37, orange = -56)),
            )
            advanceTimeBy(20)
            runCurrent()
            assertEquals(List(2) { RgbColor(161, 255, 0) }, device.writes.last().colors)
        }

    @Test
    fun `color calibration scales rendered frames`() =
        runTest {
            val device = FakeDevice(recommendedFrameIntervalMs = 80)
            val controller = LightingController(backgroundScope, RecordingGate(), clockMs = { testScheduler.currentTime })
            controller.bind(
                binding(device),
                LightingIntent(mode = AppMode.EFFECT, effectId = "rainbow", colorCalibration = ColorCalibration(white = 50)),
            )
            advanceTimeBy(500)
            runCurrent()
            assertTrue(device.writes.last().colors.all { it.red <= 128 && it.green <= 128 })
        }

    @Test
    fun `calibration preview shows the key color, pauses the render loop and lights up when off`() =
        runTest {
            val device = FakeDevice(recommendedFrameIntervalMs = 80)
            val controller = LightingController(backgroundScope, RecordingGate(), clockMs = { testScheduler.currentTime })
            controller.bind(binding(device), LightingIntent(mode = AppMode.EFFECT, effectId = "rainbow", power = false))
            advanceTimeBy(300)
            runCurrent()

            controller.previewColorCalibration(ColorCalibration(yellow = 37, orange = -56), RgbColor(255, 128, 0))
            advanceTimeBy(20)
            runCurrent()
            val previewWrites = device.writes.size
            assertEquals(FakeDevice.Write(List(2) { RgbColor(161, 81, 0) }, 100, true), device.writes.last())
            advanceTimeBy(500)
            runCurrent()
            assertEquals(previewWrites, device.writes.size)
            assertTrue(controller.snapshot.value.calibrationPreview)

            controller.previewColorCalibration(null)
            advanceTimeBy(20)
            runCurrent()
            assertFalse(device.writes.last().power)
        }

    @Test
    fun `calibration preview expires on its own`() =
        runTest {
            val device = FakeDevice()
            val controller = LightingController(backgroundScope, RecordingGate(), clockMs = { testScheduler.currentTime })
            controller.bind(binding(device), LightingIntent(staticColors = List(2) { RgbColor(10, 20, 30) }))
            controller.previewColorCalibration(ColorCalibration(white = 20))
            advanceTimeBy(LightingController.CALIBRATION_PREVIEW_TIMEOUT_MS + 100)
            runCurrent()
            assertFalse(controller.snapshot.value.calibrationPreview)
            assertEquals(List(2) { RgbColor(10, 20, 30) }, device.writes.last().colors)
        }

    @Test
    fun `saving a calibration ends the preview`() =
        runTest {
            val device = FakeDevice()
            val controller = LightingController(backgroundScope, RecordingGate(), clockMs = { testScheduler.currentTime })
            controller.bind(binding(device), LightingIntent(staticColors = List(2) { RgbColor(255, 255, 255) }))
            controller.previewColorCalibration(ColorCalibration(white = 20))
            controller.setColorCalibration(ColorCalibration(white = -20))
            advanceTimeBy(20)
            runCurrent()
            assertEquals(List(2) { RgbColor(255, 255, 204) }, device.writes.last().colors)
            assertEquals(ColorCalibration(white = -20), controller.snapshot.value.colorCalibration)
            assertFalse(controller.snapshot.value.calibrationPreview)
        }

    @Test
    fun `aurora dims ambilight with its own audio source`() =
        runTest {
            val device = FakeDevice(recommendedFrameIntervalMs = 80)
            val ambient = MutableAmbientFrameSource().apply { update(List(2) { RgbColor(255, 255, 255) }, AmbientCaptureStatus.CAPTURING, 1L) }
            val vu = MutableAudioLevelSource(AudioLevelState(1.0, AudioCaptureStatus.CAPTURING))
            val aurora = MutableAudioLevelSource(AudioLevelState(0.0, AudioCaptureStatus.CAPTURING))
            val controller = LightingController(backgroundScope, RecordingGate(), clockMs = { testScheduler.currentTime })
            controller.bind(
                binding(device, ambient = ambient, audio = vu, ambientAudio = aurora),
                LightingIntent(mode = AppMode.AMBIENT, ambientSmoothing = 0, ambientVividness = 0, ambientAudio = true),
            )
            advanceTimeBy(200)
            runCurrent()
            val full = AmbientColorMath.ledIntensity(RgbColor(255, 255, 255))
            assertTrue(device.writes.last().colors.all { it.red < full.red })

            aurora.update(1.0, AudioCaptureStatus.CAPTURING)
            advanceTimeBy(200)
            runCurrent()
            assertEquals(List(2) { full }, device.writes.last().colors)
        }
}
