package com.hooandee.colores.ambient

import com.hooandee.colores.device.LedGridCell
import com.hooandee.colores.led.RgbColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AmbientSamplingTest {
    @Test
    fun `capture surface preserves common landscape display aspect ratios`() {
        val cases =
            listOf(
                Triple(1280 to 960, AmbientCaptureDimensions(32, 24), "4:3"),
                Triple(1920 to 1080, AmbientCaptureDimensions(32, 18), "16:9"),
                Triple(1280 to 800, AmbientCaptureDimensions(32, 20), "16:10"),
                Triple(2400 to 1080, AmbientCaptureDimensions(32, 14), "20:9"),
                Triple(1024 to 1024, AmbientCaptureDimensions(32, 32), "1:1"),
            )

        cases.forEach { (display, expected, label) ->
            assertEquals(label, expected, ambientCaptureDimensions(display.first, display.second))
        }
    }

    @Test
    fun `capture surface preserves portrait display aspect ratios`() {
        assertEquals(AmbientCaptureDimensions(18, 32), ambientCaptureDimensions(1080, 1920))
        assertEquals(AmbientCaptureDimensions(24, 32), ambientCaptureDimensions(960, 1280))
    }

    @Test
    fun `global sampling reads RGBA rows with padding and repeats the average`() {
        val frame =
            rgbaFrame(
                rows =
                    listOf(
                        listOf(RgbColor(255, 0, 0), RgbColor(0, 255, 0)),
                        listOf(RgbColor(0, 0, 255), RgbColor(255, 255, 255)),
                    ),
                rowPadding = 8,
            )

        val sampled =
            AmbientSampler.sample(
                frame = frame,
                zones = 2,
                gridLayout = null,
                supportsPerZone = false,
                mode = AmbientSamplingMode.FULL_SCENE,
            )

        assertEquals(List(2) { RgbColor(127, 127, 127) }, sampled)
    }

    @Test
    fun `full scene maps every stick cell across different aspect ratios`() {
        val colors =
            listOf(
                RgbColor(255, 0, 0),
                RgbColor(255, 255, 0),
                RgbColor(0, 255, 0),
                RgbColor(0, 255, 255),
                RgbColor(0, 0, 255),
                RgbColor(255, 0, 255),
                RgbColor(255, 255, 255),
                RgbColor(0, 0, 0),
            )
        val layout =
            listOf(
                LedGridCell(0, 0, 0, "top_left"),
                LedGridCell(0, 0, 1, "top_right"),
                LedGridCell(0, 1, 0, "bottom_left"),
                LedGridCell(0, 1, 1, "bottom_right"),
                LedGridCell(1, 0, 0, "top_left"),
                LedGridCell(1, 0, 1, "top_right"),
                LedGridCell(1, 1, 0, "bottom_left"),
                LedGridCell(1, 1, 1, "bottom_right"),
            )

        listOf(
            Triple(12, 9, "4:3"),
            Triple(16, 9, "16:9"),
            Triple(16, 10, "16:10"),
            Triple(20, 9, "20:9"),
            Triple(9, 16, "9:16"),
        ).forEach { (width, height, label) ->
            val frame = zonedFrame(width, height, colors)
            val sampled = AmbientSampler.sample(frame, 8, layout, true, AmbientSamplingMode.FULL_SCENE)

            assertEquals(label, colors, sampled)
        }
    }

    @Test
    fun `bottom edge ignores the upper scene`() {
        val frame =
            rgbaFrame(
                rows =
                    listOf(
                        List(4) { RgbColor(255, 0, 0) },
                        List(4) { RgbColor(255, 0, 0) },
                        List(4) { RgbColor(0, 0, 255) },
                        List(4) { RgbColor(0, 0, 255) },
                    ),
            )
        val layout = List(4) { index -> LedGridCell(index / 2, 0, index % 2, null) }

        val sampled = AmbientSampler.sample(frame, 4, layout, true, AmbientSamplingMode.BOTTOM_EDGE)

        assertEquals(List(4) { RgbColor(0, 0, 255) }, sampled)
    }

    @Test
    fun `invalid frame degrades to black without reading outside its buffer`() {
        val frame = AmbientPixelFrame(4, 4, pixelStride = 4, rowStride = 16, bytes = ByteArray(7))

        assertEquals(
            List(3) { RgbColor(0, 0, 0) },
            AmbientSampler.sample(frame, 3, null, true, AmbientSamplingMode.FULL_SCENE),
        )
    }

    @Test
    fun `vividness matches Decky saturation mapping`() {
        val base = RgbColor(140, 120, 100)

        assertEquals(base, AmbientColorMath.vivid(base, 0))
        val vivid = AmbientColorMath.vivid(base, 100)
        assertTrue(vivid.red - vivid.blue > base.red - base.blue)
    }

    @Test
    fun `time based smoothing is stable across capture rates`() {
        val black = RgbColor(0, 0, 0)
        val white = RgbColor(255, 255, 255)

        val once = AmbientColorMath.smooth(black, white, smoothing = 75, elapsedMs = 100)
        val half = AmbientColorMath.smooth(black, white, smoothing = 75, elapsedMs = 50)
        val twice = AmbientColorMath.smooth(half, white, smoothing = 75, elapsedMs = 50)

        assertTrue(kotlin.math.abs(once.red - twice.red) <= 1)
        assertEquals(once.red, once.green)
        assertEquals(once.green, once.blue)
    }

    @Test
    fun `capture cadence drops excess frames without building a queue`() {
        assertEquals(false, AmbientCaptureCadence.shouldProcess(lastFrameMs = 1_000, nowMs = 1_099, fps = 10))
        assertEquals(true, AmbientCaptureCadence.shouldProcess(lastFrameMs = 1_000, nowMs = 1_100, fps = 10))
        assertEquals(true, AmbientCaptureCadence.shouldProcess(lastFrameMs = 0, nowMs = 1, fps = 30))
    }

    private fun rgbaFrame(
        rows: List<List<RgbColor>>,
        rowPadding: Int = 0,
    ): AmbientPixelFrame {
        val height = rows.size
        val width = rows.first().size
        val rowStride = width * 4 + rowPadding
        val bytes = ByteArray(rowStride * height)
        rows.forEachIndexed { y, row ->
            row.forEachIndexed { x, color ->
                val offset = y * rowStride + x * 4
                bytes[offset] = color.red.toByte()
                bytes[offset + 1] = color.green.toByte()
                bytes[offset + 2] = color.blue.toByte()
                bytes[offset + 3] = 0xFF.toByte()
            }
        }
        return AmbientPixelFrame(width, height, pixelStride = 4, rowStride = rowStride, bytes = bytes)
    }

    private fun zonedFrame(
        width: Int,
        height: Int,
        colors: List<RgbColor>,
    ): AmbientPixelFrame {
        val rows = MutableList(height) { MutableList(width) { RgbColor(0, 0, 0) } }
        repeat(2) { row ->
            repeat(4) { column ->
                val colorIndex = (column / 2) * 4 + row * 2 + column % 2
                for (y in row * height / 2 until (row + 1) * height / 2) {
                    for (x in column * width / 4 until (column + 1) * width / 4) {
                        rows[y][x] = colors[colorIndex]
                    }
                }
            }
        }
        return rgbaFrame(rows)
    }
}
