package com.hooandee.colores.device.learning

import com.hooandee.colores.led.LedDescriptor
import com.hooandee.colores.led.SettingsProviderDescriptor
import com.hooandee.colores.led.SingleAdcJoypadDescriptor
import com.hooandee.colores.led.SysfsColorKind
import com.hooandee.colores.led.SysfsRgbDescriptor
import org.json.JSONArray
import org.json.JSONObject

internal fun encodeLearningDescriptor(descriptor: LedDescriptor): String =
    when (descriptor) {
        is SettingsProviderDescriptor -> {
            val value = JSONObject()
                .put("type", "settings")
                .put("driver", descriptor.driver)
                .put("transport", descriptor.transport)
                .put("color_key", descriptor.colorKey)
                .put("color_format", descriptor.colorFormat)
                .put("brightness_key", descriptor.brightnessKey)
                .put("brightness_min", descriptor.brightnessRange.start)
                .put("brightness_max", descriptor.brightnessRange.endInclusive)
                .put("enable_keys", JSONArray(descriptor.enableKeys))
                .put("zones", descriptor.zones)
            descriptor.htr3212?.let { hardware ->
                value.put(
                    "htr3212",
                    JSONObject()
                        .put("left_bus", hardware.leftBus)
                        .put("right_bus", hardware.rightBus)
                        .put("address", hardware.address)
                        .put("left_order", JSONArray(hardware.leftOrder))
                        .put("right_order", JSONArray(hardware.rightOrder))
                        .put("rgb_start_register", hardware.rgbStartRegister)
                        .put("block_write", hardware.blockWrite)
                        .put("paired_write", hardware.pairedWrite)
                        .put("explicit_initialization", hardware.explicitInitialization)
                        .put("automatic_activation", hardware.automaticActivation),
                )
            }
            value.toString()
        }
        is SingleAdcJoypadDescriptor -> {
            val value = JSONObject().put("type", "singleadc").put("base_path", descriptor.basePath)
            if (descriptor.vendorEffects) value.put("vendor_effects", true)
            value.toString()
        }
        is SysfsRgbDescriptor -> descriptor.toJson().toString()
    }

internal fun decodeLearningDescriptor(raw: String): LedDescriptor? =
    runCatching {
        val json = JSONObject(raw)
        when (json.getString("type")) {
            "settings" -> {
                val hardware =
                    json.optJSONObject("htr3212")?.let { value ->
                        com.hooandee.colores.led.Htr3212Descriptor(
                            leftBus = value.getInt("left_bus"),
                            rightBus = value.getInt("right_bus"),
                            address = value.getInt("address"),
                            leftOrder = value.getJSONArray("left_order").ints(),
                            rightOrder = value.getJSONArray("right_order").ints(),
                            rgbStartRegister = value.getInt("rgb_start_register"),
                            blockWrite = value.optBoolean("block_write", false),
                            pairedWrite = value.optBoolean("paired_write", false),
                            explicitInitialization = value.optBoolean("explicit_initialization", false),
                            automaticActivation = value.optBoolean("automatic_activation", false),
                        )
                    }
                SettingsProviderDescriptor(
                    driver = json.getString("driver"),
                    transport = json.getString("transport"),
                    colorKey = json.getString("color_key"),
                    colorFormat = json.getString("color_format"),
                    brightnessKey = json.getString("brightness_key"),
                    brightnessRange = json.getDouble("brightness_min").toFloat()..json.getDouble("brightness_max").toFloat(),
                    enableKeys = json.getJSONArray("enable_keys").strings(),
                    zones = json.getInt("zones"),
                    requiresPermission = null,
                    vendorService = "",
                    htr3212 = hardware,
                )
            }
            "singleadc" -> SingleAdcJoypadDescriptor(json.getString("base_path"), vendorEffects = json.optBoolean("vendor_effects", false))
            "sysfs" -> json.toSysfsDescriptor()
            else -> null
        }
    }.getOrNull()

private fun SysfsRgbDescriptor.toJson(): JSONObject {
    val value =
        JSONObject()
            .put("type", "sysfs")
            .put("node_path", nodePath)
            .put("zones", zones)
            .put("max_brightness", maxBrightness)
            .put("kind", kind.name)
    if (multiIndex.isNotEmpty()) value.put("multi_index", JSONArray(multiIndex))
    if (channelNodes.isNotEmpty()) value.put("channel_nodes", JSONArray(channelNodes))
    if (members.isNotEmpty()) value.put("members", JSONArray(members.map { it.toJson() }))
    return value
}

private fun JSONObject.toSysfsDescriptor(): SysfsRgbDescriptor =
    SysfsRgbDescriptor(
        nodePath = getString("node_path"),
        zones = getInt("zones"),
        maxBrightness = getInt("max_brightness"),
        kind = SysfsColorKind.valueOf(getString("kind")),
        multiIndex = optJSONArray("multi_index")?.strings().orEmpty(),
        channelNodes = optJSONArray("channel_nodes")?.strings().orEmpty(),
        members = optJSONArray("members")?.let { array -> (0 until array.length()).map { array.getJSONObject(it).toSysfsDescriptor() } }.orEmpty(),
    )

private fun JSONArray.strings(): List<String> = (0 until length()).map(::getString)

private fun JSONArray.ints(): List<Int> = (0 until length()).map(::getInt)
