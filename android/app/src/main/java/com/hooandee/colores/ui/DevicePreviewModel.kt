package com.hooandee.colores.ui

import com.hooandee.colores.device.LedGridCell
import com.hooandee.colores.led.RgbColor

internal data class DeviceRingSegment(
    val color: RgbColor,
    val startAngle: Float,
    val sweepAngle: Float,
)

internal data class DevicePreviewGroups(
    val groups: List<List<DeviceRingSegment>>,
    val representsSticks: Boolean,
)

internal fun devicePreviewGroups(
    frame: List<RgbColor>,
    layout: List<LedGridCell>?,
): DevicePreviewGroups {
    if (frame.isEmpty()) return DevicePreviewGroups(emptyList(), representsSticks = false)
    val validLayout =
        layout?.takeIf {
            it.size == frame.size &&
                it.all { cell -> cell.stick == 0 || cell.stick == 1 } &&
                it.any { cell -> cell.stick == 0 } &&
                it.any { cell -> cell.stick == 1 }
        }
    if (validLayout != null) {
        val groups =
            listOf(0, 1).map { stick ->
                validLayout.indices.filter { validLayout[it].stick == stick }.toSegments(frame, validLayout)
            }
        return DevicePreviewGroups(groups, representsSticks = true)
    }
    if (frame.size == 1) {
        return DevicePreviewGroups(listOf(listOf(0).toSegments(frame, null)), representsSticks = false)
    }
    val split = (frame.size + 1) / 2
    return DevicePreviewGroups(
        groups =
            listOf(
                (0 until split).toList().toSegments(frame, null),
                (split until frame.size).toList().toSegments(frame, null),
            ),
        representsSticks = false,
    )
}

private fun List<Int>.toSegments(
    frame: List<RgbColor>,
    layout: List<LedGridCell>?,
): List<DeviceRingSegment> {
    if (isEmpty()) return emptyList()
    val sweep = if (size == 1) 360f else (360f / size - SEGMENT_GAP_DEGREES).coerceAtLeast(1f)
    return mapIndexed { ordinal, frameIndex ->
        val center =
            if (size == 1) {
                90f
            } else {
                layout?.getOrNull(frameIndex)?.position?.positionCenterAngle() ?: (-90f + ordinal * 360f / size)
            }
        DeviceRingSegment(
            color = frame[frameIndex],
            startAngle = center - sweep / 2f,
            sweepAngle = sweep,
        )
    }
}

private fun String.positionCenterAngle(): Float? =
    when (this) {
        "top" -> -90f
        "top_right" -> 315f
        "right" -> 0f
        "bottom_right" -> 45f
        "bottom" -> 90f
        "bottom_left" -> 135f
        "left" -> 180f
        "top_left" -> 225f
        else -> null
    }

private const val SEGMENT_GAP_DEGREES = 4f
