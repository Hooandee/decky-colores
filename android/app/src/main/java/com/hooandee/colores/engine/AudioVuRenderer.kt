package com.hooandee.colores.engine

import com.hooandee.colores.audio.AudioCaptureStatus
import com.hooandee.colores.audio.AudioLevelState

class AudioVuRenderer(
    private val zones: Int,
    private val frameIntervalMs: Long,
    private val scale: () -> AudioScale = { AudioScale.DEFAULT },
    private val sensitivityDb: () -> Int = { AudioSensitivity.NORMAL_DB },
    private val state: () -> AudioLevelState,
) : Renderer {
    override fun render(nowSeconds: Double): RenderTick {
        val current = state()
        val level =
            if (current.status == AudioCaptureStatus.CAPTURING) {
                AudioSensitivity.adjust(current.level, sensitivityDb())
            } else {
                0.0
            }
        return RenderTick(Effects.vu(level, zones, scale()), frameIntervalMs)
    }
}
