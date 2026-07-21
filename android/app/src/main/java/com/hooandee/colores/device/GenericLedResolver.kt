package com.hooandee.colores.device

import com.hooandee.colores.led.SysfsRgbDescriptor

internal object GenericLedResolver {
    const val VENDOR_ID = "generic-vendor"
    const val SYSFS_ID = "generic-sysfs"

    fun vendor(
        identity: AndroidDeviceIdentity,
        pserverAvailable: Boolean,
        colorKeyValue: String?,
    ): DetectedAndroidDevice? {
        if (!pserverAvailable || colorKeyValue.isNullOrBlank()) return null
        val zones = GenericVendorLed.DEFAULT_ZONES
        return DetectedAndroidDevice(
            id = VENDOR_ID,
            friendlyName = identity.friendlyName(),
            capabilities = DeviceCapabilities(color = true, brightness = true, perZone = zones > 1, zones = zones),
            led = GenericVendorLed.descriptor(zones),
            previewProfileId = null,
            previewCalibration = null,
        )
    }

    fun sysfs(
        identity: AndroidDeviceIdentity,
        descriptor: SysfsRgbDescriptor?,
    ): DetectedAndroidDevice? {
        if (descriptor == null) return null
        return DetectedAndroidDevice(
            id = SYSFS_ID,
            friendlyName = identity.friendlyName(),
            capabilities =
                DeviceCapabilities(
                    color = true,
                    brightness = true,
                    perZone = descriptor.zones > 1,
                    zones = descriptor.zones,
                ),
            led = descriptor,
            previewProfileId = null,
            previewCalibration = null,
        )
    }

    private fun AndroidDeviceIdentity.friendlyName(): String =
        model.ifBlank { manufacturer }.ifBlank { device }.ifBlank { "RGB" }
}
