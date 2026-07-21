package com.hooandee.colores.device

import com.hooandee.colores.led.LedDescriptor
import com.hooandee.colores.led.SingleAdcJoypadDescriptor
import com.hooandee.colores.led.SysfsRgbDescriptor

internal object GenericLedResolver {
    const val VENDOR_ID = "generic-vendor"
    const val SYSFS_ID = "generic-sysfs"
    const val JOYPAD_ID = "generic-joypad"

    fun vendor(
        identity: AndroidDeviceIdentity,
        pserverAvailable: Boolean,
        colorKeyValue: String?,
    ): DetectedAndroidDevice? {
        if (!pserverAvailable || colorKeyValue.isNullOrBlank()) return null
        val zones = GenericVendorLed.DEFAULT_ZONES
        return build(VENDOR_ID, identity, zones, GenericVendorLed.descriptor(zones))
    }

    fun joypad(
        identity: AndroidDeviceIdentity,
        descriptor: SingleAdcJoypadDescriptor?,
    ): DetectedAndroidDevice? = descriptor?.let { build(JOYPAD_ID, identity, 1, it) }

    fun sysfs(
        identity: AndroidDeviceIdentity,
        descriptor: SysfsRgbDescriptor?,
    ): DetectedAndroidDevice? = descriptor?.let { build(SYSFS_ID, identity, it.zones, it) }

    private fun build(
        id: String,
        identity: AndroidDeviceIdentity,
        zones: Int,
        led: LedDescriptor,
    ): DetectedAndroidDevice =
        DetectedAndroidDevice(
            id = id,
            friendlyName = identity.friendlyName(),
            capabilities = DeviceCapabilities(color = true, brightness = true, perZone = zones > 1, zones = zones),
            led = led,
            previewProfileId = null,
            previewCalibration = null,
        )

    private fun AndroidDeviceIdentity.friendlyName(): String =
        model.ifBlank { manufacturer }.ifBlank { device }.ifBlank { "RGB" }
}
