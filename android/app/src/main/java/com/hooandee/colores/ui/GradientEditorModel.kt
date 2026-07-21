package com.hooandee.colores.ui

import com.hooandee.colores.device.LedGridCell

internal enum class GradientZonePosition {
    TOP,
    LEFT,
    BOTTOM,
    RIGHT,
    TOP_LEFT,
    TOP_RIGHT,
    BOTTOM_LEFT,
    BOTTOM_RIGHT,
}

internal data class GradientEditorZone(
    val index: Int,
    val stick: Int?,
    val row: Int,
    val col: Int,
    val position: GradientZonePosition?,
)

internal fun gradientEditorZones(
    layout: List<LedGridCell>?,
    count: Int,
): List<GradientEditorZone> {
    if (layout != null && layout.size == count) {
        return layout.mapIndexed { index, cell ->
            GradientEditorZone(
                index = index,
                stick = cell.stick,
                row = cell.row,
                col = cell.col,
                position = cell.position?.let(::parsePosition),
            )
        }
    }
    return List(count) { index ->
        GradientEditorZone(index = index, stick = null, row = index / 4, col = index % 4, position = null)
    }
}

private fun parsePosition(token: String): GradientZonePosition? =
    runCatching { GradientZonePosition.valueOf(token.uppercase()) }.getOrNull()
