package com.hooandee.colores.led

import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.assertEquals
import org.junit.Test

class ConflatedLedWriterTest {
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `writes immediately then coalesces drag updates`() =
        runTest {
            val written = mutableListOf<Int>()
            val writer = ConflatedLedWriter<Int>(backgroundScope, 80) {
                written += it
                true
            }

            writer.submit(1)
            runCurrent()
            writer.submit(2)
            writer.submit(3)
            writer.submit(4)
            advanceTimeBy(79)
            runCurrent()
            assertEquals(listOf(1), written)

            advanceTimeBy(1)
            runCurrent()
            assertEquals(listOf(1, 4), written)
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `retries a failed latest value after a longer delay`() =
        runTest {
            var failuresRemaining = 1
            val written = mutableListOf<Int>()
            val writer =
                ConflatedLedWriter<Int>(backgroundScope, 80, retryIntervalMs = 500) {
                    if (failuresRemaining-- > 0) {
                        false
                    } else {
                        written += it
                        true
                    }
                }

            writer.submit(7)
            runCurrent()
            assertEquals(emptyList<Int>(), written)

            advanceTimeBy(499)
            runCurrent()
            assertEquals(emptyList<Int>(), written)

            advanceTimeBy(1)
            runCurrent()
            assertEquals(listOf(7), written)
        }
}
