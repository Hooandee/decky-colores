package com.hooandee.colores.device.learning

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class HardwareLearningCoordinator {
    private val mutex = Mutex()
    private var active = false

    suspend fun begin(prepare: suspend () -> Boolean): Boolean =
        mutex.withLock {
            if (active || !runCatching { prepare() }.getOrDefault(false)) return@withLock false
            active = true
            true
        }

    suspend fun <T> run(operation: suspend () -> T): T? =
        mutex.withLock {
            if (active) operation() else null
        }

    suspend fun <T> finish(operation: suspend () -> T): T? =
        mutex.withLock {
            if (!active) return@withLock null
            try {
                operation()
            } finally {
                active = false
            }
        }

    suspend fun <T> whenIdle(operation: suspend () -> T): T? =
        mutex.withLock {
            if (active) null else operation()
        }
}
