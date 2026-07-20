package com.hooandee.colores.device

import android.content.Context

data class AndroidDeviceIdentity(
    val model: String,
    val device: String,
    val manufacturer: String,
    val productProperties: Map<String, String>,
)

data class DetectedAndroidDevice(
    val id: String,
    val friendlyName: String,
    val driver: String,
)

class AndroidDeviceDetector(
    private val context: Context,
) {
    fun readIdentity(): AndroidDeviceIdentity =
        TODO("Read Build.MODEL, Build.DEVICE, Build.MANUFACTURER and getprop ro.product.*")

    fun detect(): DetectedAndroidDevice? =
        TODO("Match the Android identity against shared/devices.json")
}
