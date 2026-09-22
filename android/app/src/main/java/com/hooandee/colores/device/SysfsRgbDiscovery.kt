package com.hooandee.colores.device

import com.hooandee.colores.led.FileSysfsAccess
import com.hooandee.colores.led.SysfsAccess
import com.hooandee.colores.led.SysfsColorKind
import com.hooandee.colores.led.SysfsRgbDescriptor
import com.hooandee.colores.led.SysfsRgbFrames
import java.io.File

data class SysfsLedNode(
    val name: String,
    val path: String,
)

object SysfsRgbDiscovery {
    private const val DEFAULT_ROOT = "/sys/class/leds"
    private const val MAX_ZONES = 32
    private const val MAX_COMPOSITE_MEMBERS = 8
    private const val PACKED_TOKEN = "rgb"
    private val CHANNEL_NAMES = SysfsRgbFrames.STANDARD_CHANNELS
    private val CHANNEL_TOKEN = Regex("[a-z][a-z0-9_]*")
    private val CHANNEL_NODE_NAME = Regex("^(.*?)([:_.-]?)(red|green|blue)$", RegexOption.IGNORE_CASE)
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

    fun scanAll(
        root: String = DEFAULT_ROOT,
        access: SysfsAccess = FileSysfsAccess,
    ): List<SysfsRgbDescriptor> = discoverAll(readNodes(root), access)

    fun discover(
        nodes: List<SysfsLedNode>,
        access: SysfsAccess,
    ): SysfsRgbDescriptor? = discoverAll(nodes, access).firstOrNull { it.kind != SysfsColorKind.COMPOSITE }

    fun discoverAll(
        nodes: List<SysfsLedNode>,
        access: SysfsAccess,
    ): List<SysfsRgbDescriptor> {
        val groups = channelGroups(nodes, access)
        val grouped = groups.flatMap(ChannelGroup::members).map(SysfsLedNode::path).toSet()
        val singles =
            nodes
                .filterNot { it.path in grouped || EXCLUDED_NAME.containsMatchIn(it.name) }
                .mapNotNull { node -> describe(node, access)?.let { node.name to it } }
        val prefixedGroups = groups.filter { it.prefix.isNotEmpty() }.map { it.prefix to it.descriptor }
        val individuals =
            (singles + prefixedGroups)
                .sortedBy { (name, _) -> if (PREFERRED_NAME.containsMatchIn(name)) 0 else 1 }
                .map { it.second }
        return buildList {
            addAll(individuals)
            composite(individuals)?.let(::add)
            addAll(groups.filter { it.prefix.isEmpty() }.map(ChannelGroup::descriptor))
        }
    }

    fun isUnprefixedChannelGroup(descriptor: SysfsRgbDescriptor): Boolean =
        descriptor.kind == SysfsColorKind.CHANNEL_NODES &&
            descriptor.channelNodes.map { it.substringAfterLast('/').lowercase() } == CHANNEL_NAMES

    private fun describe(
        node: SysfsLedNode,
        access: SysfsAccess,
    ): SysfsRgbDescriptor? {
        val multiIntensity = "${node.path}/multi_intensity"
        if (access.exists(multiIntensity) && access.canWrite(multiIntensity)) {
            val tokens = access.read("${node.path}/multi_index").orEmpty().lowercase().split(Regex("\\s+")).filter(String::isNotBlank)
            return multiIntensityDescriptor(node, tokens, maxBrightness(node.path, access))
        }
        val channels = CHANNEL_NAMES.map { "${node.path}/$it" }
        if (channels.all(access::exists) && channels.all(access::canWrite)) {
            return SysfsRgbDescriptor(
                nodePath = node.path,
                zones = 1,
                maxBrightness = maxBrightness(node.path, access),
                kind = SysfsColorKind.RGB_CHANNELS,
            )
        }
        return null
    }

