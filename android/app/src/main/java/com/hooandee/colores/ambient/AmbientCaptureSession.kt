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
)

object AmbientCaptureCadence {
    fun shouldProcess(
        lastFrameMs: Long,
        nowMs: Long,
        fps: Int,
    ): Boolean = lastFrameMs == 0L || nowMs - lastFrameMs >= 1_000L / fps.normalizedAmbientCaptureFps()
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

    fun stop(status: AmbientCaptureStatus) {
        capture?.stop()
        capture = null
        source.reset(status)
    }

    fun updateConfig(config: AmbientCaptureConfig) {
        capture?.updateConfig(config)
    }
}
