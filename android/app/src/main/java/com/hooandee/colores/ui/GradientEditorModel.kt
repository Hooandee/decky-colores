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

// Physical placement of the 4 logical zones within a stick, as a 2x2 grid. Calibrated
// on hardware per model so the editor grid matches what the user sees on the ring. The
// AYN Thor's two sticks are mirrored on the vertical axis: on the left ring the rows
// are flipped relative to the right ring.
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
    if (deviceId == "ayn-thor" && count == 8) {
        return List(count) { index ->
            val cell = if (index < 4) AYN_THOR_LEFT_CELLS[index % 4] else AYN_THOR_RIGHT_CELLS[index % 4]
            GradientEditorZone(index = index, stick = index / 4, row = cell.row, col = cell.col, position = cell.position)
        }
    }
    if (deviceId == "retroid-pocket-5" && count == 8) {
        return List(count) { index ->
            val cell = RETROID_POCKET_5_CELLS[index % 4]
            GradientEditorZone(index = index, stick = index / 4, row = cell.row, col = cell.col, position = cell.position)
        }
    }
    // Fallback: a single wrapped grid, at most four columns per row, no scrolling.
    return List(count) { index ->
        GradientEditorZone(index = index, stick = null, row = index / 4, col = index % 4, position = null)
    }
}
