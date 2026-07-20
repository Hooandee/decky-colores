package com.hooandee.colores.ui

internal enum class GradientStick {
    LEFT,
    RIGHT,
}

internal enum class GradientZonePosition {
    TOP,
    LEFT,
    BOTTOM,
    RIGHT,
}

internal data class GradientEditorZone(
    val index: Int,
    val stick: GradientStick?,
    val position: GradientZonePosition?,
)

internal fun gradientEditorZones(
    deviceId: String?,
    count: Int,
): List<GradientEditorZone> {
    if (deviceId != "retroid-pocket-5" || count != 8) {
        return List(count) { GradientEditorZone(it, null, null) }
    }
    val positions =
        listOf(
            GradientZonePosition.TOP,
            GradientZonePosition.LEFT,
            GradientZonePosition.BOTTOM,
            GradientZonePosition.RIGHT,
        )
    return List(count) { index ->
        GradientEditorZone(
            index = index,
            stick = if (index < 4) GradientStick.LEFT else GradientStick.RIGHT,
            position = positions[index % 4],
        )
    }
}
