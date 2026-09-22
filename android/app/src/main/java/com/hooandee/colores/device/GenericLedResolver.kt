package com.hooandee.colores.device

import com.hooandee.colores.device.learning.ProbeCandidate
import com.hooandee.colores.device.learning.ProbeSurface
import com.hooandee.colores.device.learning.PROBE_VERSION
import com.hooandee.colores.device.learning.SETTINGS_PROBE_ID
import com.hooandee.colores.device.learning.SINGLEADC_PROBE_ID
import com.hooandee.colores.device.learning.SYSFS_PROBE_ID
import com.hooandee.colores.led.SettingsProviderCodec
import com.hooandee.colores.led.SingleAdcJoypadDescriptor
import com.hooandee.colores.led.SysfsColorKind
import com.hooandee.colores.led.SysfsRgbDescriptor

internal object GenericLedResolver {
    fun settingsCandidate(
        pserverAvailable: Boolean,
        colorKeyValue: String?,
    ): ProbeCandidate? {
        if (!pserverAvailable) return null
        val observed = SettingsProviderCodec.observeColorFormat(colorKeyValue) ?: return null
        return ProbeCandidate(
            cartridgeId = SETTINGS_PROBE_ID,
            cartridgeVersion = PROBE_VERSION,
            surface = ProbeSurface.SETTINGS_PSERVER,
            descriptor = GenericVendorLed.descriptor(observed.count, observed.format),
            signalKeys =
                if (observed.format == SettingsProviderCodec.ARGB_HEX_CSV) {
                    setOf("observed_color_count")
                } else {
                    setOf("observed_color_count", "observed_color_format_${observed.format}")
                },
        )
    }

    fun joypadCandidate(
        descriptor: SingleAdcJoypadDescriptor?,
    ): ProbeCandidate? =
        descriptor?.let {
            ProbeCandidate(
                cartridgeId = SINGLEADC_PROBE_ID,
                cartridgeVersion = PROBE_VERSION,
                surface = ProbeSurface.SINGLEADC_JOYPAD,
                descriptor = it,
                signalKeys = setOf("singleadc_surface"),
            )
        }

    fun sysfsCandidate(
        descriptor: SysfsRgbDescriptor?,
    ): ProbeCandidate? =
        descriptor?.let {
            ProbeCandidate(
                cartridgeId = SYSFS_PROBE_ID,
                cartridgeVersion = PROBE_VERSION,
                surface = ProbeSurface.SYSFS_RGB,
                descriptor = it,
                signalKeys = sysfsSignalKeys(it),
            )
        }

    fun sysfsCandidates(descriptors: List<SysfsRgbDescriptor>): List<ProbeCandidate> = descriptors.mapNotNull(::sysfsCandidate)

    private fun sysfsSignalKeys(descriptor: SysfsRgbDescriptor): Set<String> =
        when (descriptor.kind) {
            SysfsColorKind.CHANNEL_NODES ->
                if (SysfsRgbDiscovery.isUnprefixedChannelGroup(descriptor)) {
                    setOf("channel_nodes", "unprefixed_channel_nodes")
                } else {
                    setOf("channel_nodes")
                }
            SysfsColorKind.COMPOSITE -> setOf("composite_nodes", "observed_node_count")
            else -> setOf("color_kind", "observed_index_count")
        }
}
