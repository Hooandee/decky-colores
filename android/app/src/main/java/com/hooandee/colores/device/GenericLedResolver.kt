package com.hooandee.colores.device

import com.hooandee.colores.device.learning.ProbeCandidate
import com.hooandee.colores.device.learning.ProbeSurface
import com.hooandee.colores.device.learning.PROBE_VERSION
import com.hooandee.colores.device.learning.SETTINGS_PROBE_ID
import com.hooandee.colores.device.learning.SINGLEADC_PROBE_ID
import com.hooandee.colores.device.learning.SYSFS_PROBE_ID
import com.hooandee.colores.led.SingleAdcJoypadDescriptor
import com.hooandee.colores.led.SysfsRgbDescriptor

internal object GenericLedResolver {
    fun settingsCandidate(
        pserverAvailable: Boolean,
        colorKeyValue: String?,
    ): ProbeCandidate? {
        if (!pserverAvailable) return null
        val colors = colorKeyValue.parseArgbColors()
        if (colors.isEmpty()) return null
        return ProbeCandidate(
            cartridgeId = SETTINGS_PROBE_ID,
            cartridgeVersion = PROBE_VERSION,
            surface = ProbeSurface.SETTINGS_PSERVER,
            descriptor = GenericVendorLed.descriptor(colors.size),
            signalKeys = setOf("observed_color_count"),
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
                signalKeys = setOf("color_kind", "observed_index_count"),
            )
        }

    private fun String?.parseArgbColors(): List<String> =
        this
            ?.split(',')
            ?.map(String::trim)
            ?.takeIf { it.isNotEmpty() && it.all(ARGb_COLOR::matches) }
            .orEmpty()

    private val ARGb_COLOR = Regex("#[0-9A-Fa-f]{8}")
}
