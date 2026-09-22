package com.hooandee.colores.device.learning

import com.hooandee.colores.led.FileSysfsAccess
import com.hooandee.colores.led.SingleAdcJoypadDescriptor
import com.hooandee.colores.led.SysfsAccess

class SingleAdcLearningCartridge(
    private val access: SysfsAccess = FileSysfsAccess,
) : ProbeCartridge {
    override val id = SINGLEADC_PROBE_ID
    override val version = PROBE_VERSION
    override val surface = ProbeSurface.SINGLEADC_JOYPAD

    override fun accepts(candidate: ProbeCandidate): Boolean {
        val descriptor = candidate.descriptor as? SingleAdcJoypadDescriptor ?: return false
        return candidate.cartridgeId == id &&
            candidate.cartridgeVersion == version &&
            candidate.surface == surface &&
            descriptor.basePath == SingleAdcJoypadDescriptor.DEFAULT_BASE_PATH
    }

    override fun snapshot(candidate: ProbeCandidate): ProbeSnapshot? {
        val descriptor = candidate.descriptor as? SingleAdcJoypadDescriptor ?: return null
        if (!accepts(candidate)) return null
        val statePaths = statePaths(descriptor)
        val latch = node(descriptor, "led_set")
        if (!access.canWrite(latch) || statePaths.any { !access.canWrite(it) }) return null
        val values = statePaths.associateWith { access.read(it) ?: return null }
        val effectValues = effectValues(descriptor)
        return ProbeSnapshot(values + effectValues.orEmpty())
    }

    override fun supportedSteps(candidate: ProbeCandidate): List<ProbeStep> {
        val descriptor = candidate.descriptor as? SingleAdcJoypadDescriptor ?: return emptyList()
        if (snapshot(candidate) == null) return emptyList()
        return buildList {
            addAll(listOf(ProbeStep.COLOR, ProbeStep.BRIGHTNESS_LOW, ProbeStep.BRIGHTNESS_HIGH, ProbeStep.POWER_OFF, ProbeStep.POWER_ON))
            if (effectValues(descriptor) != null) add(ProbeStep.HARDWARE_EFFECT)
        }
    }

    override fun bindingCandidate(
        candidate: ProbeCandidate,
        evidence: List<ProbeEvidence>,
    ): ProbeCandidate {
        val descriptor = candidate.descriptor as? SingleAdcJoypadDescriptor ?: return candidate
        val confirmed = evidence.any { it.step == ProbeStep.HARDWARE_EFFECT && it.level == EvidenceLevel.USER_CONFIRMED }
        return if (confirmed) candidate.copy(descriptor = descriptor.copy(vendorEffects = true)) else candidate
    }

    override fun execute(
        candidate: ProbeCandidate,
        step: ProbeStep,
        zone: Int?,
    ): Boolean {
        val descriptor = candidate.descriptor as? SingleAdcJoypadDescriptor ?: return false
        if (snapshot(candidate) == null) return false
        val writes =
            when (step) {
                ProbeStep.COLOR ->
                    linkedMapOf(
                        node(descriptor, "custum_rgb_r") to "255",
                        node(descriptor, "custum_rgb_g") to "0",
                        node(descriptor, "custum_rgb_b") to "255",
                        node(descriptor, "led_level") to HIGH_LEVEL,
                        node(descriptor, "led_mode") to "1",
                        node(descriptor, "led_switch") to "1",
                    )
                ProbeStep.BRIGHTNESS_LOW -> linkedMapOf(node(descriptor, "led_level") to LOW_LEVEL)
                ProbeStep.BRIGHTNESS_HIGH -> linkedMapOf(node(descriptor, "led_level") to HIGH_LEVEL)
                ProbeStep.POWER_OFF -> linkedMapOf(node(descriptor, "led_switch") to "0")
                ProbeStep.POWER_ON -> linkedMapOf(node(descriptor, "led_switch") to "1")
                ProbeStep.HARDWARE_EFFECT -> {
                    if (effectValues(descriptor) == null) return false
                    linkedMapOf(
                        node(descriptor, "Led_rgb_r2") to "255",
                        node(descriptor, "Led_rgb_g2") to "0",
                        node(descriptor, "Led_rgb_b2") to "255",
                        node(descriptor, "Led_rgb_r1") to "0",
                        node(descriptor, "Led_rgb_g1") to "0",
                        node(descriptor, "Led_rgb_b1") to "255",
                        node(descriptor, "led_level") to HIGH_LEVEL,
                        node(descriptor, "led_speed") to BREATHING_SPEED,
                        node(descriptor, "led_mode") to BREATHING_MODE,
                        node(descriptor, "led_switch") to "1",
                    )
                }
                ProbeStep.ZONE -> return false
            }
        return writeAndLatch(descriptor, writes)
    }

    override fun restore(
        candidate: ProbeCandidate,
        snapshot: ProbeSnapshot,
    ): RollbackStatus {
        val descriptor = candidate.descriptor as? SingleAdcJoypadDescriptor ?: return RollbackStatus.RESTORE_FAILED
        if (!accepts(candidate) || !restorableKeys(descriptor, snapshot.values.keys)) return RollbackStatus.RESTORE_FAILED
        val restoredValues = snapshot.values.attemptAll(access::write)
        val latched = access.write(node(descriptor, "led_set"), "1")
        if (!restoredValues || !latched) return RollbackStatus.RESTORE_FAILED
        val readback = snapshot.values.mapValues { (path, _) -> access.read(path)?.trim() }
        val mismatchedPaths = snapshot.values.filter { (path, value) -> readback[path] != value.trim() }.keys
        val brightnessPath = node(descriptor, "led_level")
        return when {
            mismatchedPaths.isEmpty() -> RollbackStatus.RESTORED_AND_READ_BACK
            mismatchedPaths == setOf(brightnessPath) && readback[brightnessPath]?.toIntOrNull()?.let { it > 0 } == true ->
                RollbackStatus.RESTORED_WITHOUT_HARDWARE_READBACK
            else -> RollbackStatus.RESTORE_FAILED
        }
    }

    private fun writeAndLatch(
        descriptor: SingleAdcJoypadDescriptor,
        values: Map<String, String>,
    ): Boolean = values.all { (path, value) -> access.write(path, value) } && access.write(node(descriptor, "led_set"), "1")

    private fun statePaths(descriptor: SingleAdcJoypadDescriptor): List<String> =
        STATE_NODES.map { node(descriptor, it) }

    private fun effectPaths(descriptor: SingleAdcJoypadDescriptor): List<String> =
        EFFECT_NODES.map { node(descriptor, it) }

    private fun effectValues(descriptor: SingleAdcJoypadDescriptor): Map<String, String>? {
        val paths = effectPaths(descriptor)
        if (paths.any { !access.canWrite(it) }) return null
        return paths.associateWith { access.read(it) ?: return null }
    }

    private fun restorableKeys(
        descriptor: SingleAdcJoypadDescriptor,
        keys: Set<String>,
    ): Boolean {
        val state = statePaths(descriptor).toSet()
        val effect = effectPaths(descriptor).toSet()
        val extra = keys - state
        return keys.containsAll(state) && (extra.isEmpty() || extra == effect)
    }

    private fun node(
        descriptor: SingleAdcJoypadDescriptor,
        name: String,
    ): String = "${descriptor.basePath}/$name"

    private companion object {
        const val LOW_LEVEL = "10"
        const val HIGH_LEVEL = "55"
        const val BREATHING_MODE = "2"
        const val BREATHING_SPEED = "4"
        val STATE_NODES = listOf("custum_rgb_r", "custum_rgb_g", "custum_rgb_b", "led_level", "led_mode", "led_switch")
        val EFFECT_NODES = listOf("Led_rgb_r2", "Led_rgb_g2", "Led_rgb_b2", "Led_rgb_r1", "Led_rgb_g1", "Led_rgb_b1", "led_speed")
    }
}
