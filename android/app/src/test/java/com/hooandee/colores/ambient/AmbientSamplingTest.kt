package com.hooandee.colores.ambient

import com.hooandee.colores.device.LedGridCell
import com.hooandee.colores.led.RgbColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AmbientSamplingTest {
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
    fun `full scene maps each stick grid cell across the whole screen`() {
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
        val frame =
            rgbaFrame(
                rows =
                    listOf(
                        listOf(colors[0], colors[1], colors[4], colors[5]),
                        listOf(colors[2], colors[3], colors[6], colors[7]),
                    ),
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

        val sampled = AmbientSampler.sample(frame, 8, layout, true, AmbientSamplingMode.FULL_SCENE)

        assertEquals(colors, sampled)
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
}
