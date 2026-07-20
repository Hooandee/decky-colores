package com.hooandee.colores.device

import com.hooandee.colores.led.SettingsProviderDescriptor
import com.hooandee.colores.led.Htr3212Descriptor
import com.hooandee.colores.led.SysfsRgbDescriptor
import org.json.JSONArray

data class DeviceCapabilities(
    val color: Boolean,
    val brightness: Boolean,
    val perZone: Boolean,
    val zones: Int,
)

internal data class AndroidDeviceDefinition(
    val models: List<String>,
    val devices: List<String>,
    val detected: DetectedAndroidDevice,
)

class DeviceRegistry internal constructor(
    internal val devices: List<AndroidDeviceDefinition>,
) {
    val hasControllableDevices: Boolean
        get() = devices.any { it.detected.capabilities.color || it.detected.capabilities.brightness }

    fun match(identity: AndroidDeviceIdentity): DetectedAndroidDevice? =
        devices.firstOrNull { definition ->
            val modelMatches = definition.models.isEmpty() || definition.models.any { it.matches(identity.model) }
            val deviceMatches = definition.devices.isEmpty() || definition.devices.any { it.matches(identity.device) }
            (definition.models.isNotEmpty() || definition.devices.isNotEmpty()) && modelMatches && deviceMatches
        }?.detected

    companion object {
        fun parse(
            devicesJson: String,
            previewProfilesJson: String,
        ): DeviceRegistry =
            runCatching {
                val previewProfiles = parsePreviewProfiles(previewProfilesJson)
                val root = JSONArray(devicesJson)
                val definitions =
                    (0 until root.length()).mapNotNull { index ->
                        runCatching {
                            val entry = root.getJSONObject(index)
                            val android = entry.getJSONObject("android")
                            val led = android.getJSONObject("led")
                            val capabilities = entry.getJSONObject("capabilities")
                            val brightnessRange = led.optJSONArray("brightnessRange")
                            val zones = led.getInt("zones")
                            val sysfs =
                                led.optJSONObject("sysfs")?.let { node ->
                                    SysfsRgbDescriptor(
                                        basePath = node.optString("basePath", "/sys/class/leds"),
                                        red = node.getString("red"),
                                        green = node.getString("green"),
                                        blue = node.getString("blue"),
                                        maxBrightness = node.optInt("maxBrightness", 255),
                                    ).also { require(it.maxBrightness > 0) }
                                }
                            val htr3212 =
                                led.optJSONObject("htr3212")?.let { hardware ->
                                    Htr3212Descriptor(
                                        leftBus = hardware.getInt("leftBus"),
                                        rightBus = hardware.getInt("rightBus"),
                                        address = hardware.getInt("address"),
                                        leftOrder = hardware.getIntList("leftOrder"),
                                        rightOrder = hardware.getIntList("rightOrder"),
                                    ).also {
                                        require(it.address in 0..0x7f)
                                        require(it.leftOrder.sorted() == listOf(0, 1, 2, 3))
                                        require(it.rightOrder.sorted() == listOf(0, 1, 2, 3))
                                    }
                                }
                            val previewProfileId = entry.optString("previewProfile").takeIf(String::isNotBlank)
                            AndroidDeviceDefinition(
                                models = android.getStringList("model"),
                                devices = android.getStringList("device"),
                                detected =
                                    DetectedAndroidDevice(
                                        id = entry.getString("id"),
                                        friendlyName = entry.getString("friendlyName"),
                                        capabilities =
                                            DeviceCapabilities(
                                                color = capabilities.optBoolean("color"),
                                                brightness = capabilities.optBoolean("brightness"),
                                                perZone = capabilities.optBoolean("perZone"),
                                                zones = zones,
                                            ),
                                        led =
                                            SettingsProviderDescriptor(
                                                driver = led.getString("driver"),
                                                transport = led.optString("transport", "direct"),
                                                colorKey = led.optString("colorKey"),
                                                colorFormat = led.optString("colorFormat"),
                                                brightnessKey = led.optString("brightnessKey"),
                                                brightnessRange =
                                                    brightnessRange?.let {
                                                        it.getDouble(0).toFloat()..it.getDouble(1).toFloat()
                                                    } ?: (0f..1f),
                                                enableKeys = led.optStringList("enableKeys"),
                                                zones = zones,
                                                requiresPermission =
                                                    if (led.isNull("requiresPermission")) {
                                                        null
                                                    } else {
                                                        led.optString("requiresPermission").takeIf(String::isNotBlank)
                                                    },
                                                vendorService = led.optString("vendorService"),
                                                htr3212 = htr3212,
                                                sysfs = sysfs,
                                            ),
                                        previewProfileId = previewProfileId,
                                        previewCalibration = previewProfileId?.let(previewProfiles::get),
                                    ),
                            )
                        }.getOrNull()
                    }
                DeviceRegistry(definitions)
            }.getOrElse { DeviceRegistry(emptyList()) }
    }
}

private fun parsePreviewProfiles(json: String): Map<String, LedPreviewCalibration> =
    runCatching {
        val root = JSONArray(json)
        (0 until root.length()).mapNotNull { index ->
            runCatching {
                val entry = root.getJSONObject(index)
                entry.getString("id") to entry.getJSONObject("calibration").toLedPreviewCalibration()
            }.getOrNull()
        }.toMap()
    }.getOrDefault(emptyMap())

private fun String.matches(value: String): Boolean = trim().equals(value.trim(), ignoreCase = true)

private fun org.json.JSONObject.getStringList(key: String): List<String> {
    val values = getJSONArray(key)
    return (0 until values.length()).map { values.getString(it) }
}

private fun org.json.JSONObject.optStringList(key: String): List<String> =
    optJSONArray(key)?.let { values -> (0 until values.length()).map { values.getString(it) } } ?: emptyList()

private fun org.json.JSONObject.getIntList(key: String): List<Int> {
    val values = getJSONArray(key)
    return (0 until values.length()).map(values::getInt)
}

private fun org.json.JSONObject.toLedPreviewCalibration() =
    LedPreviewCalibration(
        saturationScale = requiredBoundedFloat("saturationScale", 0f, 1.5f),
        whiteMix = requiredBoundedFloat("whiteMix", 0f, 1f),
        redGain = requiredBoundedFloat("redGain", 0f, 2f),
        greenGain = requiredBoundedFloat("greenGain", 0f, 2f),
        blueGain = requiredBoundedFloat("blueGain", 0f, 2f),
        valueGamma = requiredBoundedFloat("valueGamma", 0.1f, 3f),
        glowAlpha = requiredBoundedFloat("glowAlpha", 0f, 1f),
        hueMap =
            getJSONArray("hueMap").let { points ->
                (0 until points.length()).map { index ->
                    val point = points.getJSONObject(index)
                    LedPreviewHuePoint(
                        input = point.requiredBoundedFloat("input", 0f, 360f),
                        output = point.requiredBoundedFloat("output", 0f, 360f),
                    )
                }
            },
    )

private fun org.json.JSONObject.requiredBoundedFloat(
    key: String,
    minimum: Float,
    maximum: Float,
): Float {
    val value = getDouble(key).toFloat()
    require(value.isFinite() && value in minimum..maximum)
    return value
}
