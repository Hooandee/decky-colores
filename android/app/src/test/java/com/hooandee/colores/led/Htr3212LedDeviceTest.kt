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
    fun `direct HTR control does not create missing optional vendor settings`() =
        runTest {
            val store = FakeHtrSettingsStore(existingVendorSettings = false)
            store.values["enabled"] = "1,1"
            val executor = FakePServerExecutor()
            val device = device(store, executor)

            device.readState()
            assertTrue(device.applyZones(List(8) { RgbColor(20, 30, 40) }, brightness = 70, power = true))
            runCurrent()

            assertFalse(store.values.containsKey("color"))
            assertFalse(store.values.containsKey("brightness"))
            assertEquals("1,1", store.values["enabled"])
            assertEquals(2, executor.commands.size)
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
            assertEquals(54, executor.commands[0].count { it == '&' })
            assertTrue(executor.commands[1].startsWith("i2cset -f -y 0 0x3c"))
            assertEquals(54, executor.commands[1].count { it == '&' })
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `Thor submits both sticks in one privileged transaction`() =
        runTest {
            val store = FakeHtrSettingsStore()
            val executor = FakePServerExecutor()
            val device = device(store, executor, pairedWrite = true)

            assertTrue(device.applyZones(List(8) { RgbColor(20, 30, 40) }, brightness = 70, power = true))
            runCurrent()

            assertEquals(1, executor.commands.size)
            assertTrue(executor.commands.single().contains("i2cset -f -y 1 0x3c"))
            assertTrue(executor.commands.single().contains("i2cset -f -y 0 0x3c"))
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `an already applied frame is skipped and the next change goes out at once`() =
        runTest {
            val executor = FakePServerExecutor()
            val device = device(FakeHtrSettingsStore(), executor, pairedWrite = true)
            val red = List(8) { RgbColor(200, 0, 0) }

            device.applyZones(red, brightness = 100, power = true)
            runCurrent()
            advanceTimeBy(100)
            device.applyZones(red, brightness = 100, power = true)
            runCurrent()
            advanceTimeBy(10)
            device.applyZones(List(8) { RgbColor(0, 0, 200) }, brightness = 100, power = true)
            runCurrent()

            assertEquals(2, executor.commands.size)
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `a half failed vendor write is redone when the earlier state comes back`() =
        runTest {
            val store = FakeHtrSettingsStore()
            val device = device(store, FakePServerExecutor(), pairedWrite = true)
            val colors = List(8) { RgbColor(20, 30, 40) }
            device.applyZones(colors, brightness = 60, power = true)
            runCurrent()
            advanceTimeBy(2_000)

            store.failingKeys += "enabled"
            device.applyZones(colors, brightness = 70, power = false)
            runCurrent()
            store.failingKeys.clear()
            device.applyZones(colors, brightness = 60, power = true)
            advanceTimeBy(2_000)
            runCurrent()

            assertEquals("0.6", store.values["brightness"])
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `paired sticks are written concurrently and either failure fails the frame`() =
        runTest {
            val executor = FakePServerExecutor()
            val device = device(FakeHtrSettingsStore(), executor, pairedWrite = true)
            device.applyZones(List(8) { RgbColor(20, 30, 40) }, brightness = 70, power = true)
            runCurrent()
            val command = executor.commands.single()

            val sequentialMs = Regex("i2cset ").findAll(command).count() * FAKE_I2C_MS
            val (status, elapsedMs) = runWithFakeI2c(command, failingBus = null)
            assertEquals(0, status)
            assertTrue("elapsed=$elapsedMs sequential=$sequentialMs", elapsedMs < sequentialMs * 0.75)

            assertTrue(runWithFakeI2c(command, failingBus = 1).first != 0)
            assertTrue(runWithFakeI2c(command, failingBus = 0).first != 0)
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
            assertTrue(executor.commands.takeLast(2).all { command -> command.count { it == '&' } == 54 })
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `direct RGB bytes are scaled by the requested brightness`() =
        runTest {
            val store = FakeHtrSettingsStore()
            val executor = FakePServerExecutor()
            val device = device(store, executor)

            device.applyZones(List(8) { RgbColor(200, 100, 50) }, brightness = 50, power = true)
            runCurrent()

            assertEquals("0.5", store.values["brightness"])
            assertTrue(executor.commands[0].contains("0x0d 0x64 b"))
            assertTrue(executor.commands[0].contains("0x0e 0x32 b"))
            assertTrue(executor.commands[0].contains("0x0f 0x19 b"))
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `zero brightness keeps power enabled but sends black direct zones`() =
        runTest {
            val store = FakeHtrSettingsStore()
            val executor = FakePServerExecutor()
            val device = device(store, executor)

            device.applyZones(List(8) { RgbColor(200, 100, 50) }, brightness = 0, power = true)
            runCurrent()

            assertEquals("1,1", store.values["enabled"])
            assertEquals("0.0", store.values["brightness"])
            assertTrue(executor.commands[0].contains("0x0d 0x00 b"))
            assertTrue(executor.commands[0].contains("0x0e 0x00 b"))
            assertTrue(executor.commands[0].contains("0x0f 0x00 b"))
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `editing a vendor-source zone does not repaint the vendor color`() =
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

            assertEquals(1, store.writes.count { it.first == "color" })
            assertEquals(3, executor.commands.size)
            assertEquals(6, executor.commands.last().count { it == '&' })
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
            assertTrue(executor.commands.takeLast(2).all { command -> command.count { it == '&' } == 54 })
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
            assertTrue(executor.commands[3].contains("0x10 0x36 b"))
            assertTrue(executor.commands[3].contains("0x11 0x37 b"))
            assertTrue(executor.commands[3].contains("0x12 0x37 b"))
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `the seed write schedules one delayed resend that editing does not multiply`() =
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
            assertEquals(3, executor.commands.size)

            advanceTimeBy(2_000)
            runCurrent()

            assertEquals(5, executor.commands.size)
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
        blockWrite: Boolean = false,
        pairedWrite: Boolean = false,
    ) =
        Htr3212LedDevice(
            descriptor =
                descriptor.copy(
                    htr3212 = descriptor.htr3212?.copy(blockWrite = blockWrite, pairedWrite = pairedWrite),
                ),
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
                    explicitInitialization = true,
                ),
        )
}

private class FakeHtrSettingsStore(
    override val available: Boolean = true,
    existingVendorSettings: Boolean = true,
) : SystemSettingsStore {
    val values = mutableMapOf<String, String>()
    val writes = mutableListOf<Pair<String, String>>()
    val failingKeys = mutableSetOf<String>()

    init {
        if (existingVendorSettings) {
            values["color"] = "#FFFFFFFF,#FFFFFFFF"
            values["brightness"] = "1.0"
            values["enabled"] = "1,1"
            values["left"] = "1"
            values["right"] = "1"
        }
    }

    override fun get(key: String): String? = values[key]

    override fun put(key: String, value: String): Boolean {
        writes += key to value
        if (key in failingKeys) return false
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

private const val FAKE_I2C_MS = 40

/** Runs a generated command with i2cset replaced by a timed stub; returns exit status and wall time. */
private fun runWithFakeI2c(
    command: String,
    failingBus: Int?,
): Pair<Int, Long> {
    val stub =
        "i2cset() { sleep 0.04; [ \"\$3\" != \"${failingBus ?: -1}\" ]; }; log() { :; }; "
    val started = System.nanoTime()
    val process = ProcessBuilder("/bin/sh", "-c", stub + command).redirectErrorStream(true).start()
    process.inputStream.readBytes()
    val status = process.waitFor()
    return status to (System.nanoTime() - started) / 1_000_000
}
