package com.hooandee.colores.audio

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AudioCaptureStatus {
    AUTHORIZATION_REQUIRED,
    STARTING,
    CAPTURING,
    NO_AUDIO,
    REVOKED,
    ERROR,
}

data class AudioLevelState(
    val level: Double = 0.0,
    val status: AudioCaptureStatus = AudioCaptureStatus.AUTHORIZATION_REQUIRED,
)

interface AudioLevelSource {
    val state: StateFlow<AudioLevelState>
}

class MutableAudioLevelSource(
    initial: AudioLevelState = AudioLevelState(),
) : AudioLevelSource {
    private val mutableState = MutableStateFlow(initial)
    override val state: StateFlow<AudioLevelState> = mutableState.asStateFlow()

    fun update(
        level: Double,
        status: AudioCaptureStatus,
    ) {
        mutableState.value = AudioLevelState(level.coerceIn(0.0, 1.0), status)
    }

    fun reset(status: AudioCaptureStatus) {
        mutableState.value = AudioLevelState(status = status)
    }
}
