package com.hooandee.colores.device.learning

import com.hooandee.colores.device.AndroidDeviceIdentity
import com.hooandee.colores.device.DetectedAndroidDevice
import com.hooandee.colores.device.LedGridCell
import com.hooandee.colores.led.LedDescriptor
import com.hooandee.colores.led.SettingsProviderDescriptor

internal fun resolveLearnedDevice(
    identity: AndroidDeviceIdentity,
    binding: LearnedDeviceBinding?,
    candidates: List<ProbeCandidate>,
): DetectedAndroidDevice? {
    binding ?: return null
    if (binding.identityHash != learningIdentityHash(identity)) return null
    val boundDescriptor = decodeLearningDescriptor(binding.descriptorJson) ?: return null
    val candidate =
        candidates.firstOrNull {
            it.cartridgeId == binding.cartridgeId &&
                it.cartridgeVersion == binding.cartridgeVersion &&
                learningDescriptorsCompatible(it.descriptor, boundDescriptor)
        } ?: return null
    if (!binding.capabilities.color || binding.capabilities.zones < 1) return null
    return DetectedAndroidDevice(
        id = learnedDeviceId(binding),
        friendlyName = identity.model.ifBlank { identity.manufacturer }.ifBlank { identity.device }.ifBlank { "RGB" },
        capabilities = binding.capabilities,
        led = boundDescriptor,
        previewProfileId = null,
        previewCalibration = null,
        gridLayout = candidate.learnedGridLayout(binding.capabilities.perZone, binding.capabilities.zones),
    )
}

private fun ProbeCandidate.learnedGridLayout(
    perZone: Boolean,
    zones: Int,
): List<LedGridCell>? =
    if (surface == ProbeSurface.HTR3212 && perZone && zones == 8) {
        listOf(
            LedGridCell(0, 0, 0, "top_left"),
            LedGridCell(0, 1, 0, "bottom_left"),
            LedGridCell(0, 1, 1, "bottom_right"),
            LedGridCell(0, 0, 1, "top_right"),
            LedGridCell(1, 0, 0, "top_left"),
            LedGridCell(1, 1, 0, "bottom_left"),
            LedGridCell(1, 1, 1, "bottom_right"),
            LedGridCell(1, 0, 1, "top_right"),
        )
    } else {
        null
    }

internal fun learnedDeviceIdForPromotion(
    identity: AndroidDeviceIdentity,
    exact: DetectedAndroidDevice,
    binding: LearnedDeviceBinding?,
): String? {
    binding ?: return null
    if (exact.id.startsWith("learned-") || binding.identityHash != learningIdentityHash(identity)) return null
    val boundDescriptor = decodeLearningDescriptor(binding.descriptorJson) ?: return null
    if (binding.capabilities != exact.capabilities || !learningDescriptorsCompatible(exact.led, boundDescriptor)) return null
    return learnedDeviceId(binding)
}

internal fun learningDescriptorsCompatible(
    observedDescriptor: LedDescriptor,
    boundDescriptor: LedDescriptor,
): Boolean {
    val observedShape = observedDescriptor.learningShape()
    val learnedShape = boundDescriptor.learningShape()
    if (observedShape == learnedShape) return true
    val observed = observedShape as? SettingsProviderDescriptor ?: return false
    val learned = learnedShape as? SettingsProviderDescriptor ?: return false
    val observedHtr = observed.htr3212 ?: return false
    val learnedHtr = learned.htr3212 ?: return false
    if (!learnedHtr.leftOrder.isHtrOrder() || !learnedHtr.rightOrder.isHtrOrder()) return false
    return learned.copy(
        htr3212 =
            learnedHtr.copy(
                leftOrder = observedHtr.leftOrder,
                rightOrder = observedHtr.rightOrder,
            ),
    ) == observed
}

private fun learnedDeviceId(binding: LearnedDeviceBinding): String =
    "learned-${binding.cartridgeId}-${binding.identityHash.take(12)}"

private fun LedDescriptor.learningShape(): LedDescriptor =
    if (this is SettingsProviderDescriptor) copy(requiresPermission = null, vendorService = "") else this

private fun List<Int>.isHtrOrder(): Boolean = size == 4 && sorted() == listOf(0, 1, 2, 3)
