package com.hooandee.colores.device.learning

import com.hooandee.colores.device.AndroidDeviceIdentity
import com.hooandee.colores.led.SysfsColorKind
import com.hooandee.colores.led.SysfsRgbDescriptor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HardwareLearningSessionTest {
    private val identity = AndroidDeviceIdentity("Mystery", "mystery", "Maker", emptyMap())
    private val candidate =
        ProbeCandidate(
            cartridgeId = "android-sysfs-multicolor",
            cartridgeVersion = 1,
            surface = ProbeSurface.SYSFS_RGB,
            descriptor = SysfsRgbDescriptor("/sys/class/leds/gamepad", 2, 255, SysfsColorKind.MULTI_INTENSITY_HEX),
            signalKeys = setOf("observed_index_count"),
        )

    @Test
    fun `probe cannot change hardware before explicit consent`() {
        val fixture = fixture()
        fixture.session.start(candidate)

        assertFalse(fixture.session.run(ProbeStep.COLOR))
        assertEquals("original", fixture.cartridge.hardwareValue)
        assertNull(fixture.store.loadRollback())
    }

    @Test
    fun `failed durable journal blocks the first hardware write`() {
        val cartridge = RecordingCartridge()
        val store = HardwareLearningStore(read = { null }, write = { _, _ -> false }, remove = { true })
        val session = session(cartridge, store)
        session.start(candidate)

        val state = session.consent()

        assertEquals(LearningBlockReason.JOURNAL_UNAVAILABLE, (state as HardwareLearningState.Blocked).reason)
        assertEquals("original", cartridge.hardwareValue)
    }

    @Test
    fun `transport exception restores and blocks the session`() {
        val cartridge = RecordingCartridge(throwOnExecute = true)
        val values = mutableMapOf<String, String>()
        val store = HardwareLearningStore(values::get, { key, value -> values.set(key, value).let { true } }, { values.remove(it) != null })
        val session = session(cartridge, store)
        session.start(candidate)
        session.consent()

        assertFalse(session.run(ProbeStep.COLOR))
        assertEquals(LearningBlockReason.WRITE_FAILED, (session.state as HardwareLearningState.Blocked).reason)
        assertEquals("original", cartridge.hardwareValue)
        assertNull(store.loadRollback())
    }

    @Test
    fun `snapshot exception blocks before journaling or writing`() {
        val cartridge = RecordingCartridge(throwOnSnapshot = true)
        val values = mutableMapOf<String, String>()
        val store = HardwareLearningStore(values::get, { key, value -> values.set(key, value).let { true } }, { values.remove(it) != null })
        val session = session(cartridge, store)
        session.start(candidate)

        val state = session.consent()

        assertEquals(LearningBlockReason.SNAPSHOT_UNAVAILABLE, (state as HardwareLearningState.Blocked).reason)
        assertEquals("original", cartridge.hardwareValue)
        assertNull(store.loadRollback())
    }

    @Test
    fun `negative or unsure color answer never persists a binding`() {
        listOf(UserObservation.NO, UserObservation.UNSURE).forEach { answer ->
            val fixture = fixture()
            fixture.session.start(candidate)
            fixture.session.consent()
            assertTrue(fixture.session.run(ProbeStep.COLOR))
            fixture.session.answer(answer)

            val result = fixture.session.finish()

            assertEquals(HardwareLearningStatus.BLOCKED, result.status)
            assertNull(fixture.store.loadBinding())
            assertEquals("original", fixture.cartridge.hardwareValue)
        }
    }

    @Test
    fun `cancellation restores the original hardware value`() {
        val fixture = fixture()
        fixture.session.start(candidate)
        fixture.session.consent()
        fixture.session.run(ProbeStep.COLOR)
        assertEquals("color", fixture.cartridge.hardwareValue)

        val status = fixture.session.cancel()

        assertEquals(RollbackStatus.RESTORED_AND_READ_BACK, status)
        assertEquals("original", fixture.cartridge.hardwareValue)
        assertNull(fixture.store.loadRollback())
        assertNull(fixture.store.loadBinding())
    }

    @Test
    fun `two independently confirmed positions enable per zone control`() {
        val fixture = fixture()
        fixture.session.start(candidate)
        fixture.session.consent()
        fixture.session.run(ProbeStep.COLOR)
        fixture.session.answer(UserObservation.YES)
        fixture.session.run(ProbeStep.ZONE, zone = 0)
        fixture.session.answer(UserObservation.YES)
        fixture.session.run(ProbeStep.ZONE, zone = 1)
        fixture.session.answer(UserObservation.YES)

        val result = fixture.session.finish()
        val binding = requireNotNull(fixture.store.loadBinding())

        assertEquals(HardwareLearningStatus.ADAPTED, result.status)
        assertTrue(binding.capabilities.color)
        assertTrue(binding.capabilities.perZone)
        assertEquals(2, binding.capabilities.zones)
        assertEquals(2, binding.capabilities.zones)
    }

    @Test
    fun `partially confirmed topology stays on safe solid control`() {
        val fixture = fixture()
        fixture.session.start(candidate)
        fixture.session.consent()
        fixture.session.run(ProbeStep.COLOR)
        fixture.session.answer(UserObservation.YES)
        fixture.session.run(ProbeStep.ZONE, zone = 0)
        fixture.session.answer(UserObservation.YES)

        val result = fixture.session.finish()

        assertEquals(HardwareLearningStatus.ADAPTED, result.status)
        assertFalse(result.capabilities.perZone)
        assertEquals(1, result.capabilities.zones)
    }

    @Test
    fun `power capability requires confirmed off and on steps`() {
        val fixture = fixture()
        fixture.session.start(candidate)
        fixture.session.consent()
        fixture.session.run(ProbeStep.COLOR)
        fixture.session.answer(UserObservation.YES)
        fixture.session.run(ProbeStep.POWER_OFF)
        fixture.session.answer(UserObservation.YES)

        assertFalse(fixture.session.finish().capabilities.power)

        val complete = fixture()
        complete.session.start(candidate)
        complete.session.consent()
        complete.session.run(ProbeStep.COLOR)
        complete.session.answer(UserObservation.YES)
        complete.session.run(ProbeStep.POWER_OFF)
        complete.session.answer(UserObservation.YES)
        complete.session.run(ProbeStep.POWER_ON)
        complete.session.answer(UserObservation.YES)

        assertTrue(complete.session.finish().capabilities.power)
    }

    @Test
    fun `binding is blocked when the restored journal cannot be cleared`() {
        val values = mutableMapOf<String, String>()
        val store = HardwareLearningStore(values::get, { key, value -> values.set(key, value).let { true } }, { false })
        val cartridge = RecordingCartridge()
        val session = session(cartridge, store)
        session.start(candidate)
        session.consent()
        session.run(ProbeStep.COLOR)
        session.answer(UserObservation.YES)

        val result = session.finish()

        assertEquals(HardwareLearningStatus.BLOCKED, result.status)
        assertEquals(LearningBlockReason.JOURNAL_UNAVAILABLE, (session.state as HardwareLearningState.Blocked).reason)
        assertNull(store.loadBinding())
        assertTrue(store.hasRollback())
    }

    private fun fixture(): Fixture {
        val values = mutableMapOf<String, String>()
        val store = HardwareLearningStore(values::get, { key, value -> values.set(key, value).let { true } }, { values.remove(it) != null })
        val cartridge = RecordingCartridge()
        return Fixture(store, cartridge, session(cartridge, store))
    }

    private fun session(
        cartridge: RecordingCartridge,
        store: HardwareLearningStore,
    ) =
        HardwareLearningSession(
            identity = identity,
            catalog = ProbeCartridgeCatalog(listOf(cartridge)),
            store = store,
            appVersion = "0.1.0",
            nowEpochMs = { 1234L },
            newSessionId = { "session-1" },
        )

    private data class Fixture(
        val store: HardwareLearningStore,
        val cartridge: RecordingCartridge,
        val session: HardwareLearningSession,
    )

    private class RecordingCartridge(
        private val throwOnExecute: Boolean = false,
        private val throwOnSnapshot: Boolean = false,
    ) : ProbeCartridge {
        override val id = "android-sysfs-multicolor"
        override val version = 1
        override val surface = ProbeSurface.SYSFS_RGB
        var hardwareValue = "original"

        override fun accepts(candidate: ProbeCandidate) = candidate.cartridgeId == id
        override fun snapshot(candidate: ProbeCandidate): ProbeSnapshot {
            if (throwOnSnapshot) error("snapshot")
            return ProbeSnapshot(mapOf("hardware" to hardwareValue))
        }
        override fun supportedSteps(candidate: ProbeCandidate) =
            listOf(ProbeStep.COLOR, ProbeStep.ZONE, ProbeStep.POWER_OFF, ProbeStep.POWER_ON)
        override fun execute(candidate: ProbeCandidate, step: ProbeStep, zone: Int?): Boolean {
            if (throwOnExecute) error("transport")
            hardwareValue = if (step == ProbeStep.ZONE) "zone-$zone" else "color"
            return true
        }

        override fun restore(candidate: ProbeCandidate, snapshot: ProbeSnapshot): RollbackStatus {
            hardwareValue = snapshot.values.getValue("hardware")
            return RollbackStatus.RESTORED_AND_READ_BACK
        }
    }
}
