package com.hooandee.colores.device.learning

import com.hooandee.colores.device.AndroidDeviceIdentity
import com.hooandee.colores.led.SysfsColorKind
import com.hooandee.colores.led.SysfsRgbDescriptor
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HardwareLearningHandoffTest {
    private val candidate =
        ProbeCandidate(
            cartridgeId = "android-sysfs-multicolor",
            cartridgeVersion = 1,
            surface = ProbeSurface.SYSFS_RGB,
            descriptor = SysfsRgbDescriptor("/sys/class/leds/gamepad", 1, 255, SysfsColorKind.MULTI_INTENSITY_HEX),
            signalKeys = emptySet(),
        )

    @Test
    fun `cancellation outlives the owner scope and restores the active probe`() =
        runTest {
            val fixture = activeFixture()
            val owner = CoroutineScope(Job())
            val pending = owner.launch { awaitCancellation() }
            owner.cancel()

            val result = fixture.handoff.cancel(pending).await()

            assertEquals(RollbackStatus.RESTORED_AND_READ_BACK, result.status)
            assertEquals("original", fixture.cartridge.hardwareValue)
            assertNull(fixture.handoff.session)
            assertNull(fixture.store.loadRollback())
            assertEquals(Unit, fixture.coordinator.whenIdle { Unit })
        }

    @Test
    fun `recovery distinguishes an active session from an empty journal`() =
        runTest {
            val fixture = activeFixture()

            assertEquals(LearningRecovery.BUSY, fixture.handoff.recover())

            fixture.handoff.cancel(null)

            assertEquals(LearningRecovery.NOTHING_TO_RESTORE, fixture.handoff.recover())
            assertEquals("original", fixture.cartridge.hardwareValue)
        }

    @Test
    fun `failed recovery is reported instead of nothing to restore`() =
        runTest {
            val coordinator = HardwareLearningCoordinator()
            val handoff = HardwareLearningHandoff(this, coordinator, { RollbackStatus.RESTORE_FAILED }, EmptyCoroutineContext)

            assertEquals(LearningRecovery.FAILED, handoff.recover())
        }

    private suspend fun kotlinx.coroutines.test.TestScope.activeFixture(): Fixture {
        val values = mutableMapOf<String, String>()
        val store = HardwareLearningStore(values::get, { key, value -> values.set(key, value).let { true } }, { values.remove(it) != null })
        val cartridge = RecordingCartridge()
        val catalog = ProbeCartridgeCatalog(listOf(cartridge))
        val coordinator = HardwareLearningCoordinator()
        val recovery = RollbackRecovery(store, catalog)
        val handoff = HardwareLearningHandoff(this, coordinator, recovery::recover, EmptyCoroutineContext)
        val session = HardwareLearningSession(AndroidDeviceIdentity("Mystery", "mystery", "Maker", emptyMap()), catalog, store, "1.0")
        coordinator.begin { true }
        session.start(candidate)
        session.consent()
        session.run(ProbeStep.COLOR)
        handoff.session = session
        return Fixture(store, cartridge, coordinator, handoff)
    }

    private data class Fixture(
        val store: HardwareLearningStore,
        val cartridge: RecordingCartridge,
        val coordinator: HardwareLearningCoordinator,
        val handoff: HardwareLearningHandoff,
    )

    private class RecordingCartridge : ProbeCartridge {
        override val id = "android-sysfs-multicolor"
        override val version = 1
        override val surface = ProbeSurface.SYSFS_RGB
        var hardwareValue = "original"

        override fun accepts(candidate: ProbeCandidate) = candidate.cartridgeId == id

        override fun snapshot(candidate: ProbeCandidate) = ProbeSnapshot(mapOf("hardware" to hardwareValue))

        override fun supportedSteps(candidate: ProbeCandidate) = listOf(ProbeStep.COLOR)

        override fun execute(candidate: ProbeCandidate, step: ProbeStep, zone: Int?): Boolean {
            hardwareValue = "magenta"
            return true
        }

        override fun restore(candidate: ProbeCandidate, snapshot: ProbeSnapshot): RollbackStatus {
            hardwareValue = snapshot.values.getValue("hardware")
            return RollbackStatus.RESTORED_AND_READ_BACK
        }
    }
}
