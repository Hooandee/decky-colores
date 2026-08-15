package com.hooandee.colores.device.learning

import com.hooandee.colores.device.AndroidDeviceIdentity
import com.hooandee.colores.device.DeviceCapabilities
import com.hooandee.colores.device.DetectedAndroidDevice
import com.hooandee.colores.led.LedDescriptor

enum class ProbeSurface {
    SETTINGS_PSERVER,
    SINGLEADC_JOYPAD,
    SYSFS_RGB,
    HTR3212,
}

data class ProbeCandidate(
    val cartridgeId: String,
    val cartridgeVersion: Int,
    val surface: ProbeSurface,
    val descriptor: LedDescriptor,
    val signalKeys: Set<String>,
)

enum class ProbeStep {
    COLOR,
    BRIGHTNESS_LOW,
    BRIGHTNESS_HIGH,
    POWER_OFF,
    POWER_ON,
    ZONE,
}

enum class UserObservation {
    YES,
    NO,
    UNSURE,
}

enum class ZoneLocation(
    val stick: Int,
    val logicalIndex: Int,
) {
    LEFT_TOP_LEFT(0, 0),
    LEFT_BOTTOM_LEFT(0, 1),
    LEFT_BOTTOM_RIGHT(0, 2),
    LEFT_TOP_RIGHT(0, 3),
    RIGHT_TOP_LEFT(1, 0),
    RIGHT_BOTTOM_LEFT(1, 1),
    RIGHT_BOTTOM_RIGHT(1, 2),
    RIGHT_TOP_RIGHT(1, 3),
}

enum class EvidenceLevel {
    TRANSPORT_CONFIRMED,
    USER_CONFIRMED,
    NOT_OBSERVED,
    INCONCLUSIVE,
}

enum class RollbackStatus {
    RESTORED_AND_READ_BACK,
    RESTORED_WITHOUT_HARDWARE_READBACK,
    RESTORE_FAILED,
}

data class ProbeSnapshot(
    val values: Map<String, String>,
)

data class RollbackRecord(
    val sessionId: String,
    val cartridgeId: String,
    val cartridgeVersion: Int,
    val descriptorJson: String,
    val snapshot: ProbeSnapshot,
)

data class LearnedDeviceBinding(
    val identityHash: String,
    val cartridgeId: String,
    val cartridgeVersion: Int,
    val descriptorJson: String,
    val capabilities: DeviceCapabilities,
    val appVersion: String,
    val learnedAtEpochMs: Long,
)

data class HardwareLearningAttempt(
    val identityHash: String,
    val results: List<HardwareLearningResult>,
    val appVersion: String,
    val completedAtEpochMs: Long,
)

sealed interface DetectionOutcome {
    val identity: AndroidDeviceIdentity
    val facts: List<HardwareFact>

    data class Resolved(
        override val identity: AndroidDeviceIdentity,
        val device: DetectedAndroidDevice,
        val candidates: List<ProbeCandidate> = emptyList(),
        override val facts: List<HardwareFact> = emptyList(),
    ) : DetectionOutcome

    data class UnavailableKnownDevice(
        override val identity: AndroidDeviceIdentity,
        val device: DetectedAndroidDevice,
        val candidates: List<ProbeCandidate>,
        override val facts: List<HardwareFact> = emptyList(),
    ) : DetectionOutcome

    data class Candidates(
        override val identity: AndroidDeviceIdentity,
        val candidates: List<ProbeCandidate>,
        override val facts: List<HardwareFact> = emptyList(),
    ) : DetectionOutcome

    data class Unsupported(
        override val identity: AndroidDeviceIdentity,
        override val facts: List<HardwareFact> = emptyList(),
    ) : DetectionOutcome
}

internal fun resolveDetectionOutcome(
    identity: AndroidDeviceIdentity,
    exact: DetectedAndroidDevice?,
    exactTransportAvailable: Boolean,
    candidates: List<ProbeCandidate>,
    learned: DetectedAndroidDevice? = null,
    facts: List<HardwareFact> = emptyList(),
): DetectionOutcome =
    when {
        exact != null && exactTransportAvailable -> DetectionOutcome.Resolved(identity, exact, candidates, facts)
        learned != null -> DetectionOutcome.Resolved(identity, learned, candidates, facts)
        exact != null -> DetectionOutcome.UnavailableKnownDevice(identity, exact, candidates, facts)
        candidates.isNotEmpty() -> DetectionOutcome.Candidates(identity, candidates, facts)
        else -> DetectionOutcome.Unsupported(identity, facts)
    }
