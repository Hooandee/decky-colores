package com.hooandee.colores.device

import android.content.Context
import android.os.Build
import java.util.concurrent.TimeUnit

data class AndroidDeviceIdentity(
    val model: String,
    val device: String,
    val manufacturer: String,
    val productProperties: Map<String, String>,
)

data class DetectedAndroidDevice(
    val id: String,
    val friendlyName: String,
    val capabilities: DeviceCapabilities,
    val led: com.hooandee.colores.led.SettingsProviderDescriptor,
    val previewCalibration: LedPreviewCalibration?,
)

class AndroidDeviceDetector(
    private val context: Context,
) {
    fun readIdentity(): AndroidDeviceIdentity {
        val properties =
            PRODUCT_PROPERTIES.associateWith(::readProperty).filterValues { it.isNotBlank() }
        return AndroidDeviceIdentity(
            model = Build.MODEL.orEmpty().ifBlank { properties["ro.product.model"].orEmpty() },
            device = Build.DEVICE.orEmpty().ifBlank { properties["ro.product.device"].orEmpty() },
            manufacturer =
                Build.MANUFACTURER.orEmpty().ifBlank { properties["ro.product.manufacturer"].orEmpty() },
            productProperties = properties,
        )
    }

    fun detect(): DetectedAndroidDevice? =
        runCatching {
            val registryJson = context.assets.open("devices.json").bufferedReader().use { it.readText() }
            DeviceRegistry.parse(registryJson).match(readIdentity())
        }.getOrNull()

    private fun readProperty(name: String): String =
        runCatching {
            val process = ProcessBuilder("/system/bin/getprop", name).redirectErrorStream(true).start()
            if (!process.waitFor(300, TimeUnit.MILLISECONDS)) {
                process.destroyForcibly()
                return@runCatching ""
            }
            process.inputStream.bufferedReader().use { it.readText().trim() }
        }.getOrDefault("")

    private companion object {
        val PRODUCT_PROPERTIES =
            listOf(
                "ro.product.model",
                "ro.product.device",
                "ro.product.manufacturer",
                "ro.product.brand",
                "ro.product.name",
                "ro.product.board",
                "ro.board.platform",
            )
    }
}
