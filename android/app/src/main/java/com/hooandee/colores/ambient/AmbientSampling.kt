package com.hooandee.colores.ambient

import com.hooandee.colores.device.LedGridCell
import com.hooandee.colores.led.RgbColor
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt

enum class AmbientSamplingMode {
    FULL_SCENE,
    BOTTOM_EDGE,
}

data class AmbientPixelFrame(
    val width: Int,
    val height: Int,
    val pixelStride: Int,
    val rowStride: Int,
    val bytes: ByteArray,
)

object AmbientSampler {
    fun sample(
        frame: AmbientPixelFrame,
        zones: Int,
        gridLayout: List<LedGridCell>?,
        supportsPerZone: Boolean,
        mode: AmbientSamplingMode,
    ): List<RgbColor> {
        val zoneCount = zones.coerceAtLeast(1)
        if (!frame.valid()) return List(zoneCount) { BLACK }
        if (!supportsPerZone || gridLayout?.size != zoneCount || gridLayout.any { it.stick == null }) {
            val average = frame.average(Region(0.0, 0.0, 1.0, 1.0))
            return List(zoneCount) { average }
        }
        if (mode == AmbientSamplingMode.BOTTOM_EDGE) {
            return List(zoneCount) { index ->
                frame.average(
                    Region(
                        x0 = index.toDouble() / zoneCount,
                        y0 = BOTTOM_EDGE_START,
                        x1 = (index + 1.0) / zoneCount,
                        y1 = 1.0,
                    ),
                )
            }
        }

        val stickIds = gridLayout.mapNotNull(LedGridCell::stick).distinct().sorted()
        return gridLayout.map { cell ->
            val stickIndex = stickIds.indexOf(cell.stick).coerceAtLeast(0)
            val cells = gridLayout.filter { it.stick == cell.stick }
            val rows = (cells.maxOfOrNull(LedGridCell::row) ?: 0) + 1
            val columns = (cells.maxOfOrNull(LedGridCell::col) ?: 0) + 1
            val stickWidth = 1.0 / stickIds.size.coerceAtLeast(1)
            val stickStart = stickIndex * stickWidth
            frame.average(
                Region(
                    x0 = stickStart + stickWidth * cell.col / columns,
                    y0 = cell.row.toDouble() / rows,
                    x1 = stickStart + stickWidth * (cell.col + 1.0) / columns,
                    y1 = (cell.row + 1.0) / rows,
                ),
            )
        }
    }

    private fun AmbientPixelFrame.valid(): Boolean =
        width > 0 &&
            height > 0 &&
            pixelStride >= 3 &&
            rowStride >= width * pixelStride &&
            bytes.size >= (height - 1) * rowStride + width * pixelStride

    private fun AmbientPixelFrame.average(region: Region): RgbColor {
        val startX = (region.x0 * width).toInt().coerceIn(0, width - 1)
        val endX = (region.x1 * width).toInt().coerceIn(startX + 1, width)
        val startY = (region.y0 * height).toInt().coerceIn(0, height - 1)
        val endY = (region.y1 * height).toInt().coerceIn(startY + 1, height)
        var red = 0L
        var green = 0L
        var blue = 0L
        var count = 0L
        for (y in startY until endY) {
            for (x in startX until endX) {
                val offset = y * rowStride + x * pixelStride
                red += bytes[offset].toInt() and 0xFF
                green += bytes[offset + 1].toInt() and 0xFF
                blue += bytes[offset + 2].toInt() and 0xFF
                count += 1
            }
        }
        return if (count == 0L) BLACK else RgbColor((red / count).toInt(), (green / count).toInt(), (blue / count).toInt())
    }

    private data class Region(
        val x0: Double,
        val y0: Double,
        val x1: Double,
        val y1: Double,
    )

    private val BLACK = RgbColor(0, 0, 0)
    private const val BOTTOM_EDGE_START = 0.72
}

object AmbientColorMath {
    fun vivid(
        color: RgbColor,
        percent: Int,
    ): RgbColor {
        val factor = 1.0 + percent.coerceIn(0, 100) * 0.015
        val gray = color.red * 0.299 + color.green * 0.587 + color.blue * 0.114
        return RgbColor(
            boosted(gray, color.red, factor),
            boosted(gray, color.green, factor),
            boosted(gray, color.blue, factor),
        )
    }

    fun smooth(
        current: RgbColor,
        target: RgbColor,
        smoothing: Int,
        elapsedMs: Long,
        sceneCut: Boolean = false,
    ): RgbColor {
        val baseAlpha = maxOf(0.04, 1.0 - smoothing.coerceIn(0, 100) / 100.0)
        val retainedPer100Ms = (1.0 - baseAlpha) * if (sceneCut) 1.0 - SCENE_CUT_BOOST else 1.0
        val alpha = 1.0 - retainedPer100Ms.pow(elapsedMs.coerceAtLeast(0L) / 100.0)
        return RgbColor(
            approached(current.red, target.red, alpha),
            approached(current.green, target.green, alpha),
            approached(current.blue, target.blue, alpha),
        )
    }

    /**
     * LEDs emit linearly while a screen applies the sRGB curve, so a dark scene would glow pale
     * white. The curve is applied to the brightest channel and the others scale with it, which
     * darkens shadows without shifting the hue of saturated colors.
     */
    fun ledIntensity(color: RgbColor): RgbColor {
        val peak = maxOf(color.red, color.green, color.blue).coerceIn(0, 255)
        if (peak == 0) return RgbColor(0, 0, 0)
        val scale = 255.0 * srgbToLinear(peak / 255.0) / peak
        return RgbColor(
            (color.red * scale).roundToInt().coerceIn(0, 255),
            (color.green * scale).roundToInt().coerceIn(0, 255),
            (color.blue * scale).roundToInt().coerceIn(0, 255),
        )
    }

    private fun srgbToLinear(value: Double): Double =
        if (value <= SRGB_TOE) value / SRGB_TOE_SLOPE else ((value + SRGB_OFFSET) / (1 + SRGB_OFFSET)).pow(SRGB_GAMMA)

    fun distance(
        a: RgbColor,
        b: RgbColor,
    ): Int = maxOf(abs(a.red - b.red), abs(a.green - b.green), abs(a.blue - b.blue))

    private fun boosted(
        gray: Double,
        channel: Int,
        factor: Double,
    ): Int = (gray + (channel - gray) * factor).roundToInt().coerceIn(0, 255)

    private fun approached(
        current: Int,
        target: Int,
        alpha: Double,
    ): Int {
        if (current == target || alpha <= 0.0) return current
        val mixed = (current + (target - current) * alpha).roundToInt().coerceIn(0, 255)
        return if (mixed == current) current + sign(target - current) else mixed
    }

    private fun sign(value: Int): Int = if (value > 0) 1 else -1

    private const val SCENE_CUT_BOOST = 0.85
    private const val SRGB_TOE = 0.04045
    private const val SRGB_TOE_SLOPE = 12.92
    private const val SRGB_OFFSET = 0.055
    private const val SRGB_GAMMA = 2.4
}
