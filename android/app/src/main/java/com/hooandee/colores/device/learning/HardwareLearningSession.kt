package com.hooandee.colores.device.learning

import com.hooandee.colores.device.AndroidDeviceIdentity
import com.hooandee.colores.device.DeviceCapabilities
import java.util.UUID

enum class LearningBlockReason {
    UNSUPPORTED_CANDIDATE,
    SNAPSHOT_UNAVAILABLE,
    JOURNAL_UNAVAILABLE,
    WRITE_FAILED,
    RESTORE_FAILED,
    BINDING_UNAVAILABLE,
}

enum class HardwareLearningStatus {
    ADAPTED,
    BLOCKED,
    RESTORE_FAILED,
    CANCELLED,
}

data class ProbeEvidence(
    val step: ProbeStep,
    val zone: Int?,
    val level: EvidenceLevel,
    val observation: UserObservation?,
    val location: ZoneLocation? = null,
)

data class HardwareLearningResult(
    val status: HardwareLearningStatus,
    val candidate: ProbeCandidate,
    val evidence: List<ProbeEvidence>,
    val capabilities: DeviceCapabilities,
    val rollbackStatus: RollbackStatus,
)

sealed interface HardwareLearningState {
    data object Idle : HardwareLearningState

    data class ConsentRequired(
        val candidate: ProbeCandidate,
    ) : HardwareLearningState

    data class Ready(
        val candidate: ProbeCandidate,
        val supportedSteps: List<ProbeStep>,
        val evidence: List<ProbeEvidence>,
    ) : HardwareLearningState

    data class AwaitingAnswer(
        val candidate: ProbeCandidate,
        val supportedSteps: List<ProbeStep>,
        val step: ProbeStep,
        val zone: Int?,
        val evidence: List<ProbeEvidence>,
    ) : HardwareLearningState

    data class Complete(
        val result: HardwareLearningResult,
    ) : HardwareLearningState

    data class Blocked(
        val reason: LearningBlockReason,
    ) : HardwareLearningState
}

