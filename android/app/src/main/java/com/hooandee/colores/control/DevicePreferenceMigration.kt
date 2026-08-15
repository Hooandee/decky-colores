package com.hooandee.colores.control

import android.content.Context
import com.hooandee.colores.gradient.GradientPreferences
import com.hooandee.colores.profiles.LightingProfileStore

internal class DevicePreferenceMigration(
    private val lighting: LightingPreferences,
    private val gradients: GradientPreferences,
    private val profiles: LightingProfileStore,
) {
    constructor(context: Context) : this(
        LightingPreferences(context),
        GradientPreferences(context),
        LightingProfileStore(context),
    )

    fun migrate(
        sourceDeviceId: String,
        targetDeviceId: String,
    ) {
        lighting.migrateDevice(sourceDeviceId, targetDeviceId)
        gradients.migrateDevice(sourceDeviceId, targetDeviceId)
        profiles.migrateDevice(sourceDeviceId, targetDeviceId)
    }
}
