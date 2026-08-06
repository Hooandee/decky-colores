package com.hooandee.colores

import android.app.Application
import com.hooandee.colores.audio.AudioCaptureSession
import com.hooandee.colores.audio.MutableAudioLevelSource
import com.hooandee.colores.control.LightingController
import com.hooandee.colores.control.LightingRuntime
import com.hooandee.colores.effects.ContextServiceGate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class ColoresApplication : Application() {
    val applicationScope: CoroutineScope by lazy { CoroutineScope(SupervisorJob() + Dispatchers.Default) }

    val audioLevelSource by lazy { MutableAudioLevelSource() }

    val audioCaptureSession by lazy { AudioCaptureSession(applicationScope, audioLevelSource) }

    val effectsServiceGate by lazy { ContextServiceGate(this) }

    val lightingController: LightingController by lazy {
        LightingController(
            scope = applicationScope,
            serviceGate = effectsServiceGate,
        )
    }

    val lightingRuntime: LightingRuntime by lazy {
        LightingRuntime(this, applicationScope, lightingController, audioLevelSource)
    }
}
