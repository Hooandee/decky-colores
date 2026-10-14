package com.hooandee.colores.ui

import com.hooandee.colores.device.DetectedAndroidDevice
import com.hooandee.colores.device.learning.DetectionOutcome
import com.hooandee.colores.led.LedDescriptor
import com.hooandee.colores.led.SettingsProviderDescriptor
import com.hooandee.colores.led.SingleAdcJoypadDescriptor
import com.hooandee.colores.led.SysfsRgbDescriptor

internal fun LedDescriptor.diagnosticDriver(): String =
    when (this) {
        is SettingsProviderDescriptor -> driver
        is SysfsRgbDescriptor -> "sysfs_rgb"
        is SingleAdcJoypadDescriptor -> "singleadc_joypad"
    }

internal fun LedDescriptor.diagnosticRoute(): String =
    when (this) {
        is SettingsProviderDescriptor -> transport
        is SysfsRgbDescriptor -> "sysfs"
        is SingleAdcJoypadDescriptor -> "sysfs"
    }

internal fun LedDescriptor.diagnosticTarget(): String =
    when (this) {
        is SettingsProviderDescriptor -> "color=$colorKey brightness=$brightnessKey enable=${enableKeys.joinToString(",")}"
        is SysfsRgbDescriptor -> (listOf(nodePath) + members.map { it.nodePath } + channelNodes).distinct().joinToString(",")
        is SingleAdcJoypadDescriptor -> basePath
    }

internal fun detectionJournalLine(
    outcome: DetectionOutcome,
    detected: DetectedAndroidDevice?,
    access: ControlAccess,
): String =
    buildString {
        append(outcome.javaClass.simpleName)
        append(" model=").append(outcome.identity.model)
        append(" device=").append(outcome.identity.device)
        append(" manufacturer=").append(outcome.identity.manufacturer)
        append(" complete=").append(outcome.identity.complete)
        if (detected != null) {
            append(" id=").append(detected.id)
            append(" driver=").append(detected.led.diagnosticDriver())
            append(" route=").append(detected.led.diagnosticRoute())
            append(" target=").append(detected.led.diagnosticTarget())
            append(" zones=").append(detected.capabilities.zones)
        }
        append(" access=").append(access.name.lowercase())
        val candidates =
            when (outcome) {
                is DetectionOutcome.Resolved -> outcome.candidates
                is DetectionOutcome.UnavailableKnownDevice -> outcome.candidates
                is DetectionOutcome.Candidates -> outcome.candidates
                is DetectionOutcome.Unsupported -> emptyList()
            }
        if (candidates.isNotEmpty()) append(" candidates=").append(candidates.joinToString(",") { it.cartridgeId })
        if (outcome.facts.isNotEmpty()) {
            append(" facts=").append(outcome.facts.take(40).joinToString(";") { "${it.key}=${it.value.take(80)}" })
        }
    }
