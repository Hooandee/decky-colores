package com.hooandee.colores.control

import com.hooandee.colores.engine.BandSet
import com.hooandee.colores.engine.EffectCatalog
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
    ) : LedDevice {
        data class Write(val colors: List<RgbColor>, val brightness: Int, val power: Boolean)

        val writes = mutableListOf<Write>()
        var invalidations = 0

        @Volatile
        var failOnce = false

        override val available = true
        override val supportsPerZone = true

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

        override fun invalidate() {
            invalidations++
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
        val running: Boolean get() = starts > stops

        override fun start() {
            starts++
        }

        override fun stop() {
            stops++
        }
    }

    private fun binding(
        device: LedDevice,
        battery: BatterySource = FakeBattery(BatteryReading(80, charging = true, present = true)),
        temperature: TemperatureSource? = null,
        performance: PerformanceSource? = null,
        zones: Int = 2,
    ) = LightingBinding("dev", device, zones, catalog, bands, battery, temperature, performance)

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
            val controller = LightingController(backgroundScope, RecordingGate(), clockMs = { testScheduler.currentTime })
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
        }
}
