package com.hooandee.colores

import android.app.Application
import com.hooandee.colores.ambient.AmbientCaptureSession
import com.hooandee.colores.ambient.MutableAmbientFrameSource
import com.hooandee.colores.audio.AudioCaptureSession
import com.hooandee.colores.audio.MutableAudioLevelSource
import com.hooandee.colores.apps.ForegroundAppObserver
import com.hooandee.colores.apps.PServerFocusedAppResolver
import com.hooandee.colores.apps.UsageAccess
import com.hooandee.colores.control.LightingController
import com.hooandee.colores.control.LightingRuntime
import com.hooandee.colores.control.attachProfileRuntime
import com.hooandee.colores.effects.ContextServiceGate
import com.hooandee.colores.profiles.LightingProfileCoordinator
import com.hooandee.colores.profiles.LightingProfileStore
import com.hooandee.colores.settings.AppPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class ColoresApplication : Application() {
    val appPreferences: AppPreferences by lazy { AppPreferences(this) }

    val applicationScope: CoroutineScope by lazy { CoroutineScope(SupervisorJob() + Dispatchers.Default) }

    val audioLevelSource by lazy { MutableAudioLevelSource() }

    val audioCaptureSession by lazy { AudioCaptureSession(applicationScope, audioLevelSource) }

    val ambientFrameSource by lazy { MutableAmbientFrameSource() }

    val ambientCaptureSession by lazy { AmbientCaptureSession(this, ambientFrameSource) }

    val effectsServiceGate by lazy { ContextServiceGate(this) }

    val profileStore: LightingProfileStore by lazy { LightingProfileStore(this) }

    val usageAccess: UsageAccess by lazy { UsageAccess(this) }

    val lightingController: LightingController by lazy {
        LightingController(
            scope = applicationScope,
            serviceGate = effectsServiceGate,
        )
    }

    val lightingRuntime: LightingRuntime by lazy {
        LightingRuntime(this, applicationScope, lightingController, audioLevelSource, ambientFrameSource)
    }

    val profileCoordinator: LightingProfileCoordinator by lazy {
        LightingProfileCoordinator(
            scope = applicationScope,
            store = profileStore,
            usageAccess = usageAccess,
            observer = ForegroundAppObserver(this, usageAccess, focusedAppResolver = PServerFocusedAppResolver(this)),
            controller = lightingController,
            serviceGate = effectsServiceGate,
        )
    }

    suspend fun restoreRuntime(): Boolean =
        attachProfileRuntime(lightingRuntime.restoreSaved()) { restored ->
            profileCoordinator.bindDevice(restored.deviceId, restored.zones, restored.gradientSupported)
            profileCoordinator.refreshAccess()
        }
}
