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

data class ContentBox(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    val area: Int
        get() = (right - left) * (bottom - top)

    companion object {
        fun full(frame: AmbientPixelFrame) = ContentBox(0, 0, frame.width, frame.height)
    }
}

class ContentBoxTracker {
    var box: ContentBox? = null
        private set
    private var candidate: ContentBox? = null
    private var streak = 0
    private var growth = 0

    fun reset() {
        box = null
        candidate = null
        streak = 0
        growth = 0
    }

    fun update(detected: ContentBox): ContentBox {
        val current = box ?: detected.also { box = it }
        if (detected == current) {
            candidate = null
            streak = 0
            growth = 0
            return current
        }
        if (detected.area > current.area) {
            candidate = null
            streak = 0
            growth++
            if (growth >= RELEASE_FRAMES) {
                box = detected
                growth = 0
            }
            return box ?: detected
        }
        growth = 0
        if (detected != candidate) {
            candidate = detected
            streak = 0
        }
        streak++
        if (streak >= ADOPT_FRAMES) {
            box = detected
            candidate = null
            streak = 0
        }
        return box ?: detected
    }

    companion object {
        const val ADOPT_FRAMES = 3
        const val RELEASE_FRAMES = 20
    }
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
        contentBox: ContentBox? = null,
    ): List<RgbColor> {
        val zoneCount = zones.coerceAtLeast(1)
        if (!frame.valid()) return List(zoneCount) { BLACK }
        val box = contentBox?.takeIf { it.left >= 0 && it.top >= 0 && it.right <= frame.width && it.bottom <= frame.height && it.area > 0 } ?: ContentBox.full(frame)
        fun AmbientPixelFrame.average(region: Region): RgbColor = regionColor(box, region)
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

    fun contentBox(frame: AmbientPixelFrame): ContentBox? {
        if (!frame.valid()) return null
        val width = frame.width
        val height = frame.height
        if ((0 until height).all { frame.rowIsBar(it) }) return ContentBox(0, 0, width, height)
        val (top, bottom) = symmetricBars(height) { frame.rowIsBar(it) }
        val (left, right) = symmetricBars(width) { frame.columnIsBar(it, top, bottom) }
        return ContentBox(left, top, right, bottom)
    }

    private inline fun symmetricBars(
        size: Int,
        isBar: (Int) -> Boolean,
    ): Pair<Int, Int> {
        val limit = size * MAX_CROP_PERCENT / 100
        var start = 0
        while (start < limit && isBar(start)) start++
        var end = 0
        while (end < limit && isBar(size - 1 - end)) end++
        if (start == 0 || end == 0 || abs(start - end) > 1) return 0 to size
        return start to size - end
    }

    fun regionColor(
        frame: AmbientPixelFrame,
        x0: Int,
        y0: Int,
        x1: Int,
        y1: Int,
    ): RgbColor {
        val plain = LongArray(3)
        val weighted = LongArray(3)
        var count = 0L
        var totalWeight = 0L
        for (y in y0 until y1) {
            for (x in x0 until x1) {
                val offset = y * frame.rowStride + x * frame.pixelStride
                val red = frame.bytes[offset].toInt() and 0xFF
                val green = frame.bytes[offset + 1].toInt() and 0xFF
                val blue = frame.bytes[offset + 2].toInt() and 0xFF
                val weight = 255L + CHROMA_WEIGHT * (maxOf(red, green, blue) - minOf(red, green, blue))
                plain[0] += red
                plain[1] += green
                plain[2] += blue
                weighted[0] += red * weight
                weighted[1] += green * weight
                weighted[2] += blue * weight
                count++
                totalWeight += weight
            }
        }
        if (count == 0L) return BLACK
        val meanPeak = plain.maxOf { it / count }
        val color = LongArray(3) { weighted[it] / totalWeight }
        val colorPeak = color.max()
        if (colorPeak == 0L) return BLACK
        fun scaled(value: Long) = minOf(255L, (value * meanPeak + colorPeak / 2) / colorPeak).toInt()
        return RgbColor(scaled(color[0]), scaled(color[1]), scaled(color[2]))
    }

    private fun AmbientPixelFrame.rowIsBar(y: Int): Boolean {
        var dark = 0
        for (x in 0 until width) if (peakAt(x, y) <= BAR_LEVEL) dark++
        return dark * 100 >= width * BAR_SHARE_PERCENT
    }

    private fun AmbientPixelFrame.columnIsBar(
        x: Int,
        top: Int,
        bottom: Int,
    ): Boolean {
        var dark = 0
        for (y in top until bottom) if (peakAt(x, y) <= BAR_LEVEL) dark++
        return dark * 100 >= (bottom - top) * BAR_SHARE_PERCENT
    }

    private fun AmbientPixelFrame.peakAt(
        x: Int,
        y: Int,
    ): Int {
        val offset = y * rowStride + x * pixelStride
        return maxOf(bytes[offset].toInt() and 0xFF, bytes[offset + 1].toInt() and 0xFF, bytes[offset + 2].toInt() and 0xFF)
    }

    private fun AmbientPixelFrame.regionColor(
        box: ContentBox,
        region: Region,
    ): RgbColor {
        val boxWidth = box.right - box.left
        val boxHeight = box.bottom - box.top
        val x0 = box.left + (region.x0 * boxWidth).toInt()
        val y0 = box.top + (region.y0 * boxHeight).toInt()
        val x1 = maxOf(x0 + 1, minOf(box.right, box.left + (region.x1 * boxWidth).toInt()))
        val y1 = maxOf(y0 + 1, minOf(box.bottom, box.top + (region.y1 * boxHeight).toInt()))
        return regionColor(this, minOf(x0, box.right - 1), minOf(y0, box.bottom - 1), x1, y1)
    }

    private data class Region(
        val x0: Double,
        val y0: Double,
        val x1: Double,
        val y1: Double,
    )

    private val BLACK = RgbColor(0, 0, 0)
    private const val BOTTOM_EDGE_START = 0.72
    private const val BAR_LEVEL = 24
    private const val BAR_SHARE_PERCENT = 90
    private const val MAX_CROP_PERCENT = 30
    private const val CHROMA_WEIGHT = 4L
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
     * LEDs are linear while screens apply the sRGB curve, so dark scenes would glow pale white.
     * Curving only the brightest channel and scaling the rest keeps the hue.
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
