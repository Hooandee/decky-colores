package com.hooandee.colores

import android.app.Application
import com.hooandee.colores.control.LightingController
import com.hooandee.colores.effects.ContextServiceGate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class ColoresApplication : Application() {
    val applicationScope: CoroutineScope by lazy { CoroutineScope(SupervisorJob() + Dispatchers.Default) }

    val lightingController: LightingController by lazy {
        LightingController(
            scope = applicationScope,
            serviceGate = ContextServiceGate(this),
        )
    }
}
