package com.hooandee.colores.ambient

import android.content.Context
import android.media.projection.MediaProjection
import com.hooandee.colores.device.LedGridCell

data class AmbientCaptureConfig(
    val zones: Int,
    val gridLayout: List<LedGridCell>?,
    val supportsPerZone: Boolean,
    val captureFps: Int,
    val samplingMode: AmbientSamplingMode,
    val audio: Boolean = false,
)

object AmbientCaptureCadence {
    fun shouldProcess(
        lastFrameMs: Long,
        nowMs: Long,
        fps: Int,
    ): Boolean = delayUntilDueMs(lastFrameMs, nowMs, fps) == 0L

    fun delayUntilDueMs(
        lastFrameMs: Long,
        nowMs: Long,
        fps: Int,
    ): Long =
        if (lastFrameMs == 0L) {
            0L
        } else {
            (1_000L / fps.normalizedAmbientCaptureFps() - (nowMs - lastFrameMs)).coerceAtLeast(0L)
        }
}

internal fun Int.normalizedAmbientCaptureFps(): Int = ((coerceIn(5, 30) + 2) / 5) * 5

class AmbientCaptureSession(
    private val context: Context,
    private val source: MutableAmbientFrameSource,
) {
    private var capture: AndroidScreenCapture? = null

    fun start(
        projection: MediaProjection,
        config: AmbientCaptureConfig,
        onError: (Throwable) -> Unit,
    ) {
        stop(AmbientCaptureStatus.STARTING)
        val active =
            AndroidScreenCapture(
                context = context,
                projection = projection,
                initialConfig = config,
                onFrame = { colors, timestampMs ->
                    source.update(colors, AmbientCaptureStatus.CAPTURING, timestampMs)
                },
                onNoFrames = { source.setStatus(AmbientCaptureStatus.NO_FRAMES) },
                onError = onError,
            )
        capture = active
        active.start()
    }

    val live: Boolean
        get() = capture != null

    fun pause(status: AmbientCaptureStatus) {
        capture?.setPaused(true)
        source.reset(status)
    }

    fun resume(config: AmbientCaptureConfig?): Boolean {
        val active = capture ?: return false
        config?.let(active::updateConfig)
        source.reset(AmbientCaptureStatus.STARTING)
        active.setPaused(false)
        return true
    }

    fun stop(status: AmbientCaptureStatus) {
        capture?.stop()
        capture = null
        source.reset(status)
    }

    fun updateConfig(config: AmbientCaptureConfig) {
        capture?.updateConfig(config)
    }
}
