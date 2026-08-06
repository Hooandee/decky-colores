package com.hooandee.colores.audio

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AudioCaptureSessionTest {
    private class FakeCapture(
        private val chunks: ArrayDeque<ShortArray> = ArrayDeque(),
        private val failure: Throwable? = null,
    ) : PlaybackCapture {
        var starts = 0
        var closes = 0

        override fun start() {
            starts++
        }

        override suspend fun read(buffer: ShortArray): Int {
            failure?.let { throw it }
            val chunk = chunks.removeFirstOrNull() ?: awaitCancellation()
            chunk.copyInto(buffer)
            return chunk.size
        }

        override fun close() {
            closes++
        }
    }

    @Test
    fun `audible pcm publishes the eased level`() =
        runTest {
            val source = MutableAudioLevelSource()
            val capture = FakeCapture(ArrayDeque(listOf(ShortArray(32) { 8_000 })))
            val session = AudioCaptureSession(backgroundScope, source, silentReadLimit = 2)

            session.start(capture)
            runCurrent()

            assertTrue(session.running)
            assertEquals(1, capture.starts)
            assertEquals(AudioCaptureStatus.CAPTURING, source.state.value.status)
            assertEquals(0.4163, source.state.value.level, 0.0001)
        }

    @Test
    fun `sustained zero pcm reports no detectable internal audio`() =
        runTest {
            val source = MutableAudioLevelSource()
            val chunks = ArrayDeque(listOf(ShortArray(16), ShortArray(16)))
            val session = AudioCaptureSession(backgroundScope, source, silentReadLimit = 2)

            session.start(FakeCapture(chunks))
            runCurrent()

            assertEquals(AudioCaptureStatus.NO_AUDIO, source.state.value.status)
            assertEquals(0.0, source.state.value.level, 0.000001)
        }

    @Test
    fun `read failure publishes error and closes capture`() =
        runTest {
            val source = MutableAudioLevelSource()
            val capture = FakeCapture(failure = IllegalStateException("read failed"))
            val session = AudioCaptureSession(backgroundScope, source)
            var terminalError: Throwable? = null

            session.start(capture) { terminalError = it }
            runCurrent()

            assertFalse(session.running)
            assertEquals(AudioCaptureStatus.ERROR, source.state.value.status)
            assertEquals("read failed", terminalError?.message)
            assertEquals(1, capture.closes)
        }

    @Test
    fun `explicit stop is idempotent and preserves the requested terminal status`() =
        runTest {
            val source = MutableAudioLevelSource()
            val capture = FakeCapture()
            val session = AudioCaptureSession(backgroundScope, source)

            session.start(capture)
            runCurrent()
            session.stop(AudioCaptureStatus.REVOKED)
            session.stop(AudioCaptureStatus.REVOKED)
            runCurrent()

            assertFalse(session.running)
            assertEquals(AudioCaptureStatus.REVOKED, source.state.value.status)
            assertEquals(1, capture.closes)
        }
}
