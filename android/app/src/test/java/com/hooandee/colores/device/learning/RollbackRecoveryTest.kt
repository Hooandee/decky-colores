package com.hooandee.colores.device.learning

import com.hooandee.colores.led.SingleAdcJoypadDescriptor
import org.junit.Assert.assertEquals
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
