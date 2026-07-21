package com.hooandee.colores.ui

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

private data class Cell(
    val row: Int,
    val col: Int,
    val position: GradientZonePosition,
)

private val AYN_THOR_LEFT_CELLS =
    listOf(
        Cell(0, 0, GradientZonePosition.TOP_LEFT),
        Cell(1, 0, GradientZonePosition.BOTTOM_LEFT),
        Cell(1, 1, GradientZonePosition.BOTTOM_RIGHT),
        Cell(0, 1, GradientZonePosition.TOP_RIGHT),
    )
private val AYN_THOR_RIGHT_CELLS =
    listOf(
        Cell(1, 0, GradientZonePosition.BOTTOM_LEFT),
        Cell(0, 0, GradientZonePosition.TOP_LEFT),
        Cell(0, 1, GradientZonePosition.TOP_RIGHT),
        Cell(1, 1, GradientZonePosition.BOTTOM_RIGHT),
    )
private val RETROID_POCKET_5_CELLS =
    listOf(
        Cell(0, 0, GradientZonePosition.TOP),
        Cell(0, 1, GradientZonePosition.LEFT),
        Cell(1, 0, GradientZonePosition.BOTTOM),
        Cell(1, 1, GradientZonePosition.RIGHT),
    )

internal fun gradientEditorZones(
    deviceId: String?,
    count: Int,
): List<GradientEditorZone> {
    val cellFor: ((Int) -> Cell)? =
        when {
            deviceId == "ayn-thor" && count == 8 -> { index -> if (index < 4) AYN_THOR_LEFT_CELLS[index % 4] else AYN_THOR_RIGHT_CELLS[index % 4] }
            deviceId == "retroid-pocket-5" && count == 8 -> { index -> RETROID_POCKET_5_CELLS[index % 4] }
            else -> null
        }
    if (cellFor != null) {
        return List(count) { index ->
            val cell = cellFor(index)
            GradientEditorZone(index = index, stick = index / 4, row = cell.row, col = cell.col, position = cell.position)
        }
    }
    return List(count) { index ->
        GradientEditorZone(index = index, stick = null, row = index / 4, col = index % 4, position = null)
    }
}
