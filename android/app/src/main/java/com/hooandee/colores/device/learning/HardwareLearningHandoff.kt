package com.hooandee.colores.device.learning

import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext

data class HardwareLearningCancellation(
    val status: RollbackStatus?,
    val state: HardwareLearningState,
)

class HardwareLearningHandoff(
    private val scope: CoroutineScope,
    private val coordinator: HardwareLearningCoordinator,
    private val recoverRollback: () -> RollbackStatus?,
    private val io: CoroutineContext = Dispatchers.IO,
) {
    @Volatile
    var session: HardwareLearningSession? = null

    @Volatile
    private var cancellation: Deferred<HardwareLearningCancellation>? = null

    fun cancel(pending: Job?): Deferred<HardwareLearningCancellation> =
        scope.async(io + NonCancellable) {
            pending?.join()
            val current = session
            val status = coordinator.finish { current?.cancel() }
            if (session === current) session = null
            HardwareLearningCancellation(status, current?.state ?: HardwareLearningState.Idle)
        }.also { cancellation = it }

    suspend fun awaitCancellation() {
        cancellation?.await()
    }

    suspend fun recover(): LearningRecovery {
        awaitCancellation()
        return coordinator.whenIdle { withContext(io) { recoverRollback() }.toLearningRecovery() } ?: LearningRecovery.BUSY
    }
}
