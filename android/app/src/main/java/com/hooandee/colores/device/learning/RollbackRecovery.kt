package com.hooandee.colores.device.learning

import com.hooandee.colores.led.Htr3212Descriptor
import com.hooandee.colores.led.SettingsProviderDescriptor

internal const val MAX_ROLLBACK_RECOVERY_ATTEMPTS = 3

enum class LearningRecovery {
    BUSY,
    NOTHING_TO_RESTORE,
    RESTORED,
    FAILED,
}

internal fun RollbackStatus?.toLearningRecovery(): LearningRecovery =
    when (this) {
        null -> LearningRecovery.NOTHING_TO_RESTORE
        RollbackStatus.RESTORE_FAILED -> LearningRecovery.FAILED
        else -> LearningRecovery.RESTORED
    }

class RollbackRecovery(
    private val store: HardwareLearningStore,
    private val catalog: ProbeCartridgeCatalog,
    private val topologyReader: I2cTopologyReader? = null,
    private val maxAttempts: Int = MAX_ROLLBACK_RECOVERY_ATTEMPTS,
) {
    val pending: Boolean
        get() = store.hasRollback()

    val failure: RollbackFailure?
        get() = store.loadRollbackFailure()

    fun recover(): RollbackStatus? {
        if (!store.hasRollback()) return null
        val (status, reason) = attempt()
        if (reason == null) return status
        val attempts = store.recordRollbackFailure(reason)
        if (attempts >= maxAttempts) store.archiveRollback(reason)
        return RollbackStatus.RESTORE_FAILED
    }

    fun discard(): Boolean = store.archiveRollback(RollbackFailureReason.DISCARDED)

    private fun attempt(): Pair<RollbackStatus, RollbackFailureReason?> {
        val record = store.loadRollback() ?: return failed(RollbackFailureReason.CORRUPT_JOURNAL)
        val cartridge = catalog.find(record.cartridgeId, record.cartridgeVersion) ?: return failed(RollbackFailureReason.UNKNOWN_CARTRIDGE)
        val descriptor = decodeLearningDescriptor(record.descriptorJson) ?: return failed(RollbackFailureReason.INVALID_DESCRIPTOR)
        val candidate =
            ProbeCandidate(
                cartridgeId = record.cartridgeId,
                cartridgeVersion = record.cartridgeVersion,
                surface = cartridge.surface,
                descriptor = descriptor,
                signalKeys = emptySet(),
            )
        if (!runCatching { cartridge.accepts(candidate) }.getOrDefault(false)) return failed(RollbackFailureReason.REJECTED_CANDIDATE)
        val hardware = (descriptor as? SettingsProviderDescriptor)?.htr3212
        if (hardware != null && topologyContradicts(hardware)) {
            runCatching { cartridge.restoreSettingsOnly(candidate, record.snapshot) }
            return failed(RollbackFailureReason.TOPOLOGY_MISMATCH)
        }
        val status = runCatching { cartridge.restore(candidate, record.snapshot) }.getOrDefault(RollbackStatus.RESTORE_FAILED)
        if (status == RollbackStatus.RESTORE_FAILED) return failed(RollbackFailureReason.RESTORE_FAILED)
        if (!store.clearRollback()) return failed(RollbackFailureReason.JOURNAL_NOT_CLEARED)
        return status to null
    }

    private fun topologyContradicts(hardware: Htr3212Descriptor): Boolean {
        val controllers = topologyReader?.let { reader -> runCatching { reader.read() }.getOrNull() }.orEmpty()
        if (controllers.isEmpty()) return false
        val left = controllers.any { it.driver == HTR_LEFT_DRIVER && it.bus == hardware.leftBus && it.address == hardware.address }
        val right = controllers.any { it.driver == HTR_RIGHT_DRIVER && it.bus == hardware.rightBus && it.address == hardware.address }
        return !(left && right)
    }

    private fun failed(reason: RollbackFailureReason): Pair<RollbackStatus, RollbackFailureReason?> = RollbackStatus.RESTORE_FAILED to reason

    private companion object {
        const val HTR_LEFT_DRIVER = "htr3212l"
        const val HTR_RIGHT_DRIVER = "htr3212r"
    }
}

suspend fun restoreAfterLearningRollback(
    recover: suspend () -> RollbackStatus?,
    restoreRuntime: suspend () -> Boolean,
): Boolean {
    if (recover() == RollbackStatus.RESTORE_FAILED) return false
    return restoreRuntime()
}
