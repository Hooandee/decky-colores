package com.hooandee.colores.engine

object AudioSensitivity {
    const val MIN_DB = -12
    const val NORMAL_DB = 0
    const val MAX_DB = 12

    fun adjust(
        level: Double,
        gainDb: Int,
    ): Double =
        (
            level.coerceIn(0.0, 1.0) +
                gainDb.coerceIn(MIN_DB, MAX_DB).toDouble() / DYNAMIC_RANGE_DB
        ).coerceIn(0.0, 1.0)

    private const val DYNAMIC_RANGE_DB = 40.0
}
