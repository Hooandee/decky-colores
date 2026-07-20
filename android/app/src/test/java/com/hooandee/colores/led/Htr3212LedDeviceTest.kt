package com.hooandee.colores.led

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.TestScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Htr3212LedDeviceTest {
    @Test
    fun `vendor stick colors expand to four physical zones per stick`() =
        runTest {
            val store = FakeHtrSettingsStore()
            store.values["color"] = "#FFFF0000,#FF26D66C"
            store.values["brightness"] = "1.0"
            store.values["enabled"] = "1,1"
            val device = device(store, FakePServerExecutor())

            assertEquals(
                LedState(
                    zoneColors = List(4) { RgbColor(255, 0, 0) } + List(4) { RgbColor(38, 214, 108) },
                    brightness = 100,
                    power = true,
                ),
                device.readState(),
            )
            assertTrue(store.writes.isEmpty())
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `eight zones keep two vendor fallback colors and write both calibrated buses`() =
        runTest {
            val store = FakeHtrSettingsStore()
            val executor = FakePServerExecutor()
            val device = device(store, executor)
            val colors = (1..8).map { RgbColor(it, it + 10, it + 20) }

            assertTrue(device.applyZones(colors, brightness = 70, power = true))
            runCurrent()

            assertEquals("#FF010B15,#FF050F19", store.values["color"])
            assertEquals("0.7", store.values["brightness"])
            assertEquals("1,1", store.values["enabled"])
            assertEquals(2, executor.commands.size)
            assertTrue(executor.commands[0].startsWith("i2cset -f -y 1 0x3c"))
            assertTrue(executor.commands[0].contains("0x0a 0x03 i"))
            assertTrue(executor.commands[0].contains("0x07 0x04 i"))
            assertTrue(executor.commands[1].startsWith("i2cset -f -y 0 0x3c"))
            assertTrue(executor.commands[1].contains("0x04 0x05 i"))
            assertTrue(executor.commands[1].contains("0x01 0x08 i"))
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `power off uses vendor gate and does not relight direct zones`() =
        runTest {
            val store = FakeHtrSettingsStore()
            val executor = FakePServerExecutor()
            val device = device(store, executor)

            device.applySolid(RgbColor(1, 2, 3), brightness = 50, power = false)
            runCurrent()

            assertEquals("0,0", store.values["enabled"])
            assertEquals("0", store.values["left"])
            assertEquals("0", store.values["right"])
            assertTrue(executor.commands.isEmpty())
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `failed direct write retries only the missing hardware frame`() =
        runTest {
            val store = FakeHtrSettingsStore()
            val executor = FakePServerExecutor(failuresRemaining = 1)
            val device = device(store, executor)
            val colors = List(8) { RgbColor(20, 30, 40) }

            device.applyZones(colors, brightness = 60, power = true)
            runCurrent()
            assertEquals(2, executor.commands.size)

            advanceTimeBy(500)
            runCurrent()

            assertEquals(3, executor.commands.size)
            assertEquals(5, store.writes.size)
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `invalidate forces a complete hardware resend`() =
        runTest {
            val store = FakeHtrSettingsStore()
            val executor = FakePServerExecutor()
            val device = device(store, executor)
            val colors = List(8) { RgbColor(20, 30, 40) }

            device.applyZones(colors, brightness = 60, power = true)
            runCurrent()
            advanceTimeBy(80)
            device.invalidate()
            device.applyZones(colors, brightness = 60, power = true)
            runCurrent()

            assertEquals(4, executor.commands.size)
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `vendor brightness repaint is followed by a complete direct resend`() =
        runTest {
            val store = FakeHtrSettingsStore()
            val executor = FakePServerExecutor()
            val device = device(store, executor)
            val colors = (1..8).map { RgbColor(it, it + 10, it + 20) }

            device.applyZones(colors, brightness = 60, power = true)
            runCurrent()
            advanceTimeBy(80)
            device.applyZones(colors, brightness = 70, power = true)
            runCurrent()

            assertEquals(4, executor.commands.size)
            assertTrue(executor.commands.takeLast(2).all { command -> command.count { it == '&' } == 24 })
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `vendor fallback color repaint resends unchanged zones too`() =
        runTest {
            val store = FakeHtrSettingsStore()
            val executor = FakePServerExecutor()
            val device = device(store, executor)
            val initial = (1..8).map { RgbColor(it, it + 10, it + 20) }

            device.applyZones(initial, brightness = 60, power = true)
            runCurrent()
            advanceTimeBy(80)
            device.applyZones(initial.toMutableList().also { it[0] = RgbColor(90, 91, 92) }, brightness = 60, power = true)
            runCurrent()

            assertEquals(4, executor.commands.size)
            assertTrue(executor.commands.takeLast(2).all { command -> command.count { it == '&' } == 24 })
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `vendor repaint is corrected by a delayed full hardware resend`() =
        runTest {
            val store = FakeHtrSettingsStore()
            val executor = FakePServerExecutor()
            val device = device(store, executor)
            val colors = (1..8).map { RgbColor(it, it + 10, it + 20) }

            device.applyZones(colors, brightness = 60, power = true)
            runCurrent()
            assertEquals(2, executor.commands.size)

            advanceTimeBy(2_000)
            runCurrent()

            assertEquals(4, executor.commands.size)
            assertTrue(executor.commands.takeLast(2).all { command -> command.count { it == '&' } == 24 })
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `delayed hardware resend uses the newest zone colors`() =
        runTest {
            val store = FakeHtrSettingsStore()
            val executor = FakePServerExecutor()
            val device = device(store, executor)
            val initial = (1..8).map { RgbColor(it, it + 10, it + 20) }
            val updated = initial.toMutableList().also { it[1] = RgbColor(90, 91, 92) }

            device.applyZones(initial, brightness = 60, power = true)
            runCurrent()
            advanceTimeBy(80)
            device.applyZones(updated, brightness = 60, power = true)
            runCurrent()
            assertEquals(3, executor.commands.size)

            advanceTimeBy(2_000)
            runCurrent()

            assertEquals(5, executor.commands.size)
            assertTrue(executor.commands[3].contains("0x04 0x5a i"))
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `successive vendor updates collapse to one delayed hardware resend`() =
        runTest {
            val store = FakeHtrSettingsStore()
            val executor = FakePServerExecutor()
            val device = device(store, executor)
            val initial = (1..8).map { RgbColor(it, it + 10, it + 20) }
            val updated = initial.toMutableList().also { it[0] = RgbColor(90, 91, 92) }

            device.applyZones(initial, brightness = 60, power = true)
            runCurrent()
            advanceTimeBy(80)
            device.applyZones(updated, brightness = 60, power = true)
            runCurrent()
            assertEquals(4, executor.commands.size)

            advanceTimeBy(2_000)
            runCurrent()

            assertEquals(6, executor.commands.size)
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `delayed hardware resend survives an initial direct write failure`() =
        runTest {
            val store = FakeHtrSettingsStore()
            val executor = FakePServerExecutor(failuresRemaining = 1)
            val device = device(store, executor)
            val colors = (1..8).map { RgbColor(it, it + 10, it + 20) }

            device.applyZones(colors, brightness = 60, power = true)
            runCurrent()
            assertEquals(2, executor.commands.size)

            advanceTimeBy(2_000)
            runCurrent()

            assertEquals(5, executor.commands.size)
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `invalidate during a hardware write prevents stale cache publication`() =
        runTest {
            val store = FakeHtrSettingsStore()
            lateinit var device: Htr3212LedDevice
            val executor =
                FakePServerExecutor(
                    onExecute = { commandIndex ->
                        if (commandIndex == 1) device.invalidate()
                    },
                )
            device = device(store, executor)
            val colors = List(8) { RgbColor(20, 30, 40) }

            device.applyZones(colors, brightness = 60, power = true)
            runCurrent()
            advanceTimeBy(80)
            device.applyZones(colors, brightness = 60, power = true)
            runCurrent()

            assertEquals(4, executor.commands.size)
        }

    @Test
    fun `device is unavailable without the privileged transport`() =
        runTest {
            assertFalse(device(FakeHtrSettingsStore(), FakePServerExecutor(available = false)).available)
        }

    private fun TestScope.device(
        store: SystemSettingsStore,
        executor: PServerCommandExecutor,
    ) =
        Htr3212LedDevice(
            descriptor = descriptor,
            store = store,
            executor = executor,
            scope = backgroundScope,
            settleVendor = {},
        )

    private val descriptor =
        SettingsProviderDescriptor(
            driver = "htr3212",
            transport = "pserver",
            colorKey = "color",
            colorFormat = "argb_hex_csv",
            brightnessKey = "brightness",
            brightnessRange = 0f..1f,
            enableKeys = listOf("enabled", "left", "right"),
            zones = 8,
            requiresPermission = null,
            vendorService = "com.rp.gameassistant",
            htr3212 =
                Htr3212Descriptor(
                    leftBus = 1,
                    rightBus = 0,
                    address = 0x3c,
                    leftOrder = listOf(0, 1, 3, 2),
                    rightOrder = listOf(1, 2, 3, 0),
                ),
        )
}

private class FakeHtrSettingsStore(
    override val available: Boolean = true,
) : SystemSettingsStore {
    val values = mutableMapOf<String, String>()
    val writes = mutableListOf<Pair<String, String>>()

    override fun get(key: String): String? = values[key]

    override fun put(key: String, value: String): Boolean {
        writes += key to value
        values[key] = value
        return true
    }
}

private class FakePServerExecutor(
    override val available: Boolean = true,
    var failuresRemaining: Int = 0,
    private val onExecute: (Int) -> Unit = {},
) : PServerCommandExecutor {
    val commands = mutableListOf<String>()

    override fun execute(command: String): Boolean {
        commands += command
        onExecute(commands.size)
        if (failuresRemaining <= 0) return true
        failuresRemaining -= 1
        return false
    }
}
