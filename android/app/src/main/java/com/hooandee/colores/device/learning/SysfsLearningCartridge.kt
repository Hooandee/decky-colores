package com.hooandee.colores.device.learning

import com.hooandee.colores.led.FileSysfsAccess
import com.hooandee.colores.led.SysfsAccess
import com.hooandee.colores.led.SysfsColorKind
import com.hooandee.colores.led.SysfsRgbDescriptor
import kotlin.math.roundToInt

class SysfsLearningCartridge(
    private val access: SysfsAccess = FileSysfsAccess,
) : ProbeCartridge {
    override val id = SYSFS_PROBE_ID
    override val version = PROBE_VERSION
    override val surface = ProbeSurface.SYSFS_RGB

    override fun accepts(candidate: ProbeCandidate): Boolean {
        val descriptor = candidate.descriptor as? SysfsRgbDescriptor ?: return false
        val name = descriptor.nodePath.substringAfterLast('/')
        return candidate.cartridgeId == id &&
            candidate.cartridgeVersion == version &&
            candidate.surface == surface &&
            descriptor.nodePath.startsWith(SYSFS_ROOT) &&
            !descriptor.nodePath.contains("..") &&
            !EXCLUDED_NAME.containsMatchIn(name) &&
            descriptor.zones in 1..MAX_ZONES &&
            descriptor.maxBrightness in 1..MAX_BRIGHTNESS
    }

    override fun snapshot(candidate: ProbeCandidate): ProbeSnapshot? {
        val descriptor = candidate.descriptor as? SysfsRgbDescriptor ?: return null
        if (!accepts(candidate)) return null
        val paths = colorPaths(descriptor) + brightnessPath(descriptor)
        if (paths.any { !access.exists(it) || !access.canWrite(it) }) return null
        val values = paths.associateWith { access.read(it) ?: return null }
        return ProbeSnapshot(values)
    }

    override fun supportedSteps(candidate: ProbeCandidate): List<ProbeStep> {
        val descriptor = candidate.descriptor as? SysfsRgbDescriptor ?: return emptyList()
        if (snapshot(candidate) == null) return emptyList()
        return buildList {
            add(ProbeStep.COLOR)
            add(ProbeStep.BRIGHTNESS_LOW)
            add(ProbeStep.BRIGHTNESS_HIGH)
            if (descriptor.zones > 1) add(ProbeStep.ZONE)
        }
    }

    override fun execute(
        candidate: ProbeCandidate,
        step: ProbeStep,
        zone: Int?,
    ): Boolean {
        val descriptor = candidate.descriptor as? SysfsRgbDescriptor ?: return false
        if (snapshot(candidate) == null) return false
        return when (step) {
            ProbeStep.COLOR -> writeFrame(descriptor, List(descriptor.zones) { PROBE_RGB }, LOW_PERCENT)
            ProbeStep.BRIGHTNESS_LOW -> access.write(brightnessPath(descriptor), scaledBrightness(descriptor, LOW_PERCENT).toString())
            ProbeStep.BRIGHTNESS_HIGH -> access.write(brightnessPath(descriptor), scaledBrightness(descriptor, HIGH_PERCENT).toString())
            ProbeStep.ZONE -> {
                val index = zone?.takeIf { it in 0 until descriptor.zones } ?: return false
                writeFrame(descriptor, List(descriptor.zones) { if (it == index) PROBE_RGB else OFF_RGB }, LOW_PERCENT)
            }
            ProbeStep.POWER_OFF, ProbeStep.POWER_ON -> false
        }
    }

    override fun restore(
        candidate: ProbeCandidate,
        snapshot: ProbeSnapshot,
    ): RollbackStatus {
        val descriptor = candidate.descriptor as? SysfsRgbDescriptor ?: return RollbackStatus.RESTORE_FAILED
        val expectedPaths = (colorPaths(descriptor) + brightnessPath(descriptor)).toSet()
        if (!accepts(candidate) || snapshot.values.keys != expectedPaths) return RollbackStatus.RESTORE_FAILED
        if (!snapshot.values.attemptAll(access::write)) return RollbackStatus.RESTORE_FAILED
        val restored = snapshot.values.all { (path, value) -> access.read(path)?.trim() == value.trim() }
        return if (restored) RollbackStatus.RESTORED_AND_READ_BACK else RollbackStatus.RESTORE_FAILED
    }

    private fun writeFrame(
        descriptor: SysfsRgbDescriptor,
        colors: List<Rgb>,
        brightnessPercent: Int,
    ): Boolean {
        val colorWritten =
            when (descriptor.kind) {
                SysfsColorKind.RGB_CHANNELS -> {
                    val color = colors.first()
                    colorPaths(descriptor).zip(listOf(color.red, color.green, color.blue)).all { (path, channel) ->
                        val value = ((channel / 255.0) * descriptor.maxBrightness).roundToInt()
                        access.write(path, value.toString())
                    }
                }
                SysfsColorKind.MULTI_INTENSITY_DECIMAL ->
                    access.write(
                        colorPaths(descriptor).single(),
                        colors.flatMap { listOf(it.red, it.green, it.blue) }.joinToString(" "),
                    )
                SysfsColorKind.MULTI_INTENSITY_HEX ->
                    access.write(
                        colorPaths(descriptor).single(),
                        colors.joinToString(" ") { "0x%06X".format((it.red shl 16) or (it.green shl 8) or it.blue) },
                    )
            }
        return colorWritten && access.write(brightnessPath(descriptor), scaledBrightness(descriptor, brightnessPercent).toString())
    }

    private fun colorPaths(descriptor: SysfsRgbDescriptor): List<String> =
        when (descriptor.kind) {
            SysfsColorKind.RGB_CHANNELS -> listOf("red", "green", "blue").map { "${descriptor.nodePath}/$it" }
            else -> listOf("${descriptor.nodePath}/multi_intensity")
        }

    private fun brightnessPath(descriptor: SysfsRgbDescriptor): String = "${descriptor.nodePath}/brightness"

    private fun scaledBrightness(
        descriptor: SysfsRgbDescriptor,
        percent: Int,
    ): Int = ((percent / 100.0) * descriptor.maxBrightness).roundToInt()

    private data class Rgb(val red: Int, val green: Int, val blue: Int)

    private companion object {
        const val SYSFS_ROOT = "/sys/class/leds/"
        const val MAX_ZONES = 32
        const val MAX_BRIGHTNESS = 65535
        const val LOW_PERCENT = 25
        const val HIGH_PERCENT = 55
        val PROBE_RGB = Rgb(255, 0, 255)
        val OFF_RGB = Rgb(0, 0, 0)
        val EXCLUDED_NAME = Regex("notif|status|charg|button|kbd|keyboard|backlight|lcd|flash|torch|indicator|mic|wlan|wifi|bt|lte|caps|numlock|mmc|power|batt", RegexOption.IGNORE_CASE)
    }
}
