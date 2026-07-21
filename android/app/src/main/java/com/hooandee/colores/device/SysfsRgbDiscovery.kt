package com.hooandee.colores.device

import com.hooandee.colores.led.FileSysfsAccess
import com.hooandee.colores.led.SysfsAccess
import com.hooandee.colores.led.SysfsColorKind
import com.hooandee.colores.led.SysfsRgbDescriptor
import java.io.File

data class SysfsLedNode(
    val name: String,
    val path: String,
)

object SysfsRgbDiscovery {
    private const val DEFAULT_ROOT = "/sys/class/leds"
    private val CHANNEL_NAMES = setOf("red", "green", "blue")
    private val EXCLUDED_NAME =
        Regex(
            "notif|status|charg|button|kbd|keyboard|backlight|lcd|flash|torch|indicator|mic|wlan|wifi|bt|lte|caps|numlock|mmc|power|batt",
            RegexOption.IGNORE_CASE,
        )
    private val PREFERRED_NAME =
        Regex("joystick|stick|ring|rgb|gamepad", RegexOption.IGNORE_CASE)

    fun scan(
        root: String = DEFAULT_ROOT,
        access: SysfsAccess = FileSysfsAccess,
    ): SysfsRgbDescriptor? = discover(readNodes(root), access)

    fun discover(
        nodes: List<SysfsLedNode>,
        access: SysfsAccess,
    ): SysfsRgbDescriptor? {
        val candidates =
            nodes
                .filterNot { EXCLUDED_NAME.containsMatchIn(it.name) }
                .sortedBy { if (PREFERRED_NAME.containsMatchIn(it.name)) 0 else 1 }
        return candidates.firstNotNullOfOrNull { describe(it, access) }
    }

    private fun describe(
        node: SysfsLedNode,
        access: SysfsAccess,
    ): SysfsRgbDescriptor? {
        val multiIntensity = "${node.path}/multi_intensity"
        if (access.exists(multiIntensity) && access.canWrite(multiIntensity)) {
            val tokens = access.read("${node.path}/multi_index").orEmpty().split(Regex("\\s+")).filter(String::isNotBlank)
            val decimal = tokens.isNotEmpty() && tokens.all { it.lowercase() in CHANNEL_NAMES }
            val zones = if (decimal) maxOf(1, tokens.size / 3) else maxOf(1, tokens.size)
            return SysfsRgbDescriptor(
                nodePath = node.path,
                zones = zones,
                maxBrightness = maxBrightness(node, access),
                kind = if (decimal) SysfsColorKind.MULTI_INTENSITY_DECIMAL else SysfsColorKind.MULTI_INTENSITY_HEX,
            )
        }
        val channels = CHANNEL_NAMES.map { "${node.path}/$it" }
        if (channels.all(access::exists) && channels.all(access::canWrite)) {
            return SysfsRgbDescriptor(
                nodePath = node.path,
                zones = 1,
                maxBrightness = maxBrightness(node, access),
                kind = SysfsColorKind.RGB_CHANNELS,
            )
        }
        return null
    }

    private fun maxBrightness(
        node: SysfsLedNode,
        access: SysfsAccess,
    ): Int = access.read("${node.path}/max_brightness")?.toIntOrNull()?.takeIf { it > 0 } ?: 255

    private fun readNodes(root: String): List<SysfsLedNode> =
        runCatching {
            File(root).listFiles()?.filter(File::isDirectory)?.map { SysfsLedNode(it.name, it.absolutePath) }.orEmpty()
        }.getOrDefault(emptyList())
}
