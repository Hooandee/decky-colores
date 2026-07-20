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
                                                colorKey = led.getString("colorKey"),
                                                colorFormat = led.getString("colorFormat"),
                                                brightnessKey = led.getString("brightnessKey"),
                                                brightnessRange =
                                                    brightnessRange.getDouble(0).toFloat()..
                                                        brightnessRange.getDouble(1).toFloat(),
                                                enableKeys = led.getStringList("enableKeys"),
                                                zones = zones,
                                                requiresPermission = led.getString("requiresPermission"),
                                                vendorService = led.getString("vendorService"),
                                            ),
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
