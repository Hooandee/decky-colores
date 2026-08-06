package com.hooandee.colores.engine

import com.hooandee.colores.led.RgbColor

data class AudioScale(
    val lowColor: RgbColor = RgbColor(0, 230, 90),
    val mediumColor: RgbColor = RgbColor(255, 200, 0),
    val peakColor: RgbColor = RgbColor(255, 40, 0),
    val mediumAt: Int = 50,
    val peakAt: Int = 100,
) {
    init {
        require(mediumAt in 1..99)
        require(peakAt in (mediumAt + 1)..100)
    }

    fun colorAt(position: Double): RgbColor {
        val percent = position.coerceIn(0.0, 1.0) * 100.0
        return when {
            percent <= mediumAt -> mix(lowColor, mediumColor, percent / mediumAt)
            percent <= peakAt -> mix(mediumColor, peakColor, (percent - mediumAt) / (peakAt - mediumAt))
            else -> peakColor
        }
    }

    private fun mix(
        start: RgbColor,
        end: RgbColor,
        progress: Double,
    ): RgbColor =
        RgbColor(
            FrameMath.clamp8(FrameMath.lerp(start.red, end.red, progress)),
            FrameMath.clamp8(FrameMath.lerp(start.green, end.green, progress)),
            FrameMath.clamp8(FrameMath.lerp(start.blue, end.blue, progress)),
        )

    companion object {
        val DEFAULT = AudioScale()
    }
}
