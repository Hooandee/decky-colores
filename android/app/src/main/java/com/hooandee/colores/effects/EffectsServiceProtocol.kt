package com.hooandee.colores.effects

import com.hooandee.colores.control.AppMode

internal const val ACTION_START_AUDIO = "com.hooandee.colores.action.START_AUDIO"
internal const val ACTION_STOP_AUDIO = "com.hooandee.colores.action.STOP_AUDIO"
internal const val ACTION_RESTORE = "com.hooandee.colores.action.RESTORE"

internal enum class EffectsServiceCommand {
    START_AUDIO,
    STOP_AUDIO,
    RESTORE,
    KEEP_ALIVE,
}

internal enum class EffectsServiceStartMode {
    FOREGROUND,
    REGULAR,
}

internal data class EffectsServiceCommandPolicy(
    val startMode: EffectsServiceStartMode,
    val reconcileController: Boolean,
)

internal fun effectsServiceCommandPolicy(command: EffectsServiceCommand): EffectsServiceCommandPolicy =
    EffectsServiceCommandPolicy(
        startMode =
            if (command == EffectsServiceCommand.STOP_AUDIO) {
                EffectsServiceStartMode.REGULAR
            } else {
                EffectsServiceStartMode.FOREGROUND
            },
        reconcileController = command == EffectsServiceCommand.STOP_AUDIO,
    )

internal fun shouldReconcileAudioController(
    requested: Boolean,
    mode: AppMode,
): Boolean = requested && mode == AppMode.AUDIO

internal fun resolveEffectsServiceCommand(
    intentPresent: Boolean,
    action: String?,
): EffectsServiceCommand =
    when {
        !intentPresent -> EffectsServiceCommand.RESTORE
        action == ACTION_START_AUDIO -> EffectsServiceCommand.START_AUDIO
        action == ACTION_STOP_AUDIO -> EffectsServiceCommand.STOP_AUDIO
        action == ACTION_RESTORE -> EffectsServiceCommand.RESTORE
        else -> EffectsServiceCommand.KEEP_ALIVE
    }
