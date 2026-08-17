package com.hooandee.colores.ambient

import android.content.Context
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.view.WindowManager
import com.hooandee.colores.led.RgbColor
import kotlin.math.roundToInt

internal data class AmbientCaptureDimensions(
    val width: Int,
    val height: Int,
)

internal fun ambientCaptureDimensions(
    displayWidth: Int,
    displayHeight: Int,
    maxLongEdge: Int = 32,
): AmbientCaptureDimensions {
    if (displayWidth <= 0 || displayHeight <= 0 || maxLongEdge <= 0) return AmbientCaptureDimensions(32, 18)
    return if (displayWidth >= displayHeight) {
        AmbientCaptureDimensions(
            width = maxLongEdge,
            height = (maxLongEdge * displayHeight.toFloat() / displayWidth).roundToInt().coerceAtLeast(1),
        )
    } else {
        AmbientCaptureDimensions(
            width = (maxLongEdge * displayWidth.toFloat() / displayHeight).roundToInt().coerceAtLeast(1),
            height = maxLongEdge,
        )
    }
}

class AndroidScreenCapture(
    private val context: Context,
    private val projection: MediaProjection,
    initialConfig: AmbientCaptureConfig,
    private val onFrame: (List<RgbColor>, Long) -> Unit,
    private val onNoFrames: () -> Unit,
    private val onError: (Throwable) -> Unit,
) {
    @Volatile
    private var config = initialConfig
    private var thread: HandlerThread? = null
    private var handler: Handler? = null
    private var reader: ImageReader? = null
    private var display: VirtualDisplay? = null
    private var lastProcessedMs = 0L
    @Volatile
    private var running = false

    private val watchdog =
        object : Runnable {
            override fun run() {
                if (!running) return
                val now = SystemClock.elapsedRealtime()
                if (lastProcessedMs == 0L || now - lastProcessedMs >= NO_FRAME_TIMEOUT_MS) onNoFrames()
                handler?.postDelayed(this, NO_FRAME_TIMEOUT_MS)
            }
        }

    fun start() {
        check(!running)
        running = true
        val captureThread = HandlerThread("ColoresAmbientCapture").apply { start() }
        val captureHandler = Handler(captureThread.looper)
        val displayBounds = context.getSystemService(WindowManager::class.java).currentWindowMetrics.bounds
        val captureDimensions = ambientCaptureDimensions(displayBounds.width(), displayBounds.height())
        val imageReader =
            ImageReader.newInstance(
                captureDimensions.width,
                captureDimensions.height,
                PixelFormat.RGBA_8888,
                MAX_IMAGES,
            )
        thread = captureThread
        handler = captureHandler
        reader = imageReader
        imageReader.setOnImageAvailableListener({ available -> consumeLatest(available) }, captureHandler)
        display =
            projection.createVirtualDisplay(
                "ColoresAmbient",
                captureDimensions.width,
                captureDimensions.height,
                context.resources.displayMetrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader.surface,
                null,
                captureHandler,
            )
        captureHandler.postDelayed(watchdog, NO_FRAME_TIMEOUT_MS)
    }

    fun stop() {
        if (!running) return
        running = false
        handler?.removeCallbacksAndMessages(null)
        reader?.setOnImageAvailableListener(null, null)
        display?.release()
        reader?.close()
        thread?.quitSafely()
        display = null
        reader = null
        handler = null
        thread = null
        lastProcessedMs = 0L
    }

    fun updateConfig(config: AmbientCaptureConfig) {
        this.config = config
    }

    private fun consumeLatest(available: ImageReader) {
        val image = runCatching { available.acquireLatestImage() }.getOrElse {
            onError(it)
            return
        } ?: return
        try {
            val now = SystemClock.elapsedRealtime()
            val activeConfig = config
            if (!AmbientCaptureCadence.shouldProcess(lastProcessedMs, now, activeConfig.captureFps)) return
            val plane = image.planes.firstOrNull() ?: return
            val buffer = plane.buffer
            buffer.rewind()
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)
            val colors =
                AmbientSampler.sample(
                    frame = AmbientPixelFrame(image.width, image.height, plane.pixelStride, plane.rowStride, bytes),
                    zones = activeConfig.zones,
                    gridLayout = activeConfig.gridLayout,
                    supportsPerZone = activeConfig.supportsPerZone,
                    mode = activeConfig.samplingMode,
                )
            lastProcessedMs = now
            onFrame(colors, now)
        } catch (error: Throwable) {
            onError(error)
        } finally {
            image.close()
        }
    }

    private companion object {
        const val MAX_IMAGES = 2
        const val NO_FRAME_TIMEOUT_MS = 2_000L
    }
}
