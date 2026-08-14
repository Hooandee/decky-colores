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
        return ProbeSnapshot(values)
    }

    override fun supportedSteps(candidate: ProbeCandidate): List<ProbeStep> =
        if (snapshot(candidate) == null) {
            emptyList()
        } else {
            listOf(ProbeStep.COLOR, ProbeStep.BRIGHTNESS_LOW, ProbeStep.BRIGHTNESS_HIGH, ProbeStep.POWER_OFF, ProbeStep.POWER_ON)
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
                        node(descriptor, "led_level") to "25",
                        node(descriptor, "led_mode") to "1",
                        node(descriptor, "led_switch") to "1",
                    )
                ProbeStep.BRIGHTNESS_LOW -> linkedMapOf(node(descriptor, "led_level") to "25")
                ProbeStep.BRIGHTNESS_HIGH -> linkedMapOf(node(descriptor, "led_level") to "55")
                ProbeStep.POWER_OFF -> linkedMapOf(node(descriptor, "led_switch") to "0")
                ProbeStep.POWER_ON -> linkedMapOf(node(descriptor, "led_switch") to "1")
                ProbeStep.ZONE -> return false
            }
        return writeAndLatch(descriptor, writes)
    }

    override fun restore(
        candidate: ProbeCandidate,
        snapshot: ProbeSnapshot,
    ): RollbackStatus {
        val descriptor = candidate.descriptor as? SingleAdcJoypadDescriptor ?: return RollbackStatus.RESTORE_FAILED
        if (!accepts(candidate) || snapshot.values.keys != statePaths(descriptor).toSet()) return RollbackStatus.RESTORE_FAILED
        val restoredValues = snapshot.values.attemptAll(access::write)
        val latched = access.write(node(descriptor, "led_set"), "1")
        if (!restoredValues || !latched) return RollbackStatus.RESTORE_FAILED
        val restored = snapshot.values.all { (path, value) -> access.read(path)?.trim() == value.trim() }
        return if (restored) RollbackStatus.RESTORED_AND_READ_BACK else RollbackStatus.RESTORE_FAILED
    }

    private fun writeAndLatch(
        descriptor: SingleAdcJoypadDescriptor,
        values: Map<String, String>,
    ): Boolean = values.all { (path, value) -> access.write(path, value) } && access.write(node(descriptor, "led_set"), "1")

    private fun statePaths(descriptor: SingleAdcJoypadDescriptor): List<String> =
        STATE_NODES.map { node(descriptor, it) }

    private fun node(
        descriptor: SingleAdcJoypadDescriptor,
        name: String,
    ): String = "${descriptor.basePath}/$name"

    private companion object {
        val STATE_NODES = listOf("custum_rgb_r", "custum_rgb_g", "custum_rgb_b", "led_level", "led_mode", "led_switch")
    }
}
