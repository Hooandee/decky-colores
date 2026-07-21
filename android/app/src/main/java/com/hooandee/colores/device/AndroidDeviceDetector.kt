package com.hooandee.colores.device

import android.content.Context
import android.os.Build
import android.provider.Settings
import com.hooandee.colores.led.AndroidPServerCommandExecutor
import com.hooandee.colores.led.LedDescriptor
import com.hooandee.colores.led.SysfsRgbDescriptor
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
    val led: LedDescriptor,
    val previewProfileId: String?,
    val previewCalibration: LedPreviewCalibration?,
    val gridLayout: List<LedGridCell>? = null,
)

class AndroidDeviceDetector(
    private val context: Context,
    private val pserverAvailable: () -> Boolean = { AndroidPServerCommandExecutor().available },
    private val readSetting: (String) -> String? = { key -> Settings.System.getString(context.contentResolver, key) },
    private val scanSysfs: () -> SysfsRgbDescriptor? = { SysfsRgbDiscovery.scan() },
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

    fun detect(): DetectedAndroidDevice? {
        val identity =
            runCatching { readIdentity() }.getOrElse {
                AndroidDeviceIdentity(model = "", device = "", manufacturer = "", productProperties = emptyMap())
            }
        return modelMatch(identity)
            ?: GenericLedResolver.vendor(
                identity,
                pserverAvailable = runCatching { pserverAvailable() }.getOrDefault(false),
                colorKeyValue = runCatching { readSetting(GenericVendorLed.COLOR_KEY) }.getOrNull(),
            )
            ?: GenericLedResolver.sysfs(identity, runCatching { scanSysfs() }.getOrNull())
    }

    private fun modelMatch(identity: AndroidDeviceIdentity): DetectedAndroidDevice? =
        runCatching {
            DeviceRegistry.parse(
                devicesJson = context.readAsset("devices.json"),
                previewProfilesJson = context.readAsset("led-preview-profiles.json"),
            ).match(identity)
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

private fun Context.readAsset(name: String): String = assets.open(name).bufferedReader().use { it.readText() }
