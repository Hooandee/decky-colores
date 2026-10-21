package com.hooandee.colores.ambient

import com.hooandee.colores.led.RgbColor
import java.io.File
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class AmbientSamplingGoldenTest {
    private val vectors = JSONArray(File("../../shared/golden/ambient-sampling.json").readText())

    @Test
    fun `matches every shared ambient sampling golden vector`() {
        for (index in 0 until vectors.length()) {
            val vector = vectors.getJSONObject(index)
            val frame = vector.getJSONObject("input").toFrame()
            val expected = vector.getJSONObject("expected")
            when (vector.getString("operation")) {
                "content_box" ->
                    assertEquals(
                        vector.getString("id"),
                        ContentBox(expected.getInt("left"), expected.getInt("top"), expected.getInt("right"), expected.getInt("bottom")),
                        AmbientSampler.contentBox(frame),
                    )
                "region_color" -> {
                    val color = expected.getJSONObject("color")
                    assertEquals(
                        vector.getString("id"),
                        RgbColor(color.getInt("r"), color.getInt("g"), color.getInt("b")),
                        AmbientSampler.regionColor(frame, 0, 0, frame.width, frame.height),
                    )
                }
            }
        }
    }

    @Test
    fun `tracker crops quickly and restores slowly`() {
        val tracker = ContentBoxTracker()
        val full = ContentBox(0, 0, 10, 10)
        val cropped = ContentBox(0, 2, 10, 8)
        tracker.update(full)
        repeat(ContentBoxTracker.ADOPT_FRAMES - 1) { assertEquals(full, tracker.update(cropped)) }
        assertEquals(cropped, tracker.update(cropped))
        repeat(ContentBoxTracker.RELEASE_FRAMES - 1) { assertEquals(cropped, tracker.update(full)) }
        assertEquals(full, tracker.update(full))
    }

    @Test
    fun `tracker grows back even when the bigger detections flicker`() {
        val tracker = ContentBoxTracker()
        val cropped = ContentBox(0, 2, 10, 8)
        tracker.update(cropped)
        repeat(ContentBoxTracker.RELEASE_FRAMES) { index ->
            tracker.update(if (index % 2 == 0) ContentBox(0, 0, 10, 10) else ContentBox(0, 1, 10, 9))
        }
        assertEquals(true, tracker.box!!.area > cropped.area)
    }

    @Test
    fun `padded row stride is honored`() {
        val width = 2
        val rowStride = 16
        val bytes = ByteArray(rowStride * 2)
        for (y in 0 until 2) for (x in 0 until width) {
            val offset = y * rowStride + x * 4
            bytes[offset] = 200.toByte()
            bytes[offset + 1] = 40
            bytes[offset + 2] = 20
        }
        for (pad in 8 until rowStride) bytes[pad] = 0xFF.toByte()
        val frame = AmbientPixelFrame(width, 2, pixelStride = 4, rowStride = rowStride, bytes = bytes)
        assertEquals(RgbColor(200, 40, 20), AmbientSampler.regionColor(frame, 0, 0, width, 2))
        assertEquals(ContentBox(0, 0, 2, 2), AmbientSampler.contentBox(frame))
    }

    private fun JSONObject.toFrame(): AmbientPixelFrame {
        val width = getInt("width")
        val height = getInt("height")
        val pixels = getJSONArray("pixels")
        val bytes = ByteArray(width * height * 4)
        for (pixel in 0 until pixels.length()) {
            val rgb = pixels.getJSONArray(pixel)
            for (channel in 0 until 3) bytes[pixel * 4 + channel] = rgb.getInt(channel).toByte()
            bytes[pixel * 4 + 3] = 0xFF.toByte()
        }
        return AmbientPixelFrame(width, height, pixelStride = 4, rowStride = width * 4, bytes = bytes)
    }
}
