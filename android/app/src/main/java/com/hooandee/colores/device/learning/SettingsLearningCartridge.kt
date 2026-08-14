package com.hooandee.colores.device.learning

import com.hooandee.colores.device.GenericVendorLed
import com.hooandee.colores.led.SettingsProviderDescriptor
import com.hooandee.colores.led.SystemSettingsStore

class SettingsLearningCartridge(
    private val store: SystemSettingsStore,
) : ProbeCartridge {
    override val id = SETTINGS_PROBE_ID
    override val version = PROBE_VERSION
    override val surface = ProbeSurface.SETTINGS_PSERVER

    override fun accepts(candidate: ProbeCandidate): Boolean {
        val descriptor = candidate.descriptor as? SettingsProviderDescriptor ?: return false
        return candidate.cartridgeId == id &&
            candidate.cartridgeVersion == version &&
            candidate.surface == surface &&
            descriptor.driver == "settings_provider" &&
            descriptor.transport == "pserver" &&
            descriptor.colorKey == GenericVendorLed.COLOR_KEY &&
            descriptor.colorFormat == "argb_hex_csv" &&
            descriptor.brightnessKey == GenericVendorLed.BRIGHTNESS_KEY &&
            descriptor.brightnessRange == 0f..1f &&
            descriptor.enableKeys == GenericVendorLed.ENABLE_KEYS &&
            descriptor.zones in 1..MAX_ZONES
    }

    override fun snapshot(candidate: ProbeCandidate): ProbeSnapshot? {
        if (!store.available || !accepts(candidate)) return null
        val descriptor = candidate.descriptor as SettingsProviderDescriptor
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
            if (store.get(descriptor.brightnessKey) != null) {
                add(ProbeStep.BRIGHTNESS_LOW)
                add(ProbeStep.BRIGHTNESS_HIGH)
            }
            if (descriptor.enableKeys.any { store.get(it) != null }) {
                add(ProbeStep.POWER_OFF)
                add(ProbeStep.POWER_ON)
            }
            if (descriptor.zones > 1) add(ProbeStep.ZONE)
        }
    }

    override fun execute(
        candidate: ProbeCandidate,
        step: ProbeStep,
        zone: Int?,
    ): Boolean {
        val descriptor = candidate.descriptor as? SettingsProviderDescriptor ?: return false
        if (snapshot(candidate) == null) return false
        return when (step) {
            ProbeStep.COLOR -> store.put(descriptor.colorKey, List(descriptor.zones) { PROBE_COLOR }.joinToString(","))
            ProbeStep.BRIGHTNESS_LOW -> putExisting(descriptor.brightnessKey, "0.25")
            ProbeStep.BRIGHTNESS_HIGH -> putExisting(descriptor.brightnessKey, "0.55")
            ProbeStep.POWER_OFF -> putPower(descriptor, false)
            ProbeStep.POWER_ON -> putPower(descriptor, true)
            ProbeStep.ZONE -> {
                val index = zone?.takeIf { it in 0 until descriptor.zones } ?: return false
                val colors = List(descriptor.zones) { if (it == index) PROBE_COLOR else OFF_COLOR }
                store.put(descriptor.colorKey, colors.joinToString(","))
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
        val accepted = snapshot.values.attemptAll(store::put)
        if (!accepted) return RollbackStatus.RESTORE_FAILED
        val restored = snapshot.values.all { (key, value) -> store.get(key) == value }
        return if (restored) RollbackStatus.RESTORED_AND_READ_BACK else RollbackStatus.RESTORE_FAILED
    }

    private fun putExisting(
        key: String,
        value: String,
    ): Boolean = store.get(key) != null && store.put(key, value)

    private fun putPower(
        descriptor: SettingsProviderDescriptor,
        enabled: Boolean,
    ): Boolean {
        val present = descriptor.enableKeys.filter { store.get(it) != null }
        if (present.isEmpty()) return false
        val value = if (enabled) "1" else "0"
        return present.mapIndexed { index, key ->
            val encoded = if (index == 0) List(descriptor.zones) { value }.joinToString(",") else value
            store.put(key, encoded)
        }.all { it }
    }

    private companion object {
        const val MAX_ZONES = 16
        const val PROBE_COLOR = "#FFFF00FF"
        const val OFF_COLOR = "#FF000000"
    }
}
