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
                        .put("paired_write", hardware.pairedWrite),
                )
            }
            value.toString()
        }
        is SingleAdcJoypadDescriptor ->
            JSONObject().put("type", "singleadc").put("base_path", descriptor.basePath).toString()
        is SysfsRgbDescriptor ->
            JSONObject()
                .put("type", "sysfs")
                .put("node_path", descriptor.nodePath)
                .put("zones", descriptor.zones)
                .put("max_brightness", descriptor.maxBrightness)
                .put("kind", descriptor.kind.name)
                .toString()
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
            "singleadc" -> SingleAdcJoypadDescriptor(json.getString("base_path"))
            "sysfs" ->
                SysfsRgbDescriptor(
                    nodePath = json.getString("node_path"),
                    zones = json.getInt("zones"),
                    maxBrightness = json.getInt("max_brightness"),
                    kind = SysfsColorKind.valueOf(json.getString("kind")),
                )
            else -> null
        }
    }.getOrNull()

private fun JSONArray.strings(): List<String> = (0 until length()).map(::getString)

private fun JSONArray.ints(): List<Int> = (0 until length()).map(::getInt)
