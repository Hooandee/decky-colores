package com.hooandee.colores.device.learning

import com.hooandee.colores.device.GenericVendorLed
import com.hooandee.colores.led.Htr3212Descriptor
import com.hooandee.colores.led.PServerCommandExecutor
import com.hooandee.colores.led.SingleAdcJoypadDescriptor
import com.hooandee.colores.led.SystemSettingsStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.test.runTest

class RollbackRecoveryTest {
    @Test
    fun `successful startup recovery clears the durable journal`() {
        val values = mutableMapOf<String, String>()
        val store = HardwareLearningStore(values::get, { key, value -> values.set(key, value).let { true } }, { values.remove(it) != null })
        val cartridge = RecordingCartridge(RollbackStatus.RESTORED_AND_READ_BACK)
        val descriptor = SingleAdcJoypadDescriptor()
        store.saveRollback(
            RollbackRecord("s", cartridge.id, cartridge.version, encodeLearningDescriptor(descriptor), ProbeSnapshot(mapOf("red" to "4"))),
        )

        val status = RollbackRecovery(store, ProbeCartridgeCatalog(listOf(cartridge))).recover()

        assertEquals(RollbackStatus.RESTORED_AND_READ_BACK, status)
        assertNull(store.loadRollback())
        assertTrue(cartridge.restored)
    }

    @Test
    fun `failed startup recovery keeps the journal and blocks later work`() {
        val values = mutableMapOf<String, String>()
        val store = HardwareLearningStore(values::get, { key, value -> values.set(key, value).let { true } }, { values.remove(it) != null })
        val cartridge = RecordingCartridge(RollbackStatus.RESTORE_FAILED)
        store.saveRollback(
            RollbackRecord("s", cartridge.id, cartridge.version, encodeLearningDescriptor(SingleAdcJoypadDescriptor()), ProbeSnapshot(mapOf("red" to "4"))),
        )

        assertEquals(RollbackStatus.RESTORE_FAILED, RollbackRecovery(store, ProbeCartridgeCatalog(listOf(cartridge))).recover())
        assertTrue(store.loadRollback() != null)
    }

    @Test
    fun `malformed startup journal fails closed`() {
        val store = HardwareLearningStore(read = { "not-json" }, write = { _, _ -> true }, remove = { true })

        assertEquals(RollbackStatus.RESTORE_FAILED, RollbackRecovery(store, ProbeCartridgeCatalog(emptyList())).recover())
    }

    @Test
    fun `recovered journal that cannot be cleared still blocks startup`() {
        val values = mutableMapOf<String, String>()
        val store = HardwareLearningStore(values::get, { key, value -> values.set(key, value).let { true } }, { false })
        val cartridge = RecordingCartridge(RollbackStatus.RESTORED_AND_READ_BACK)
        store.saveRollback(
            RollbackRecord("s", cartridge.id, cartridge.version, encodeLearningDescriptor(SingleAdcJoypadDescriptor()), ProbeSnapshot(mapOf("red" to "4"))),
        )

        assertEquals(RollbackStatus.RESTORE_FAILED, RollbackRecovery(store, ProbeCartridgeCatalog(listOf(cartridge))).recover())
        assertTrue(store.loadRollback() != null)
    }

    @Test
    fun `restore exception keeps the journal and blocks startup`() {
        val values = mutableMapOf<String, String>()
        val store = HardwareLearningStore(values::get, { key, value -> values.set(key, value).let { true } }, { values.remove(it) != null })
        val cartridge = RecordingCartridge(RollbackStatus.RESTORED_AND_READ_BACK, throwOnRestore = true)
        store.saveRollback(
            RollbackRecord("s", cartridge.id, cartridge.version, encodeLearningDescriptor(SingleAdcJoypadDescriptor()), ProbeSnapshot(mapOf("red" to "4"))),
        )

        assertEquals(RollbackStatus.RESTORE_FAILED, RollbackRecovery(store, ProbeCartridgeCatalog(listOf(cartridge))).recover())
        assertTrue(store.hasRollback())
    }

    @Test
    fun `failed recovery prevents normal runtime restoration`() = runTest {
        var normalRestoreCalls = 0

        val restored =
            restoreAfterLearningRollback(
                recover = { RollbackStatus.RESTORE_FAILED },
                restoreRuntime = { normalRestoreCalls += 1; true },
            )

        assertEquals(false, restored)
        assertEquals(0, normalRestoreCalls)
    }

    @Test
    fun `clean or recovered startup continues into normal runtime`() = runTest {
        var normalRestoreCalls = 0

        val restored =
            restoreAfterLearningRollback(
                recover = { RollbackStatus.RESTORED_AND_READ_BACK },
                restoreRuntime = { normalRestoreCalls += 1; true },
            )

        assertTrue(restored)
        assertEquals(1, normalRestoreCalls)
    }

    @Test
    fun `repeated failures are counted and the journal is archived after the limit`() {
        val values = mutableMapOf<String, String>()
        val store = mapStore(values)
        val cartridge = RecordingCartridge(RollbackStatus.RESTORE_FAILED)
        store.saveRollback(
            RollbackRecord("s", cartridge.id, cartridge.version, encodeLearningDescriptor(SingleAdcJoypadDescriptor()), ProbeSnapshot(mapOf("red" to "4"))),
        )
        val recovery = RollbackRecovery(store, ProbeCartridgeCatalog(listOf(cartridge)), maxAttempts = 3)

        repeat(2) { assertEquals(RollbackStatus.RESTORE_FAILED, recovery.recover()) }
        assertEquals(RollbackFailure(2, RollbackFailureReason.RESTORE_FAILED), recovery.failure)
        assertTrue(recovery.pending)

        assertEquals(RollbackStatus.RESTORE_FAILED, recovery.recover())
        assertFalse(recovery.pending)
        assertNull(recovery.recover())
        val archived = requireNotNull(store.loadArchivedRollback())
        assertEquals(RollbackFailureReason.RESTORE_FAILED, archived.reason)
        assertEquals(3, archived.attempts)
        assertTrue(archived.journal.contains("\"session_id\":\"s\""))
    }

