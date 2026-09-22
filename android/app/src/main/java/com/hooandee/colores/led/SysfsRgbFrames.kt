package com.hooandee.colores.led

import kotlin.math.roundToInt

internal object SysfsRgbFrames {
    val STANDARD_CHANNELS = listOf("red", "green", "blue")
    private val ACTIVE_TRIGGER = Regex("\\[([^\\]]+)]")

    private fun nodes(descriptor: SysfsRgbDescriptor): List<String> =
        when (descriptor.kind) {
            SysfsColorKind.COMPOSITE -> descriptor.members.flatMap(::nodes)
            SysfsColorKind.CHANNEL_NODES -> descriptor.channelNodes
            else -> listOf(descriptor.nodePath)
        }

    fun colorPaths(descriptor: SysfsRgbDescriptor): List<String> =
        when (descriptor.kind) {
            SysfsColorKind.COMPOSITE -> descriptor.members.flatMap(::colorPaths)
            SysfsColorKind.CHANNEL_NODES -> descriptor.channelNodes.map { "$it/brightness" }
            SysfsColorKind.RGB_CHANNELS -> STANDARD_CHANNELS.map { "${descriptor.nodePath}/$it" }
            SysfsColorKind.MULTI_INTENSITY_DECIMAL, SysfsColorKind.MULTI_INTENSITY_HEX -> listOf("${descriptor.nodePath}/multi_intensity")
        }

    fun brightnessPaths(descriptor: SysfsRgbDescriptor): List<String> =
        when (descriptor.kind) {
            SysfsColorKind.COMPOSITE -> descriptor.members.flatMap(::brightnessPaths)
            SysfsColorKind.CHANNEL_NODES -> emptyList()
            else -> listOf("${descriptor.nodePath}/brightness")
        }

    fun statePaths(descriptor: SysfsRgbDescriptor): List<String> = (colorPaths(descriptor) + brightnessPaths(descriptor)).distinct()

    fun triggerPaths(descriptor: SysfsRgbDescriptor): List<String> = nodes(descriptor).map { "$it/trigger" }.distinct()

    private fun channelLayout(descriptor: SysfsRgbDescriptor): List<String> =
        descriptor.multiIndex.ifEmpty { List(descriptor.zones) { STANDARD_CHANNELS }.flatten() }

    fun write(
        access: SysfsAccess,
        descriptor: SysfsRgbDescriptor,
        colors: List<RgbColor>,
        brightnessPercent: Int,
        power: Boolean,
    ): Boolean {
        var succeeded = true
        writes(descriptor, colors, brightnessPercent, power).forEach { (path, value) ->
            if (!access.write(path, value)) succeeded = false
        }
        return succeeded
    }

    private fun writes(
        descriptor: SysfsRgbDescriptor,
        colors: List<RgbColor>,
        brightnessPercent: Int,
        power: Boolean,
    ): List<Pair<String, String>> {
        val fitted = colors.fitZones(descriptor.zones).map { if (power) it else RgbColor(0, 0, 0) }
        val percent = if (power) brightnessPercent.coerceIn(0, 100) else 0
        return when (descriptor.kind) {
            SysfsColorKind.COMPOSITE -> {
                var offset = 0
                descriptor.members.flatMap { member ->
                    val slice = fitted.subList(offset.coerceAtMost(fitted.size), (offset + member.zones).coerceAtMost(fitted.size))
                    offset += member.zones
                    writes(member, slice, percent, power)
                }
            }
            SysfsColorKind.CHANNEL_NODES -> {
                val color = fitted.first()
                val scale = descriptor.maxBrightness * percent / 100.0
                descriptor.channelNodes.zip(color.channels()).map { (node, channel) ->
                    "$node/brightness" to ((channel.clamp() / 255.0) * scale).roundToInt().coerceIn(0, descriptor.maxBrightness).toString()
                }
            }
            SysfsColorKind.RGB_CHANNELS ->
                colorPaths(descriptor).zip(fitted.first().channels()).map { (path, channel) ->
                    path to scaleChannel(channel, descriptor.maxBrightness).toString()
                } + brightnessWrite(descriptor, percent)
            SysfsColorKind.MULTI_INTENSITY_DECIMAL ->
                listOf(colorPaths(descriptor).single() to decimalIntensities(descriptor, fitted)) + brightnessWrite(descriptor, percent)
            SysfsColorKind.MULTI_INTENSITY_HEX ->
                listOf(colorPaths(descriptor).single() to fitted.joinToString(" ") { "0x%06X".format(it.packed()) }) +
                    brightnessWrite(descriptor, percent)
        }
    }

    fun activeTrigger(raw: String?): String? {
        val value = raw?.trim()?.takeIf(String::isNotEmpty) ?: return null
        val bracketed = ACTIVE_TRIGGER.find(value)?.groupValues?.get(1)?.trim()
        return bracketed?.takeIf(String::isNotEmpty) ?: value.takeIf { it.none(Char::isWhitespace) }
    }

    private fun decimalIntensities(
        descriptor: SysfsRgbDescriptor,
        colors: List<RgbColor>,
    ): String {
        val seen = mutableMapOf<String, Int>()
        return channelLayout(descriptor).joinToString(" ") { token ->
            val channel = token.lowercase()
            val occurrence = seen.getOrDefault(channel, 0)
            seen[channel] = occurrence + 1
            val color = colors.getOrNull(occurrence)
            val value =
                when (channel) {
                    "red" -> color?.red
                    "green" -> color?.green
                    "blue" -> color?.blue
                    else -> null
                }
            (value?.let { scaleChannel(it, descriptor.maxBrightness) } ?: 0).toString()
        }
    }

    private fun brightnessWrite(
        descriptor: SysfsRgbDescriptor,
        percent: Int,
    ): Pair<String, String> =
        "${descriptor.nodePath}/brightness" to ((percent / 100.0) * descriptor.maxBrightness).roundToInt().coerceIn(0, descriptor.maxBrightness).toString()

    private fun scaleChannel(
        channel: Int,
        maxBrightness: Int,
    ): Int = ((channel.clamp() / 255.0) * maxBrightness).roundToInt().coerceIn(0, maxBrightness)

    private fun RgbColor.channels(): List<Int> = listOf(red, green, blue)

    private fun RgbColor.packed(): Int = (red.clamp() shl 16) or (green.clamp() shl 8) or blue.clamp()

    private fun Int.clamp(): Int = coerceIn(0, 255)
}
