package com.hooandee.colores.device.learning

class RollbackRecovery(
    private val store: HardwareLearningStore,
    private val catalog: ProbeCartridgeCatalog,
) {
    fun recover(): RollbackStatus? {
        val record = store.loadRollback() ?: return if (store.hasRollback()) RollbackStatus.RESTORE_FAILED else null
        val cartridge = catalog.find(record.cartridgeId, record.cartridgeVersion) ?: return RollbackStatus.RESTORE_FAILED
        val descriptor = decodeLearningDescriptor(record.descriptorJson) ?: return RollbackStatus.RESTORE_FAILED
        val candidate =
            ProbeCandidate(
                cartridgeId = record.cartridgeId,
                cartridgeVersion = record.cartridgeVersion,
                surface = cartridge.surface,
                descriptor = descriptor,
                signalKeys = emptySet(),
            )
        if (!runCatching { cartridge.accepts(candidate) }.getOrDefault(false)) return RollbackStatus.RESTORE_FAILED
        val status = runCatching { cartridge.restore(candidate, record.snapshot) }.getOrDefault(RollbackStatus.RESTORE_FAILED)
        if (status == RollbackStatus.RESTORE_FAILED) return status
        return status.takeIf { store.clearRollback() } ?: RollbackStatus.RESTORE_FAILED
    }
}

suspend fun restoreAfterLearningRollback(
    recover: suspend () -> RollbackStatus?,
    restoreRuntime: suspend () -> Boolean,
): Boolean {
    if (recover() == RollbackStatus.RESTORE_FAILED) return false
    return restoreRuntime()
}