    @Test
    fun `discarding a corrupt journal archives it for the report`() {
        val values = mutableMapOf("rollback" to "not-json")
        val store = mapStore(values)
        val recovery = RollbackRecovery(store, ProbeCartridgeCatalog(emptyList()))

        assertEquals(RollbackStatus.RESTORE_FAILED, recovery.recover())
        assertEquals(RollbackFailureReason.CORRUPT_JOURNAL, recovery.failure?.reason)
        assertTrue(recovery.discard())

        assertFalse(recovery.pending)
        assertNull(recovery.failure)
        assertEquals("not-json", store.loadArchivedRollback()?.journal)
        assertEquals(RollbackFailureReason.DISCARDED, store.loadArchivedRollback()?.reason)
    }

    @Test
    fun `a new journal resets the previous failure counter`() {
        val store = mapStore(mutableMapOf())
        store.saveRollback(RollbackRecord("a", "x", 1, "{}", ProbeSnapshot(emptyMap())))
        store.recordRollbackFailure(RollbackFailureReason.RESTORE_FAILED)
        store.clearRollback()
        store.saveRollback(RollbackRecord("b", "x", 1, "{}", ProbeSnapshot(emptyMap())))

        assertNull(store.loadRollbackFailure())
    }

    @Test
    fun `HTR recovery skips I2C when the observed topology contradicts the journal`() {
        val key = "joystick_light_enabled"
        val settings = FakeSettings(mutableMapOf(key to "1,1"))
        val executor = FakeExecutor()
        val cartridge = Htr3212LearningCartridge(settings, executor, settleVendor = {})
        val store = mapStore(mutableMapOf())
        store.saveRollback(htrRecord(key))
        val moved = I2cTopologyReader { listOf(I2cController(3, 0x3c, "htr3212l"), I2cController(7, 0x3c, "htr3212r")) }

        val status = RollbackRecovery(store, ProbeCartridgeCatalog(listOf(cartridge)), moved).recover()

        assertEquals(RollbackStatus.RESTORE_FAILED, status)
        assertTrue(executor.commands.isEmpty())
        assertEquals("0,0", settings.values[key])
        assertEquals(RollbackFailureReason.TOPOLOGY_MISMATCH, store.loadRollbackFailure()?.reason)
        assertTrue(store.hasRollback())
    }

    @Test
    fun `HTR recovery keeps its direct restore when topology matches or is unreadable`() {
        listOf(
            I2cTopologyReader { listOf(I2cController(3, 0x3c, "htr3212l"), I2cController(5, 0x3c, "htr3212r")) },
            I2cTopologyReader { emptyList() },
            I2cTopologyReader { error("unreadable") },
        ).forEach { reader ->
            val key = "joystick_light_enabled"
            val settings = FakeSettings(mutableMapOf(key to "1,1"))
            val executor = FakeExecutor()
            val store = mapStore(mutableMapOf())
            store.saveRollback(htrRecord(key))

            val status = RollbackRecovery(store, ProbeCartridgeCatalog(listOf(Htr3212LearningCartridge(settings, executor, settleVendor = {}))), reader).recover()

            assertEquals(RollbackStatus.RESTORED_WITHOUT_HARDWARE_READBACK, status)
            assertTrue(executor.commands.isNotEmpty())
            assertFalse(store.hasRollback())
        }
    }

    private fun htrRecord(key: String): RollbackRecord {
        val descriptor =
            GenericVendorLed.descriptor(8).copy(
                driver = "htr3212",
                enableKeys = listOf(key),
                htr3212 = Htr3212Descriptor(3, 5, 0x3c, listOf(0, 1, 2, 3), listOf(0, 1, 2, 3)),
            )
        return RollbackRecord("h", HTR3212_PROBE_ID, HTR3212_PROBE_VERSION, encodeLearningDescriptor(descriptor), ProbeSnapshot(mapOf(key to "0,0")))
    }

    private fun mapStore(values: MutableMap<String, String>) =
        HardwareLearningStore(values::get, { key, value -> values.set(key, value).let { true } }, { values.remove(it) != null })

    private class FakeSettings(
        val values: MutableMap<String, String>,
    ) : SystemSettingsStore {
        override val available = true

        override fun get(key: String): String? = values[key]

        override fun put(key: String, value: String): Boolean {
            if (key !in values) return false
            values[key] = value
            return true
        }
    }

    private class FakeExecutor : PServerCommandExecutor {
        override val available = true
        val commands = mutableListOf<String>()

        override fun execute(command: String): Boolean {
            commands += command
            return true
        }
    }

    private class RecordingCartridge(
        private val status: RollbackStatus,
        private val throwOnRestore: Boolean = false,
    ) : ProbeCartridge {
        override val id = "singleadc-joypad"
        override val version = 1
        override val surface = ProbeSurface.SINGLEADC_JOYPAD
        var restored = false

        override fun accepts(candidate: ProbeCandidate) = candidate.descriptor is SingleAdcJoypadDescriptor
        override fun snapshot(candidate: ProbeCandidate) = null
        override fun supportedSteps(candidate: ProbeCandidate) = emptyList<ProbeStep>()
        override fun execute(candidate: ProbeCandidate, step: ProbeStep, zone: Int?) = false
        override fun restore(candidate: ProbeCandidate, snapshot: ProbeSnapshot): RollbackStatus {
            if (throwOnRestore) error("transport")
            restored = true
            return status
        }
    }
}
