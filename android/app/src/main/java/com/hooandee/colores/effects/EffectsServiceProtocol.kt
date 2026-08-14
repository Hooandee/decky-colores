package com.hooandee.colores.effects

import com.hooandee.colores.control.AppMode

internal const val ACTION_START_AUDIO = "com.hooandee.colores.action.START_AUDIO"
internal const val ACTION_STOP_AUDIO = "com.hooandee.colores.action.STOP_AUDIO"
internal const val ACTION_START_AMBIENT = "com.hooandee.colores.action.START_AMBIENT"
internal const val ACTION_STOP_AMBIENT = "com.hooandee.colores.action.STOP_AMBIENT"
internal const val ACTION_UPDATE_AMBIENT = "com.hooandee.colores.action.UPDATE_AMBIENT"
internal const val ACTION_RESTORE = "com.hooandee.colores.action.RESTORE"

internal enum class EffectsServiceCommand {
    START_AUDIO,
    STOP_AUDIO,
    START_AMBIENT,
    STOP_AMBIENT,
    UPDATE_AMBIENT,
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

internal fun foregroundServiceTypes(
    sdk: Int,
    mediaProjection: Boolean,
): Int {
    val projection = if (mediaProjection) 0x20 else 0
    val specialUse = if (sdk >= 34) 0x40000000 else 0
    return projection or specialUse
}

internal fun effectsServiceCommandPolicy(command: EffectsServiceCommand): EffectsServiceCommandPolicy =
    EffectsServiceCommandPolicy(
        startMode =
            if (
                command == EffectsServiceCommand.STOP_AUDIO ||
                    command == EffectsServiceCommand.STOP_AMBIENT ||
                    command == EffectsServiceCommand.UPDATE_AMBIENT
            ) {
                EffectsServiceStartMode.REGULAR
            } else {
                EffectsServiceStartMode.FOREGROUND
            },
        reconcileController = command == EffectsServiceCommand.STOP_AUDIO || command == EffectsServiceCommand.STOP_AMBIENT,
    )

internal fun shouldReconcileAudioController(
    requested: Boolean,
    mode: AppMode,
): Boolean = requested && mode == AppMode.AUDIO

internal fun shouldReconcileAmbientController(
    requested: Boolean,
    mode: AppMode,
): Boolean = requested && mode == AppMode.AMBIENT

internal fun resolveEffectsServiceCommand(
    intentPresent: Boolean,
    action: String?,
): EffectsServiceCommand =
    when {
        !intentPresent -> EffectsServiceCommand.RESTORE
        action == ACTION_START_AUDIO -> EffectsServiceCommand.START_AUDIO
        action == ACTION_STOP_AUDIO -> EffectsServiceCommand.STOP_AUDIO
        action == ACTION_START_AMBIENT -> EffectsServiceCommand.START_AMBIENT
        action == ACTION_STOP_AMBIENT -> EffectsServiceCommand.STOP_AMBIENT
        action == ACTION_UPDATE_AMBIENT -> EffectsServiceCommand.UPDATE_AMBIENT
        action == ACTION_RESTORE -> EffectsServiceCommand.RESTORE
        else -> EffectsServiceCommand.KEEP_ALIVE
    }
