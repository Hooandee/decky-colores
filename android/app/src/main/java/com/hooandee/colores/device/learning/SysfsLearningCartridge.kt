package com.hooandee.colores.device.learning

import com.hooandee.colores.led.FileSysfsAccess
import com.hooandee.colores.led.RgbColor
import com.hooandee.colores.led.SysfsAccess
import com.hooandee.colores.led.SysfsColorKind
import com.hooandee.colores.led.SysfsRgbDescriptor
import com.hooandee.colores.led.SysfsRgbFrames

class SysfsLearningCartridge(
    private val access: SysfsAccess = FileSysfsAccess,
) : ProbeCartridge {
    override val id = SYSFS_PROBE_ID
    override val version = PROBE_VERSION
    override val surface = ProbeSurface.SYSFS_RGB

    override fun accepts(candidate: ProbeCandidate): Boolean {
        val descriptor = candidate.descriptor as? SysfsRgbDescriptor ?: return false
        return candidate.cartridgeId == id &&
            candidate.cartridgeVersion == version &&
            candidate.surface == surface &&
            descriptor.isAcceptable(allowComposite = true)
    }

    override fun snapshot(candidate: ProbeCandidate): ProbeSnapshot? {
        val descriptor = candidate.descriptor as? SysfsRgbDescriptor ?: return null
        if (!accepts(candidate)) return null
        val paths = SysfsRgbFrames.statePaths(descriptor)
        if (paths.any { !access.exists(it) || !access.canWrite(it) }) return null
        val values = linkedMapOf<String, String>()
        paths.forEach { path -> values[path] = access.read(path) ?: return null }
        SysfsRgbFrames.triggerPaths(descriptor).forEach { path ->
            SysfsRgbFrames.activeTrigger(access.read(path))?.let { values[path] = it }
        }
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
        val snapshot = snapshot(candidate) ?: return false
        if (!releaseTriggers(descriptor, snapshot)) return false
        val all = List(descriptor.zones) { PROBE_RGB }
        return when (step) {
            ProbeStep.COLOR -> writeFrame(descriptor, all, HIGH_PERCENT)
            ProbeStep.BRIGHTNESS_LOW -> writeFrame(descriptor, all, LOW_PERCENT)
            ProbeStep.BRIGHTNESS_HIGH -> writeFrame(descriptor, all, HIGH_PERCENT)
            ProbeStep.ZONE -> {
                val index = zone?.takeIf { it in 0 until descriptor.zones } ?: return false
                writeFrame(descriptor, List(descriptor.zones) { if (it == index) PROBE_RGB else OFF_RGB }, HIGH_PERCENT)
            }
            ProbeStep.POWER_OFF, ProbeStep.POWER_ON, ProbeStep.HARDWARE_EFFECT -> false
        }
    }

    override fun restore(
        candidate: ProbeCandidate,
        snapshot: ProbeSnapshot,
    ): RollbackStatus {
        val descriptor = candidate.descriptor as? SysfsRgbDescriptor ?: return RollbackStatus.RESTORE_FAILED
        val statePaths = SysfsRgbFrames.statePaths(descriptor).toSet()
        val triggerPaths = SysfsRgbFrames.triggerPaths(descriptor).toSet()
        val keys = snapshot.values.keys
        if (!accepts(candidate) || !keys.containsAll(statePaths) || !(statePaths + triggerPaths).containsAll(keys)) {
            return RollbackStatus.RESTORE_FAILED
        }
        val state = snapshot.values.filterKeys { it in statePaths }
        val triggers = snapshot.values.filterKeys { it in triggerPaths && it !in statePaths }.filterValues { it != NO_TRIGGER }
        val stateWritten = state.attemptAll(access::write)
        val triggersWritten = triggers.attemptAll(access::write)
        if (!stateWritten || !triggersWritten) return RollbackStatus.RESTORE_FAILED
        val triggeredNodes = triggers.keys.map { it.substringBeforeLast('/') }.toSet()
        val stateRestored =
            state.all { (path, value) -> path.substringBeforeLast('/') in triggeredNodes || access.read(path)?.trim() == value.trim() }
        val triggersRestored = triggers.all { (path, value) -> SysfsRgbFrames.activeTrigger(access.read(path)) == value }
        return if (stateRestored && triggersRestored) RollbackStatus.RESTORED_AND_READ_BACK else RollbackStatus.RESTORE_FAILED
    }

    private fun releaseTriggers(
        descriptor: SysfsRgbDescriptor,
        snapshot: ProbeSnapshot,
    ): Boolean =
        SysfsRgbFrames.triggerPaths(descriptor)
            .filter { path -> snapshot.values[path]?.let { it != NO_TRIGGER } == true }
            .filter { path -> SysfsRgbFrames.activeTrigger(access.read(path)) != NO_TRIGGER }
            .all { path -> access.write(path, NO_TRIGGER) }

    private fun writeFrame(
        descriptor: SysfsRgbDescriptor,
        colors: List<RgbColor>,
        brightnessPercent: Int,
    ): Boolean {
        var succeeded = true
        SysfsRgbFrames.writes(descriptor, colors, brightnessPercent, power = true).forEach { (path, value) ->
            if (!access.write(path, value)) succeeded = false
        }
        return succeeded
    }

    private fun SysfsRgbDescriptor.isAcceptable(allowComposite: Boolean): Boolean {
        if (zones !in 1..MAX_ZONES || maxBrightness !in 1..MAX_BRIGHTNESS) return false
        return when (kind) {
            SysfsColorKind.COMPOSITE ->
                allowComposite &&
                    members.size in 2..MAX_MEMBERS &&
                    members.sumOf(SysfsRgbDescriptor::zones) == zones &&
                    members.all { it.isAcceptable(allowComposite = false) }
            SysfsColorKind.CHANNEL_NODES -> zones == 1 && channelNodes.size == 3 && channelNodes.all(::isSafeNode)
            SysfsColorKind.MULTI_INTENSITY_DECIMAL -> isSafeNode(nodePath) && hasCompleteLayout()
            SysfsColorKind.MULTI_INTENSITY_HEX, SysfsColorKind.RGB_CHANNELS -> isSafeNode(nodePath)
        }
    }

    private fun SysfsRgbDescriptor.hasCompleteLayout(): Boolean {
        if (multiIndex.isEmpty()) return true
        return listOf("red", "green", "blue").all { channel -> multiIndex.count { it.equals(channel, ignoreCase = true) } == zones }
    }

    private fun isSafeNode(path: String): Boolean =
        path.startsWith(SYSFS_ROOT) &&
            !path.contains("..") &&
            !EXCLUDED_NAME.containsMatchIn(path.substringAfterLast('/'))

    private companion object {
        const val SYSFS_ROOT = "/sys/class/leds/"
        const val NO_TRIGGER = "none"
        const val MAX_ZONES = 32
        const val MAX_MEMBERS = 8
        const val MAX_BRIGHTNESS = 65535
        const val LOW_PERCENT = 25
        const val HIGH_PERCENT = 55
        val PROBE_RGB = RgbColor(255, 0, 255)
        val OFF_RGB = RgbColor(0, 0, 0)
        val EXCLUDED_NAME = Regex("notif|status|charg|button|kbd|keyboard|backlight|lcd|flash|torch|indicator|mic|wlan|wifi|bt|lte|caps|numlock|mmc|power|batt", RegexOption.IGNORE_CASE)
    }
}
