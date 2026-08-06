package com.hooandee.colores.audio

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class AudioCaptureSession(
    private val scope: CoroutineScope,
    private val source: MutableAudioLevelSource,
    private val silentReadLimit: Int = 16,
    private val bufferSize: Int = 1_024,
) {
    private var job: Job? = null
    private var activeCapture: PlaybackCapture? = null

    val running: Boolean
        get() = job?.isActive == true

    fun start(
        capture: PlaybackCapture,
        onFailure: (Throwable) -> Unit = {},
    ) {
        closeActive()
        activeCapture = capture
        source.reset(AudioCaptureStatus.STARTING)
        job =
            scope.launch {
                var terminalStatus: AudioCaptureStatus? = null
                try {
                    capture.start()
                    val buffer = ShortArray(bufferSize)
                    var displayed = 0.0
                    var silentReads = 0
                    while (true) {
                        val count = capture.read(buffer)
                        check(count >= 0) { "AudioRecord read failed: $count" }
                        val target = PcmLevelMeter.level(buffer, count)
                        displayed = PcmLevelMeter.ease(displayed, target)
                        silentReads = if (target == 0.0) silentReads + 1 else 0
                        val status =
                            if (silentReads >= silentReadLimit.coerceAtLeast(1)) {
                                AudioCaptureStatus.NO_AUDIO
                            } else {
                                AudioCaptureStatus.CAPTURING
                            }
                        source.update(displayed, status)
                    }
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Throwable) {
                    terminalStatus = AudioCaptureStatus.ERROR
                    onFailure(error)
                } finally {
                    if (activeCapture === capture) {
                        activeCapture = null
                        capture.close()
                        job = null
                        terminalStatus?.let(source::reset)
                    }
                }
            }
    }

    fun stop(status: AudioCaptureStatus) {
        val hadActive = activeCapture != null || job != null
        closeActive()
        if (hadActive || source.state.value.status != status) source.reset(status)
    }

    private fun closeActive() {
        val capture = activeCapture
        activeCapture = null
        job?.cancel()
        job = null
        capture?.close()
    }
}
