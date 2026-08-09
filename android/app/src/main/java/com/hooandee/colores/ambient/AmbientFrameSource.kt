package com.hooandee.colores.ambient

import com.hooandee.colores.led.RgbColor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AmbientCaptureStatus {
    AUTHORIZATION_REQUIRED,
    STARTING,
    CAPTURING,
    NO_FRAMES,
    REVOKED,
    ERROR,
}

internal val AmbientCaptureStatus.keepsCaptureActive: Boolean
    get() = this == AmbientCaptureStatus.STARTING || this == AmbientCaptureStatus.CAPTURING || this == AmbientCaptureStatus.NO_FRAMES

internal val AmbientCaptureStatus.needsAuthorization: Boolean
    get() = this == AmbientCaptureStatus.AUTHORIZATION_REQUIRED || this == AmbientCaptureStatus.REVOKED || this == AmbientCaptureStatus.ERROR

data class AmbientFrameState(
    val colors: List<RgbColor> = emptyList(),
    val status: AmbientCaptureStatus = AmbientCaptureStatus.AUTHORIZATION_REQUIRED,
    val timestampMs: Long = 0L,
)

interface AmbientFrameSource {
    val state: StateFlow<AmbientFrameState>
}

class MutableAmbientFrameSource(
    initial: AmbientFrameState = AmbientFrameState(),
) : AmbientFrameSource {
    private val mutableState = MutableStateFlow(initial)
    override val state: StateFlow<AmbientFrameState> = mutableState.asStateFlow()

    fun update(
        colors: List<RgbColor>,
        status: AmbientCaptureStatus,
        timestampMs: Long,
    ) {
        mutableState.value = AmbientFrameState(colors, status, timestampMs)
    }

    fun reset(status: AmbientCaptureStatus) {
        mutableState.value = AmbientFrameState(status = status)
    }

    fun setStatus(status: AmbientCaptureStatus) {
        mutableState.value = mutableState.value.copy(status = status)
    }
}
