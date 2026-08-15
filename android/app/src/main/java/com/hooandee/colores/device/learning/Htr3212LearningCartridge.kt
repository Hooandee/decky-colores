package com.hooandee.colores.device.learning

import com.hooandee.colores.device.GenericVendorLed
import com.hooandee.colores.led.Htr3212Command
import com.hooandee.colores.led.PServerCommandExecutor
import com.hooandee.colores.led.RgbColor
import com.hooandee.colores.led.SettingsProviderCodec
import com.hooandee.colores.led.SettingsProviderDescriptor
import com.hooandee.colores.led.SystemSettingsStore
import kotlin.math.roundToInt

internal class Htr3212LearningCartridge(
    private val store: SystemSettingsStore,
    private val executor: PServerCommandExecutor,
    private val settleVendor: () -> Unit = { Thread.sleep(VENDOR_SETTLE_MS) },
    private val settleRepaint: () -> Unit = { Thread.sleep(VENDOR_REPAINT_MS) },
) : ProbeCartridge {
    override val id = HTR3212_PROBE_ID
    override val version = HTR3212_PROBE_VERSION
    override val surface = ProbeSurface.HTR3212

    override fun accepts(candidate: ProbeCandidate): Boolean {
        val descriptor = candidate.descriptor as? SettingsProviderDescriptor ?: return false
        val hardware = descriptor.htr3212 ?: return false
        return candidate.cartridgeId == id &&
            candidate.cartridgeVersion == version &&
            candidate.surface == surface &&
            descriptor.driver == "htr3212" &&
            descriptor.transport == "pserver" &&
            descriptor.colorKey == GenericVendorLed.COLOR_KEY &&
            descriptor.colorFormat == "argb_hex_csv" &&
            descriptor.brightnessKey == GenericVendorLed.BRIGHTNESS_KEY &&
            descriptor.enableKeys.isNotEmpty() &&
            GenericVendorLed.ENABLE_KEYS.containsAll(descriptor.enableKeys) &&
            descriptor.zones == TOTAL_ZONES &&
            hardware.leftBus in BUS_RANGE &&
            hardware.rightBus in BUS_RANGE &&
            hardware.leftBus != hardware.rightBus &&
            hardware.address == ADDRESS &&
            hardware.leftOrder.isZoneOrder() &&
            hardware.rightOrder.isZoneOrder() &&
            hardware.rgbStartRegister in RGB_START_REGISTERS
    }

    override fun snapshot(candidate: ProbeCandidate): ProbeSnapshot? {
        val descriptor = candidate.descriptor as? SettingsProviderDescriptor ?: return null
        if (!store.available || !executor.available || !accepts(candidate)) return null
        val color = store.get(descriptor.colorKey) ?: return null
        val values = linkedMapOf(descriptor.colorKey to color)
        store.get(descriptor.brightnessKey)?.let { values[descriptor.brightnessKey] = it }
        descriptor.enableKeys.forEach { key -> store.get(key)?.let { values[key] = it } }
        return ProbeSnapshot(values)
    }

    override fun supportedSteps(candidate: ProbeCandidate): List<ProbeStep> {
        val descriptor = candidate.descriptor as? SettingsProviderDescriptor ?: return emptyList()
        if (snapshot(candidate) == null) return emptyList()
        return buildList {
            add(ProbeStep.COLOR)
            add(ProbeStep.ZONE)
            if (store.get(descriptor.brightnessKey) != null) {
                add(ProbeStep.BRIGHTNESS_LOW)
                add(ProbeStep.BRIGHTNESS_HIGH)
            }
            if (descriptor.enableKeys.any { store.get(it) != null }) {
                add(ProbeStep.POWER_OFF)
                add(ProbeStep.POWER_ON)
            }
        }
    }

    override fun execute(
        candidate: ProbeCandidate,
        step: ProbeStep,
        zone: Int?,
    ): Boolean {
        val descriptor = candidate.descriptor as? SettingsProviderDescriptor ?: return false
        if (!store.available || !executor.available || !accepts(candidate)) return false
        return when (step) {
            ProbeStep.COLOR -> prepareVendor(descriptor) && writeFrame(descriptor, List(TOTAL_ZONES) { PROBE_COLOR }, HIGH_LEVEL)
            ProbeStep.BRIGHTNESS_LOW -> prepareVendor(descriptor) && writeFrame(descriptor, List(TOTAL_ZONES) { PROBE_COLOR }, LOW_LEVEL)
            ProbeStep.BRIGHTNESS_HIGH -> prepareVendor(descriptor) && writeFrame(descriptor, List(TOTAL_ZONES) { PROBE_COLOR }, HIGH_LEVEL)
            ProbeStep.POWER_OFF -> putPower(descriptor, false)
            ProbeStep.POWER_ON -> prepareVendor(descriptor) && writeFrame(descriptor, List(TOTAL_ZONES) { PROBE_COLOR }, HIGH_LEVEL)
            ProbeStep.ZONE -> {
                val index = zone?.takeIf { it in 0 until TOTAL_ZONES } ?: return false
                if (!prepareVendor(descriptor)) return false
                val colors = List(TOTAL_ZONES) { if (it == index) PROBE_COLOR else OFF_COLOR }
                val firstWrite = writeFrame(descriptor, colors, HIGH_LEVEL)
                settleRepaint()
                val reasserted = writeFrame(descriptor, colors, HIGH_LEVEL)
                firstWrite && reasserted
            }
        }
    }

    override fun restore(
        candidate: ProbeCandidate,
        snapshot: ProbeSnapshot,
    ): RollbackStatus {
        val descriptor = candidate.descriptor as? SettingsProviderDescriptor ?: return RollbackStatus.RESTORE_FAILED
        val allowedKeys = setOf(descriptor.colorKey, descriptor.brightnessKey) + descriptor.enableKeys
        if (!accepts(candidate) || descriptor.colorKey !in snapshot.values || !allowedKeys.containsAll(snapshot.values.keys)) {
            return RollbackStatus.RESTORE_FAILED
        }
        if (!snapshot.values.attemptAll(store::put)) return RollbackStatus.RESTORE_FAILED
        settleVendor()
        val vendorDescriptor = descriptor.copy(driver = "settings_provider", zones = STICKS, htr3212 = null)
        val original =
            SettingsProviderCodec.decode(
                colors = snapshot.values[descriptor.colorKey],
                brightness = snapshot.values[descriptor.brightnessKey],
                power = descriptor.enableKeys.mapNotNull(snapshot.values::get).joinToString(",").ifBlank { null },
                descriptor = vendorDescriptor,
            )
        val directRestored =
            !original.power ||
                writeFrame(
                    descriptor,
                    List(ZONES_PER_STICK) { original.zoneColors[0] } +
                        List(ZONES_PER_STICK) { original.zoneColors.getOrElse(1) { original.zoneColors[0] } },
                    original.brightness,
                )
        val settingsRestored = snapshot.values.all { (key, value) -> store.get(key) == value }
        return if (directRestored && settingsRestored) {
            RollbackStatus.RESTORED_WITHOUT_HARDWARE_READBACK
        } else {
            RollbackStatus.RESTORE_FAILED
        }
    }

    override fun bindingCandidate(
        candidate: ProbeCandidate,
        evidence: List<ProbeEvidence>,
    ): ProbeCandidate {
        val descriptor = candidate.descriptor as? SettingsProviderDescriptor ?: return candidate
        val hardware = descriptor.htr3212 ?: return candidate
        val (leftOrder, rightOrder) = calibratedOrders(evidence) ?: return candidate
        return candidate.copy(
            descriptor = descriptor.copy(htr3212 = hardware.copy(leftOrder = leftOrder, rightOrder = rightOrder)),
        )
    }

    override fun canBind(
        candidate: ProbeCandidate,
        capabilities: com.hooandee.colores.device.DeviceCapabilities,
        evidence: List<ProbeEvidence>,
    ): Boolean = capabilities.color && capabilities.perZone && capabilities.zones == TOTAL_ZONES && calibratedOrders(evidence) != null

    private fun prepareVendor(descriptor: SettingsProviderDescriptor): Boolean {
        val colorPrepared = store.put(descriptor.colorKey, List(STICKS) { PROBE_COLOR_ARGB }.joinToString(","))
        val brightnessPrepared =
            if (store.get(descriptor.brightnessKey) == null) true else store.put(descriptor.brightnessKey, HIGH_BRIGHTNESS_SETTING)
        val powerPrepared = putPower(descriptor, true)
        if (colorPrepared && brightnessPrepared && powerPrepared) settleVendor()
        return colorPrepared && brightnessPrepared && powerPrepared
    }

    private fun putPower(
        descriptor: SettingsProviderDescriptor,
        enabled: Boolean,
    ): Boolean {
        val present = descriptor.enableKeys.filter { store.get(it) != null }
        if (present.isEmpty()) return false
        val value = if (enabled) "1" else "0"
        return present.mapIndexed { index, key ->
            store.put(key, if (index == 0) List(STICKS) { value }.joinToString(",") else value)
        }.all { it }
    }

    private fun writeFrame(
        descriptor: SettingsProviderDescriptor,
        colors: List<RgbColor>,
        brightness: Int,
    ): Boolean {
        val hardware = descriptor.htr3212 ?: return false
        val scaled = colors.map { it.scale(brightness) }
        val left = scaled.take(ZONES_PER_STICK)
        val right = scaled.drop(ZONES_PER_STICK).take(ZONES_PER_STICK)
        val leftCommand =
            Htr3212Command.build(
                hardware.leftBus,
                hardware.address,
                left,
                hardware.leftOrder,
                previous = null,
                rgbStartRegister = hardware.rgbStartRegister,
                blockWrite = hardware.blockWrite,
                explicitInitialization = hardware.explicitInitialization,
            ) ?: return false
        val rightCommand =
            Htr3212Command.build(
                hardware.rightBus,
                hardware.address,
                right,
                hardware.rightOrder,
                previous = null,
                rgbStartRegister = hardware.rgbStartRegister,
                blockWrite = hardware.blockWrite,
                explicitInitialization = hardware.explicitInitialization,
            ) ?: return false
        val commands = if (hardware.pairedWrite) listOf("$leftCommand && $rightCommand") else listOf(leftCommand, rightCommand)
        return commands.all(executor::execute)
    }

    private fun RgbColor.scale(brightness: Int): RgbColor {
        val scale = brightness.coerceIn(0, 100) / 100f
        return RgbColor((red * scale).roundToInt(), (green * scale).roundToInt(), (blue * scale).roundToInt())
    }

    private fun List<Int>.isZoneOrder(): Boolean = size == ZONES_PER_STICK && sorted() == (0 until ZONES_PER_STICK).toList()

    private fun calibratedOrders(evidence: List<ProbeEvidence>): Pair<List<Int>, List<Int>>? {
        val placements =
            evidence
                .filter { it.step == ProbeStep.ZONE && it.level == EvidenceLevel.USER_CONFIRMED }
                .mapNotNull { item ->
                    val zone = item.zone?.takeIf { it in 0 until TOTAL_ZONES } ?: return@mapNotNull null
                    val location = item.location ?: return@mapNotNull null
                    if (location.logicalIndex !in 0 until ZONES_PER_STICK) return null
                    zone to location
                }
        if (placements.size != TOTAL_ZONES || placements.map { it.second }.distinct().size != TOTAL_ZONES) return null
        val left = MutableList(ZONES_PER_STICK) { -1 }
        val right = MutableList(ZONES_PER_STICK) { -1 }
        placements.forEach { (zone, location) ->
            val probedStick = zone / ZONES_PER_STICK
            if (location.stick != probedStick) return null
            val order = if (probedStick == 0) left else right
            order[location.logicalIndex] = zone % ZONES_PER_STICK
        }
        return (left to right).takeIf { left.isZoneOrder() && right.isZoneOrder() }
    }

    private companion object {
        const val ADDRESS = 0x3c
        val RGB_START_REGISTERS = setOf(0x0d)
        const val STICKS = 2
        const val ZONES_PER_STICK = 4
        const val TOTAL_ZONES = STICKS * ZONES_PER_STICK
        const val LOW_LEVEL = 25
        const val HIGH_LEVEL = 55
        const val HIGH_BRIGHTNESS_SETTING = "0.55"
        const val VENDOR_SETTLE_MS = 300L
        const val VENDOR_REPAINT_MS = 1_200L
        val BUS_RANGE = 0..31
        val PROBE_COLOR = RgbColor(255, 0, 255)
        val OFF_COLOR = RgbColor(0, 0, 0)
        const val PROBE_COLOR_ARGB = "#FFFF00FF"
    }
}
