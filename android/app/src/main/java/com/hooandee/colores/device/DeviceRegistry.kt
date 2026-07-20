package com.hooandee.colores.device

import com.hooandee.colores.led.SettingsProviderDescriptor
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
        fun parse(json: String): DeviceRegistry =
            runCatching {
                val root = JSONArray(json)
                val definitions =
                    (0 until root.length()).mapNotNull { index ->
                        runCatching {
                            val entry = root.getJSONObject(index)
                            val android = entry.getJSONObject("android")
                            val led = android.getJSONObject("led")
                            val capabilities = entry.getJSONObject("capabilities")
                            val brightnessRange = led.getJSONArray("brightnessRange")
                            val zones = led.getInt("zones")
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
                                                colorKey = led.getString("colorKey"),
                                                colorFormat = led.getString("colorFormat"),
                                                brightnessKey = led.getString("brightnessKey"),
                                                brightnessRange =
                                                    brightnessRange.getDouble(0).toFloat()..
                                                        brightnessRange.getDouble(1).toFloat(),
                                                enableKeys = led.getStringList("enableKeys"),
                                                zones = zones,
                                                requiresPermission =
                                                    if (led.isNull("requiresPermission")) {
                                                        null
                                                    } else {
                                                        led.optString("requiresPermission").takeIf(String::isNotBlank)
                                                    },
                                                vendorService = led.getString("vendorService"),
                                            ),
                                        previewCalibration =
                                            entry.optJSONObject("previewCalibration")?.toLedPreviewCalibration(),
                                    ),
                            )
                        }.getOrNull()
                    }
                DeviceRegistry(definitions)
            }.getOrElse { DeviceRegistry(emptyList()) }
    }
}

private fun String.matches(value: String): Boolean = trim().equals(value.trim(), ignoreCase = true)

private fun org.json.JSONObject.getStringList(key: String): List<String> {
    val values = getJSONArray(key)
    return (0 until values.length()).map { values.getString(it) }
}

private fun org.json.JSONObject.toLedPreviewCalibration() =
    LedPreviewCalibration(
        saturationScale = boundedFloat("saturationScale", 1f, 0f, 1.5f),
        whiteMix = boundedFloat("whiteMix", 0f, 0f, 1f),
        redGain = boundedFloat("redGain", 1f, 0f, 2f),
        greenGain = boundedFloat("greenGain", 1f, 0f, 2f),
        blueGain = boundedFloat("blueGain", 1f, 0f, 2f),
        valueGamma = boundedFloat("valueGamma", 1f, 0.1f, 3f),
        glowAlpha = boundedFloat("glowAlpha", 0f, 0f, 1f),
        hueMap =
            optJSONArray("hueMap")?.let { points ->
                (0 until points.length()).mapNotNull { index ->
                    runCatching {
                        val point = points.getJSONObject(index)
                        LedPreviewHuePoint(
                            input = point.boundedFloat("input", 0f, 0f, 360f),
                            output = point.boundedFloat("output", 0f, 0f, 360f),
                        )
                    }.getOrNull()
                }
            }.orEmpty(),
    )

private fun org.json.JSONObject.boundedFloat(
    key: String,
    default: Float,
    minimum: Float,
    maximum: Float,
): Float {
    val value = optDouble(key, default.toDouble()).toFloat()
    return if (value.isFinite()) value.coerceIn(minimum, maximum) else default
}