class HardwareLearningSession(
    private val identity: AndroidDeviceIdentity,
    private val catalog: ProbeCartridgeCatalog,
    private val store: HardwareLearningStore,
    private val appVersion: String,
    private val nowEpochMs: () -> Long = System::currentTimeMillis,
    private val newSessionId: () -> String = { UUID.randomUUID().toString() },
) {
    var state: HardwareLearningState = HardwareLearningState.Idle
        private set

    private var candidate: ProbeCandidate? = null
    private var cartridge: ProbeCartridge? = null
    private var snapshot: ProbeSnapshot? = null
    private val evidence = mutableListOf<ProbeEvidence>()

    fun start(candidate: ProbeCandidate): HardwareLearningState {
        val resolved = catalog.find(candidate.cartridgeId, candidate.cartridgeVersion)
        if (resolved == null || !runCatching { resolved.accepts(candidate) }.getOrDefault(false)) {
            state = HardwareLearningState.Blocked(LearningBlockReason.UNSUPPORTED_CANDIDATE)
            return state
        }
        this.candidate = candidate
        cartridge = resolved
        snapshot = null
        evidence.clear()
        state = HardwareLearningState.ConsentRequired(candidate)
        return state
    }

    fun consent(): HardwareLearningState {
        val currentCandidate = candidate ?: return state
        val currentCartridge = cartridge ?: return state
        if (state !is HardwareLearningState.ConsentRequired) return state
        val captured = runCatching { currentCartridge.snapshot(currentCandidate) }.getOrNull()
            ?: return block(LearningBlockReason.SNAPSHOT_UNAVAILABLE)
        val record =
            RollbackRecord(
                sessionId = newSessionId(),
                cartridgeId = currentCartridge.id,
                cartridgeVersion = currentCartridge.version,
                descriptorJson = encodeLearningDescriptor(currentCandidate.descriptor),
                snapshot = captured,
            )
        if (!store.saveRollback(record)) return block(LearningBlockReason.JOURNAL_UNAVAILABLE)
        snapshot = captured
        val steps = runCatching { currentCartridge.supportedSteps(currentCandidate) }.getOrDefault(emptyList())
        if (steps.isEmpty()) {
            restoreAndBlock(LearningBlockReason.UNSUPPORTED_CANDIDATE)
            return state
        }
        state = HardwareLearningState.Ready(currentCandidate, steps, evidence.toList())
        return state
    }

    fun run(
        step: ProbeStep,
        zone: Int? = null,
    ): Boolean {
        val ready = state as? HardwareLearningState.Ready ?: return false
        val currentCandidate = candidate ?: return false
        val currentCartridge = cartridge ?: return false
        if (step !in ready.supportedSteps) return false
        if (!runCatching { currentCartridge.execute(currentCandidate, step, zone) }.getOrDefault(false)) {
            evidence += ProbeEvidence(step, zone, EvidenceLevel.INCONCLUSIVE, null)
            restoreAndBlock(LearningBlockReason.WRITE_FAILED)
            return false
        }
        evidence += ProbeEvidence(step, zone, EvidenceLevel.TRANSPORT_CONFIRMED, null)
        state = HardwareLearningState.AwaitingAnswer(currentCandidate, ready.supportedSteps, step, zone, evidence.toList())
        return true
    }

    fun answer(
        observation: UserObservation,
        location: ZoneLocation? = null,
    ): HardwareLearningState {
        val awaiting = state as? HardwareLearningState.AwaitingAnswer ?: return state
        evidence +=
            ProbeEvidence(
                step = awaiting.step,
                zone = awaiting.zone,
                level =
                    when (observation) {
                        UserObservation.YES -> EvidenceLevel.USER_CONFIRMED
                        UserObservation.NO -> EvidenceLevel.NOT_OBSERVED
                        UserObservation.UNSURE -> EvidenceLevel.INCONCLUSIVE
                    },
                observation = observation,
                location = location.takeIf { observation == UserObservation.YES && awaiting.step == ProbeStep.ZONE },
            )
        state = HardwareLearningState.Ready(awaiting.candidate, awaiting.supportedSteps, evidence.toList())
        return state
    }

    fun finish(): HardwareLearningResult {
        val currentCandidate = requireNotNull(candidate)
        val currentCartridge = requireNotNull(cartridge)
        val rollbackStatus = restoreOriginal()
        val capabilities = confirmedCapabilities()
        if (rollbackStatus == RollbackStatus.RESTORE_FAILED) {
            val result = HardwareLearningResult(HardwareLearningStatus.RESTORE_FAILED, currentCandidate, evidence.toList(), capabilities, rollbackStatus)
            state = HardwareLearningState.Complete(result)
            return result
        }
        if (!store.clearRollback()) {
            val result = HardwareLearningResult(HardwareLearningStatus.BLOCKED, currentCandidate, evidence.toList(), capabilities, rollbackStatus)
            state = HardwareLearningState.Blocked(LearningBlockReason.JOURNAL_UNAVAILABLE)
            return result
        }
        val bindingCandidate = currentCartridge.bindingCandidate(currentCandidate, evidence.toList())
        if (!currentCartridge.canBind(bindingCandidate, capabilities, evidence.toList())) {
            val result = HardwareLearningResult(HardwareLearningStatus.BLOCKED, bindingCandidate, evidence.toList(), capabilities, rollbackStatus)
            state = HardwareLearningState.Complete(result)
            return result
        }
        val binding =
            LearnedDeviceBinding(
                identityHash = learningIdentityHash(identity),
                cartridgeId = bindingCandidate.cartridgeId,
                cartridgeVersion = bindingCandidate.cartridgeVersion,
                descriptorJson = encodeLearningDescriptor(bindingCandidate.descriptor),
                capabilities = capabilities,
                appVersion = appVersion,
                learnedAtEpochMs = nowEpochMs(),
            )
        val status = if (store.saveBinding(binding)) HardwareLearningStatus.ADAPTED else HardwareLearningStatus.BLOCKED
        val result = HardwareLearningResult(status, bindingCandidate, evidence.toList(), capabilities, rollbackStatus)
        state =
            if (status == HardwareLearningStatus.ADAPTED) {
                HardwareLearningState.Complete(result)
            } else {
                HardwareLearningState.Blocked(LearningBlockReason.BINDING_UNAVAILABLE)
            }
        return result
    }

    fun cancel(): RollbackStatus? {
        if (snapshot == null) {
            state = HardwareLearningState.Idle
            return null
        }
        val status = restoreOriginal()
        state =
            when {
                status == RollbackStatus.RESTORE_FAILED -> HardwareLearningState.Blocked(LearningBlockReason.RESTORE_FAILED)
                !store.clearRollback() -> HardwareLearningState.Blocked(LearningBlockReason.JOURNAL_UNAVAILABLE)
                else -> HardwareLearningState.Idle
            }
        return status
    }

    private fun confirmedCapabilities(): DeviceCapabilities {
        val confirmed = evidence.filter { it.level == EvidenceLevel.USER_CONFIRMED }
        val zones = confirmedZones()
        val expectedZones = candidate?.descriptor?.zoneCount() ?: 1
        val completeTopology = zones == (0 until expectedZones).toList()
        val brightness =
            confirmed.any { it.step == ProbeStep.BRIGHTNESS_LOW } &&
                confirmed.any { it.step == ProbeStep.BRIGHTNESS_HIGH }
        val power =
            confirmed.any { it.step == ProbeStep.POWER_OFF } &&
                confirmed.any { it.step == ProbeStep.POWER_ON }
        return DeviceCapabilities(
            color = confirmed.any { it.step == ProbeStep.COLOR },
            brightness = brightness,
            perZone = completeTopology && expectedZones > 1,
            zones = expectedZones.takeIf { completeTopology && expectedZones > 1 } ?: 1,
            power = power,
        )
    }

    private fun confirmedZones(): List<Int> = confirmedZoneIndices(evidence, candidate?.surface)

    private fun restoreOriginal(): RollbackStatus {
        val currentCandidate = candidate ?: return RollbackStatus.RESTORE_FAILED
        val currentCartridge = cartridge ?: return RollbackStatus.RESTORE_FAILED
        val captured = snapshot ?: return RollbackStatus.RESTORE_FAILED
        return runCatching { currentCartridge.restore(currentCandidate, captured) }
            .getOrDefault(RollbackStatus.RESTORE_FAILED)
    }

    private fun restoreAndBlock(reason: LearningBlockReason) {
        val restored = restoreOriginal()
        val blockReason =
            when {
                restored == RollbackStatus.RESTORE_FAILED -> LearningBlockReason.RESTORE_FAILED
                !store.clearRollback() -> LearningBlockReason.JOURNAL_UNAVAILABLE
                else -> reason
            }
        state = HardwareLearningState.Blocked(blockReason)
    }

    private fun block(reason: LearningBlockReason): HardwareLearningState {
        state = HardwareLearningState.Blocked(reason)
        return state
    }
}

internal fun confirmedZoneIndices(
    evidence: List<ProbeEvidence>,
    surface: ProbeSurface?,
): List<Int> =
    evidence
        .filter {
            it.step == ProbeStep.ZONE &&
                it.level == EvidenceLevel.USER_CONFIRMED &&
                (surface != ProbeSurface.HTR3212 || it.location?.logicalIndex?.let { index -> index >= 0 } == true)
        }
        .mapNotNull(ProbeEvidence::zone)
        .distinct()
        .sorted()

private fun com.hooandee.colores.led.LedDescriptor.zoneCount(): Int =
    when (this) {
        is com.hooandee.colores.led.SettingsProviderDescriptor -> zones
        is com.hooandee.colores.led.SysfsRgbDescriptor -> zones
        is com.hooandee.colores.led.SingleAdcJoypadDescriptor -> 1
    }.coerceIn(1, 32)
