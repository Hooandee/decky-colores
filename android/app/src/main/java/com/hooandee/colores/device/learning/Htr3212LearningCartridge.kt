package com.hooandee.colores.device.learning

import com.hooandee.colores.device.GenericVendorLed
import com.hooandee.colores.led.Htr3212Command
import com.hooandee.colores.led.PServerCommandExecutor
import com.hooandee.colores.led.RgbColor
import com.hooandee.colores.led.SettingsProviderCodec
import com.hooandee.colores.led.SettingsProviderDescriptor
import com.hooandee.colores.led.SystemSettingsStore
import kotlin.math.roundToInt

fun interface Htr3212RegisterReader {
    fun read(
        bus: Int,
        address: Int,
        registers: List<Int>,
    ): List<Int>?
}

internal class Htr3212LearningCartridge(
    private val store: SystemSettingsStore,
    private val executor: PServerCommandExecutor,
    private val registerReader: Htr3212RegisterReader = Htr3212RegisterReader { _, _, _ -> null },
    private val settleVendor: () -> Unit = { Thread.sleep(VENDOR_SETTLE_MS) },
    private val settleRepaint: () -> Unit = { Thread.sleep(VENDOR_REPAINT_MS) },
) : ProbeCartridge {
    override val id = HTR3212_PROBE_ID
    override val version = HTR3212_PROBE_VERSION
    override val surface = ProbeSurface.HTR3212

    override fun accepts(candidate: ProbeCandidate): Boolean {
        val descriptor = candidate.descriptor as? SettingsProviderDescriptor ?: return false
        return candidate.cartridgeId == id &&
            candidate.cartridgeVersion == version &&
            candidate.surface == surface &&
            descriptor.isAcceptedHtrDescriptor()
    }

    private fun SettingsProviderDescriptor.isAcceptedHtrDescriptor(): Boolean {
        val hardware = htr3212 ?: return false
        return driver == "htr3212" &&
            transport == "pserver" &&
            colorKey == GenericVendorLed.COLOR_KEY &&
            colorFormat == "argb_hex_csv" &&
            brightnessKey == GenericVendorLed.BRIGHTNESS_KEY &&
            enableKeys.isNotEmpty() &&
            GenericVendorLed.ENABLE_KEYS.containsAll(enableKeys) &&
            zones == TOTAL_ZONES &&
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
        val color = store.get(descriptor.colorKey)
        val powerValues = descriptor.enableKeys.mapNotNull { key -> store.get(key)?.let { key to it } }.toMap(linkedMapOf())
        val values =
            when {
                color != null -> linkedMapOf(descriptor.colorKey to color)
                powerValues.isNotEmpty() && powerValues.values.all(::isPowerOffValue) -> linkedMapOf()
                else -> rawSnapshot(descriptor) ?: return null
            }
        if (color != null) store.get(descriptor.brightnessKey)?.let { values[descriptor.brightnessKey] = it }
        values.putAll(powerValues)
        return ProbeSnapshot(values)
    }

    override fun supportedSteps(candidate: ProbeCandidate): List<ProbeStep> {
        val descriptor = candidate.descriptor as? SettingsProviderDescriptor ?: return emptyList()
        if (snapshot(candidate) == null) return emptyList()
        val direct = store.get(descriptor.colorKey) == null
        return buildList {
            add(ProbeStep.COLOR)
            add(ProbeStep.ZONE)
            if (direct || store.get(descriptor.brightnessKey) != null) {
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
            ProbeStep.COLOR -> prepareDevice(descriptor) && writeFrame(descriptor, List(TOTAL_ZONES) { PROBE_COLOR }, HIGH_LEVEL)
            ProbeStep.BRIGHTNESS_LOW -> prepareDevice(descriptor) && writeFrame(descriptor, List(TOTAL_ZONES) { PROBE_COLOR }, LOW_LEVEL)
            ProbeStep.BRIGHTNESS_HIGH -> prepareDevice(descriptor) && writeFrame(descriptor, List(TOTAL_ZONES) { PROBE_COLOR }, HIGH_LEVEL)
            ProbeStep.POWER_OFF -> putPower(descriptor, false)
            ProbeStep.POWER_ON -> prepareDevice(descriptor) && writeFrame(descriptor, List(TOTAL_ZONES) { PROBE_COLOR }, HIGH_LEVEL)
            ProbeStep.ZONE -> {
                val index = zone?.takeIf { it in 0 until TOTAL_ZONES } ?: return false
                if (!prepareDevice(descriptor)) return false
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
        if (descriptor.colorKey !in snapshot.values) return restoreRaw(descriptor, snapshot)
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
            if (original.power) {
                writeFrame(
                    descriptor,
                    List(ZONES_PER_STICK) { original.zoneColors[0] } +
                        List(ZONES_PER_STICK) { original.zoneColors.getOrElse(1) { original.zoneColors[0] } },
                    original.brightness,
                )
            } else {
                writeDarkFrame(descriptor)
            }
        val settingsRestored = snapshot.values.all { (key, value) -> store.get(key) == value }
        return if (directRestored && settingsRestored) {
            RollbackStatus.RESTORED_WITHOUT_HARDWARE_READBACK
        } else {
            RollbackStatus.RESTORE_FAILED
        }
    }

    override fun restoreSettingsOnly(
        candidate: ProbeCandidate,
        snapshot: ProbeSnapshot,
    ): RollbackStatus {
        val descriptor = candidate.descriptor as? SettingsProviderDescriptor ?: return RollbackStatus.RESTORE_FAILED
        if (!accepts(candidate)) return RollbackStatus.RESTORE_FAILED
        val allowedKeys = setOf(descriptor.colorKey, descriptor.brightnessKey) + descriptor.enableKeys
        val settings = snapshot.values.filterKeys { it in allowedKeys }
        if (settings.isEmpty()) return RollbackStatus.RESTORE_FAILED
        return restoreSettings(settings)
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

    private fun prepareDevice(descriptor: SettingsProviderDescriptor): Boolean =
        if (store.get(descriptor.colorKey) == null) {
            val present = descriptor.enableKeys.any { store.get(it) != null }
            !present || putPower(descriptor, true)
        } else {
            prepareVendor(descriptor)
        }

    private fun rawSnapshot(descriptor: SettingsProviderDescriptor): LinkedHashMap<String, String>? {
        val hardware = descriptor.htr3212 ?: return null
        val registers = (hardware.rgbStartRegister until hardware.rgbStartRegister + CHANNEL_COUNT).toList()
        val left = registerReader.read(hardware.leftBus, hardware.address, registers)?.validRegisterValues() ?: return null
        val right = registerReader.read(hardware.rightBus, hardware.address, registers)?.validRegisterValues() ?: return null
        val controls =
            if (hardware.explicitInitialization) {
                val leftControls = readControls(hardware.leftBus, hardware.address) ?: return null
                val rightControls = readControls(hardware.rightBus, hardware.address) ?: return null
                leftControls to rightControls
            } else {
                null
            }
        return linkedMapOf<String, String>().apply {
            left.forEachIndexed { index, value -> put("$RAW_LEFT_PREFIX$index", value.toString()) }
            right.forEachIndexed { index, value -> put("$RAW_RIGHT_PREFIX$index", value.toString()) }
            controls?.first?.forEach { (register, value) -> put(controlKey(RAW_LEFT_CONTROL_PREFIX, register), value.toString()) }
            controls?.second?.forEach { (register, value) -> put(controlKey(RAW_RIGHT_CONTROL_PREFIX, register), value.toString()) }
        }
    }

    private fun readControls(
        bus: Int,
        address: Int,
    ): Map<Int, Int>? {
        val registers = Htr3212Command.CONTROL_REGISTERS
        val values = registerReader.read(bus, address, registers) ?: return null
        if (values.size != registers.size || values.any { it !in 0..255 }) return null
        return registers.zip(values).toMap(linkedMapOf())
    }

    private fun ProbeSnapshot.controlValues(prefix: String): Map<Int, Int>? =
        Htr3212Command.CONTROL_REGISTERS.associateWith { register ->
            values[controlKey(prefix, register)]?.toIntOrNull()?.takeIf { it in 0..255 } ?: return null
        }

    private fun controlKey(
        prefix: String,
        register: Int,
    ): String = prefix + "%02x".format(register)

    private fun writeDarkFrame(descriptor: SettingsProviderDescriptor): Boolean =
        writeFrame(descriptor, List(TOTAL_ZONES) { OFF_COLOR }, FULL_LEVEL, initialize = false)

    private fun originallyPowered(
        descriptor: SettingsProviderDescriptor,
        snapshot: ProbeSnapshot,
    ): Boolean {
        val power = descriptor.enableKeys.mapNotNull(snapshot.values::get)
        if (descriptor.colorKey in snapshot.values) return power.isEmpty() || power.joinToString(",").split(',').any { it.trim() == "1" }
        return power.isEmpty() || !power.all(::isPowerOffValue)
    }

    override fun verifiedRollback(
        candidate: ProbeCandidate,
        snapshot: ProbeSnapshot,
        evidence: List<ProbeEvidence>,
        status: RollbackStatus,
    ): RollbackStatus {
        val descriptor = candidate.descriptor as? SettingsProviderDescriptor ?: return status
        if (status == RollbackStatus.RESTORE_FAILED || originallyPowered(descriptor, snapshot)) return status
        val powerOffUnseen = evidence.any { it.step == ProbeStep.POWER_OFF && it.level == EvidenceLevel.NOT_OBSERVED }
        return if (powerOffUnseen) RollbackStatus.RESTORED_UNVERIFIED else status
    }

    private fun restoreRaw(
        descriptor: SettingsProviderDescriptor,
        snapshot: ProbeSnapshot,
    ): RollbackStatus {
        if (!descriptor.isAcceptedHtrDescriptor()) return RollbackStatus.RESTORE_FAILED
        val settings = snapshot.values.filterKeys { it in descriptor.enableKeys }
        val hasRawValues = snapshot.values.keys.any { it.startsWith(RAW_LEFT_PREFIX) || it.startsWith(RAW_RIGHT_PREFIX) }
        if (!hasRawValues) {
            if (settings.isEmpty() || settings.size != snapshot.values.size || !settings.values.all(::isPowerOffValue)) {
                return RollbackStatus.RESTORE_FAILED
            }
            val settingsStatus = restoreSettings(settings)
            val darkened = writeDarkFrame(descriptor)
            return if (darkened) settingsStatus else RollbackStatus.RESTORE_FAILED
        }
        val left = snapshot.rawValues(RAW_LEFT_PREFIX) ?: return RollbackStatus.RESTORE_FAILED
        val right = snapshot.rawValues(RAW_RIGHT_PREFIX) ?: return RollbackStatus.RESTORE_FAILED
        val hardware = descriptor.htr3212 ?: return RollbackStatus.RESTORE_FAILED
        val controlKeys = Htr3212Command.CONTROL_REGISTERS.flatMap { listOf(controlKey(RAW_LEFT_CONTROL_PREFIX, it), controlKey(RAW_RIGHT_CONTROL_PREFIX, it)) }
        val hasControlValues = snapshot.values.keys.any { it in controlKeys }
        val allowedKeys =
            settings.keys + controlKeys + (0 until CHANNEL_COUNT).flatMap { listOf("$RAW_LEFT_PREFIX$it", "$RAW_RIGHT_PREFIX$it") }
        if (!allowedKeys.containsAll(snapshot.values.keys)) return RollbackStatus.RESTORE_FAILED
        val controls =
            if (hasControlValues) {
                val leftControls = snapshot.controlValues(RAW_LEFT_CONTROL_PREFIX) ?: return RollbackStatus.RESTORE_FAILED
                val rightControls = snapshot.controlValues(RAW_RIGHT_CONTROL_PREFIX) ?: return RollbackStatus.RESTORE_FAILED
                leftControls to rightControls
            } else {
                null
            }
        val pwmRestored = writeRawFrame(descriptor, left, right)
        val controlsRestored =
            controls == null ||
                listOfNotNull(
                    Htr3212Command.controlRestore(hardware.leftBus, hardware.address, controls.first),
                    Htr3212Command.controlRestore(hardware.rightBus, hardware.address, controls.second),
                ).let { commands -> commands.size == STICKS && commands.map(executor::execute).all { it } }
        val directRestored = pwmRestored && controlsRestored
        return if (directRestored && restoreSettings(settings) == RollbackStatus.RESTORED_WITHOUT_HARDWARE_READBACK) {
            RollbackStatus.RESTORED_WITHOUT_HARDWARE_READBACK
        } else {
            RollbackStatus.RESTORE_FAILED
        }
    }

    private fun restoreSettings(settings: Map<String, String>): RollbackStatus {
        val accepted = settings.attemptAll(store::put)
        val restored = settings.all { (key, value) -> store.get(key) == value }
        return if (accepted && restored) RollbackStatus.RESTORED_WITHOUT_HARDWARE_READBACK else RollbackStatus.RESTORE_FAILED
    }

    private fun isPowerOffValue(value: String): Boolean =
        value.split(',').map(String::trim).filter(String::isNotEmpty).let { parts ->
            parts.isNotEmpty() && parts.all { it.equals("0") || it.equals("false", ignoreCase = true) || it.equals("off", ignoreCase = true) }
        }

    private fun writeRawFrame(
        descriptor: SettingsProviderDescriptor,
        left: List<Int>,
        right: List<Int>,
    ): Boolean {
        val hardware = descriptor.htr3212 ?: return false
        val colors = { values: List<Int> -> values.chunked(CHANNELS_PER_ZONE).map { RgbColor(it[0], it[1], it[2]) } }
        val order = (0 until ZONES_PER_STICK).toList()
        val leftCommand =
            Htr3212Command.build(
                hardware.leftBus,
                hardware.address,
                colors(left),
                order,
                previous = null,
                rgbStartRegister = hardware.rgbStartRegister,
                blockWrite = hardware.blockWrite,
                explicitInitialization = hardware.explicitInitialization,
                initialize = false,
            ) ?: return false
        val rightCommand =
            Htr3212Command.build(
                hardware.rightBus,
                hardware.address,
                colors(right),
                order,
                previous = null,
                rgbStartRegister = hardware.rgbStartRegister,
                blockWrite = hardware.blockWrite,
                explicitInitialization = hardware.explicitInitialization,
                initialize = false,
            ) ?: return false
        return listOf(leftCommand, rightCommand).all(executor::execute)
    }

    private fun List<Int>.validRegisterValues(): List<Int>? = takeIf { size == CHANNEL_COUNT && all { value -> value in 0..255 } }

    private fun ProbeSnapshot.rawValues(prefix: String): List<Int>? =
        (0 until CHANNEL_COUNT).map { index -> values["$prefix$index"]?.toIntOrNull() ?: return null }.validRegisterValues()

    private fun putPower(
        descriptor: SettingsProviderDescriptor,
        enabled: Boolean,
    ): Boolean {
        val present = descriptor.enableKeys.withIndex().filter { (_, key) -> store.get(key) != null }
        if (present.isEmpty()) return false
        val value = if (enabled) "1" else "0"
        return present.map { (index, key) ->
            store.put(key, if (index == 0) List(STICKS) { value }.joinToString(",") else value)
        }.all { it }
    }

    private fun writeFrame(
        descriptor: SettingsProviderDescriptor,
        colors: List<RgbColor>,
        brightness: Int,
        initialize: Boolean = true,
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
                initialize = initialize,
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
                initialize = initialize,
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
        const val CHANNELS_PER_ZONE = 3
        const val CHANNEL_COUNT = ZONES_PER_STICK * CHANNELS_PER_ZONE
        const val LOW_LEVEL = 25
        const val HIGH_LEVEL = 55
        const val FULL_LEVEL = 100
        const val HIGH_BRIGHTNESS_SETTING = "0.55"
        const val VENDOR_SETTLE_MS = 300L
        const val VENDOR_REPAINT_MS = 1_200L
        val BUS_RANGE = 0..31
        val PROBE_COLOR = RgbColor(255, 0, 255)
        val OFF_COLOR = RgbColor(0, 0, 0)
        const val PROBE_COLOR_ARGB = "#FFFF00FF"
        const val RAW_LEFT_PREFIX = "htr.left."
        const val RAW_RIGHT_PREFIX = "htr.right."
        const val RAW_LEFT_CONTROL_PREFIX = "htr.control.left."
        const val RAW_RIGHT_CONTROL_PREFIX = "htr.control.right."
    }
}