    private fun multiIntensityDescriptor(
        node: SysfsLedNode,
        tokens: List<String>,
        maxBrightness: Int,
    ): SysfsRgbDescriptor? {
        if (tokens.isEmpty() || tokens.size > MAX_ZONES * 4) return null
        if (tokens.all { it == PACKED_TOKEN }) {
            if (tokens.size > MAX_ZONES) return null
            return SysfsRgbDescriptor(node.path, tokens.size, maxBrightness, SysfsColorKind.MULTI_INTENSITY_HEX)
        }
        if (tokens.any { it == PACKED_TOKEN || !CHANNEL_TOKEN.matches(it) }) return null
        val zones = tokens.count { it == "red" }
        if (zones == 0 || zones > MAX_ZONES) return null
        if (tokens.count { it == "green" } != zones || tokens.count { it == "blue" } != zones) return null
        val standard = List(zones) { CHANNEL_NAMES }.flatten()
        return SysfsRgbDescriptor(
            nodePath = node.path,
            zones = zones,
            maxBrightness = maxBrightness,
            kind = SysfsColorKind.MULTI_INTENSITY_DECIMAL,
            multiIndex = if (tokens == standard) emptyList() else tokens,
        )
    }

    private fun channelGroups(
        nodes: List<SysfsLedNode>,
        access: SysfsAccess,
    ): List<ChannelGroup> =
        nodes
            .mapNotNull { node -> CHANNEL_NODE_NAME.matchEntire(node.name)?.let { it to node } }
            .groupBy { (match, _) -> match.groupValues[1] to match.groupValues[2] }
            .mapNotNull { (key, entries) ->
                val (prefix, separator) = key
                if (prefix.isEmpty() && separator.isNotEmpty()) return@mapNotNull null
                val byChannel = entries.associate { (match, node) -> match.groupValues[3].lowercase() to node }
                if (entries.size != CHANNEL_NAMES.size || byChannel.keys != CHANNEL_NAMES.toSet()) return@mapNotNull null
                val members = CHANNEL_NAMES.map(byChannel::getValue)
                if (members.any { EXCLUDED_NAME.containsMatchIn(it.name) }) return@mapNotNull null
                val brightness = members.map { "${it.path}/brightness" }
                if (!brightness.all(access::exists) || !brightness.all(access::canWrite)) return@mapNotNull null
                val maxima = members.map { maxBrightness(it.path, access) }.distinct()
                if (maxima.size != 1) return@mapNotNull null
                ChannelGroup(
                    prefix = prefix,
                    members = members,
                    descriptor =
                        SysfsRgbDescriptor(
                            nodePath = members.first().path,
                            zones = 1,
                            maxBrightness = maxima.single(),
                            kind = SysfsColorKind.CHANNEL_NODES,
                            channelNodes = members.map(SysfsLedNode::path),
                        ),
                )
            }

    private fun composite(individuals: List<SysfsRgbDescriptor>): SysfsRgbDescriptor? {
        if (individuals.size < 2 || individuals.size > MAX_COMPOSITE_MEMBERS) return null
        val zones = individuals.sumOf(SysfsRgbDescriptor::zones)
        if (zones > MAX_ZONES) return null
        return SysfsRgbDescriptor(
            nodePath = individuals.first().nodePath,
            zones = zones,
            maxBrightness = individuals.first().maxBrightness,
            kind = SysfsColorKind.COMPOSITE,
            members = individuals,
        )
    }

    private fun maxBrightness(
        path: String,
        access: SysfsAccess,
    ): Int = access.read("$path/max_brightness")?.toIntOrNull()?.takeIf { it > 0 } ?: 255

    private fun readNodes(root: String): List<SysfsLedNode> =
        runCatching {
            File(root).listFiles()?.filter(File::isDirectory)?.map { SysfsLedNode(it.name, it.absolutePath) }.orEmpty()
        }.getOrDefault(emptyList())

    private data class ChannelGroup(
        val prefix: String,
        val members: List<SysfsLedNode>,
        val descriptor: SysfsRgbDescriptor,
    )
}
