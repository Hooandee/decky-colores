package com.hooandee.colores.device.learning

import com.hooandee.colores.device.AndroidDeviceIdentity
import com.hooandee.colores.device.DeviceCapabilities
import com.hooandee.colores.led.SysfsColorKind
import com.hooandee.colores.led.SysfsRgbDescriptor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HardwareLearningStoreTest {
    @Test
    fun `rollback round trip preserves the exact original values`() {
        val values = mutableMapOf<String, String>()
        val store = HardwareLearningStore(values::get, { key, value -> values.set(key, value).let { true } }, { values.remove(it) != null })
        val record =
            RollbackRecord(
                sessionId = "session-1",
                cartridgeId = "settings-pserver-joystick",
                cartridgeVersion = 1,
                descriptorJson = "{\"zones\":2}",
                snapshot = ProbeSnapshot(mapOf("color" to "#FF010203,#FF040506", "brightness" to "0.7")),
            )

        assertTrue(store.saveRollback(record))

        assertEquals(record, store.loadRollback())
    }

    @Test
    fun `failed durable write is reported and cannot look successful`() {
        val store = HardwareLearningStore(read = { null }, write = { _, _ -> false }, remove = { true })

        assertFalse(
            store.saveRollback(
                RollbackRecord("s", "singleadc-joypad", 1, "{}", ProbeSnapshot(mapOf("red" to "0"))),
            ),
        )
        assertNull(store.loadRollback())
    }

    @Test
    fun `malformed persisted records fail closed`() {
        val store = HardwareLearningStore(read = { "not-json" }, write = { _, _ -> true }, remove = { true })

        assertNull(store.loadRollback())
        assertNull(store.loadBinding())
    }

    @Test
    fun `learned binding round trip keeps only confirmed capabilities`() {
        val values = mutableMapOf<String, String>()
        val store = HardwareLearningStore(values::get, { key, value -> values.set(key, value).let { true } }, { values.remove(it) != null })
        val binding =
            LearnedDeviceBinding(
                identityHash = "a".repeat(64),
                cartridgeId = "android-sysfs-multicolor",
                cartridgeVersion = 1,
                descriptorJson = "{\"kind\":\"rgb\"}",
                capabilities = DeviceCapabilities(color = true, brightness = false, perZone = false, zones = 1),
                appVersion = "0.1.0",
                learnedAtEpochMs = 1234L,
            )

        assertTrue(store.saveBinding(binding))

        assertEquals(binding, store.loadBinding())
    }

    @Test
    fun `clear removes rollback and learned binding independently`() {
        val values = mutableMapOf("rollback" to "r", "binding" to "b")
        val store = HardwareLearningStore(values::get, { key, value -> values.set(key, value).let { true } }, { values.remove(it) != null })

        assertTrue(store.clearRollback())
        assertNull(values["rollback"])
        assertEquals("b", values["binding"])
        assertTrue(store.clearBinding())
        assertNull(values["binding"])
    }

    @Test
    fun `failed learning attempt survives process restart with its evidence`() {
        val values = mutableMapOf<String, String>()
        val store = HardwareLearningStore(values::get, { key, value -> values.set(key, value).let { true } }, { values.remove(it) != null })
        val candidate =
            ProbeCandidate(
                cartridgeId = "android-sysfs-multicolor",
                cartridgeVersion = 1,
                surface = ProbeSurface.SYSFS_RGB,
                descriptor = SysfsRgbDescriptor("/sys/class/leds/gamepad", 1, 255, SysfsColorKind.MULTI_INTENSITY_HEX),
                signalKeys = setOf("multi_intensity"),
            )
        val result =
            HardwareLearningResult(
                status = HardwareLearningStatus.BLOCKED,
                candidate = candidate,
                evidence =
                    listOf(
                        ProbeEvidence(ProbeStep.COLOR, null, EvidenceLevel.TRANSPORT_CONFIRMED, null),
                        ProbeEvidence(ProbeStep.COLOR, null, EvidenceLevel.NOT_OBSERVED, UserObservation.NO),
                    ),
                capabilities = DeviceCapabilities(false, false, false, 1, false),
                rollbackStatus = RollbackStatus.RESTORED_AND_READ_BACK,
            )
        val attempt = HardwareLearningAttempt("b".repeat(64), listOf(result), "0.1.0", 4567L)

        assertTrue(store.saveAttempt(attempt))

        assertEquals(attempt, HardwareLearningStore(values::get, { _, _ -> false }, { false }).loadAttempt())
        assertTrue(store.clearAttempt())
        assertNull(store.loadAttempt())
    }

    @Test
    fun `persisted attempt is restored only for the same hardware identity`() {
        val identity = AndroidDeviceIdentity("Odin2 Portal", "kalama", "AYN", emptyMap())
        val result =
            HardwareLearningResult(
                status = HardwareLearningStatus.BLOCKED,
                candidate =
                    ProbeCandidate(
                        "android-sysfs-multicolor",
                        1,
                        ProbeSurface.SYSFS_RGB,
                        SysfsRgbDescriptor("/sys/class/leds/gamepad", 1, 255, SysfsColorKind.MULTI_INTENSITY_HEX),
                        emptySet(),
                    ),
                evidence = emptyList(),
                capabilities = DeviceCapabilities(false, false, false, 1, false),
                rollbackStatus = RollbackStatus.RESTORED_AND_READ_BACK,
            )
        val attempt = HardwareLearningAttempt(learningIdentityHash(identity), listOf(result), "0.1.0", 4567L)

        assertEquals(listOf(result), attempt.resultsFor(identity))
        assertTrue(attempt.resultsFor(identity.copy(model = "Other")).isEmpty())
    }

    @Test
    fun `identity hash is stable across property order and contains no raw identity`() {
        val first =
            AndroidDeviceIdentity(
                model = "Thor",
                device = "kalama",
                manufacturer = "AYN",
                productProperties = linkedMapOf("ro.product.board" to "board", "ro.product.name" to "thor"),
            )
        val reordered = first.copy(productProperties = first.productProperties.toList().reversed().toMap())

        val hash = learningIdentityHash(first)

        assertEquals(hash, learningIdentityHash(reordered))
        assertTrue(hash.matches(Regex("[0-9a-f]{64}")))
        assertFalse(hash.contains("thor", ignoreCase = true))
    }
}
