package com.hooandee.colores.led

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Htr3212CommandTest {
    @Test
    fun `first RP5 frame initializes every HTR channel before isolating one point`() {
        val command =
            Htr3212Command.build(
                bus = 1,
                address = 0x3c,
                colors =
                    listOf(
                        RgbColor(255, 0, 255),
                        RgbColor(0, 0, 0),
                        RgbColor(0, 0, 0),
                        RgbColor(0, 0, 0),
                    ),
                logicalToDriverOrder = listOf(0, 1, 2, 3),
                previous = null,
                rgbStartRegister = 0x0d,
                explicitInitialization = true,
            )

        assertEquals(
            "i2cset -f -y 1 0x3c 0x4a 0x00 b && " +
                "i2cset -f -y 1 0x3c 0x4b 0x01 b && " +
                (0 until 12).joinToString(" && ") { channel ->
                    "i2cset -f -y 1 0x3c 0x%02x 0x01 b".format(0x32 + channel)
                } +
                " && i2cset -f -y 1 0x3c 0x0d 0xff b && " +
                "i2cset -f -y 1 0x3c 0x0e 0x00 b && " +
                "i2cset -f -y 1 0x3c 0x0f 0xff b && " +
                "i2cset -f -y 1 0x3c 0x10 0x00 b && " +
                "i2cset -f -y 1 0x3c 0x11 0x00 b && " +
                "i2cset -f -y 1 0x3c 0x12 0x00 b && " +
                "i2cset -f -y 1 0x3c 0x13 0x00 b && " +
                "i2cset -f -y 1 0x3c 0x14 0x00 b && " +
                "i2cset -f -y 1 0x3c 0x15 0x00 b && " +
                "i2cset -f -y 1 0x3c 0x16 0x00 b && " +
                "i2cset -f -y 1 0x3c 0x17 0x00 b && " +
                "i2cset -f -y 1 0x3c 0x18 0x00 b && " +
                "i2cset -f -y 1 0x3c 0x25 0x00 b && " +
                "i2cset -f -y 1 0x3c 0x00 0x01 b",
            command,
        )
    }

    @Test
    fun `full stick frame uses one bounded block write plus latch`() {
        val command =
            Htr3212Command.build(
                bus = 3,
                address = 0x3c,
                colors =
                    listOf(
                        RgbColor(1, 2, 3),
                        RgbColor(4, 5, 6),
                        RgbColor(7, 8, 9),
                        RgbColor(10, 11, 12),
                    ),
                logicalToDriverOrder = listOf(0, 1, 2, 3),
                previous = null,
                rgbStartRegister = 0x0d,
                blockWrite = true,
            )

        assertEquals(
            "i2cset -f -y 3 0x3c 0x0d 0x01 0x02 0x03 0x04 0x05 0x06 " +
                "0x07 0x08 0x09 0x0a 0x0b 0x0c i && " +
                "i2cset -f -y 3 0x3c 0x25 0x00 i",
            command,
        )
    }

    @Test
    fun `Retroid Pocket 5 writes all colors to the physical PWM bank`() {
        val command =
            Htr3212Command.build(
                bus = 1,
                address = 0x3c,
                colors =
                    listOf(
                        RgbColor(1, 2, 3),
                        RgbColor(4, 5, 6),
                        RgbColor(7, 8, 9),
                        RgbColor(10, 11, 12),
                    ),
                logicalToDriverOrder = listOf(0, 1, 3, 2),
                previous = List(4) { RgbColor(255, 255, 255) },
                rgbStartRegister = 0x0d,
                explicitInitialization = true,
            )

        assertEquals(
            "i2cset -f -y 1 0x3c 0x0d 0x01 b && " +
                "i2cset -f -y 1 0x3c 0x0e 0x02 b && " +
                "i2cset -f -y 1 0x3c 0x0f 0x03 b && " +
                "i2cset -f -y 1 0x3c 0x10 0x04 b && " +
                "i2cset -f -y 1 0x3c 0x11 0x05 b && " +
                "i2cset -f -y 1 0x3c 0x12 0x06 b && " +
                "i2cset -f -y 1 0x3c 0x16 0x07 b && " +
                "i2cset -f -y 1 0x3c 0x17 0x08 b && " +
                "i2cset -f -y 1 0x3c 0x18 0x09 b && " +
                "i2cset -f -y 1 0x3c 0x13 0x0a b && " +
                "i2cset -f -y 1 0x3c 0x14 0x0b b && " +
                "i2cset -f -y 1 0x3c 0x15 0x0c b && " +
                "i2cset -f -y 1 0x3c 0x25 0x00 b",
            command,
        )
    }

    @Test
    fun `later write sends one complete block when any zone changes`() {
        val previous = List(4) { RgbColor(10, 20, 30) }
        val colors = previous.toMutableList().also { it[2] = RgbColor(40, 50, 60) }

        assertEquals(
            "i2cset -f -y 0 0x3c 0x0d 0x0a 0x14 0x1e 0x0a 0x14 0x1e " +
                "0x0a 0x14 0x1e 0x28 0x32 0x3c i && " +
                "i2cset -f -y 0 0x3c 0x25 0x00 i",
            Htr3212Command.build(0, 0x3c, colors, listOf(1, 2, 3, 0), previous, blockWrite = true),
        )
    }

    @Test
    fun `values are clamped to bytes`() {
        assertEquals(
            "i2cset -f -y 1 0x3c 0x0d 0x00 b && " +
                "i2cset -f -y 1 0x3c 0x0e 0xff b && " +
                "i2cset -f -y 1 0x3c 0x0f 0x80 b && " +
                "i2cset -f -y 1 0x3c 0x25 0x00 b",
            Htr3212Command.build(
                bus = 1,
                address = 0x3c,
                colors = listOf(RgbColor(-1, 300, 128)),
                logicalToDriverOrder = listOf(0),
                previous = listOf(RgbColor(1, 1, 1)),
                explicitInitialization = true,
            ),
        )
    }

    @Test
    fun `AYN Thor start register offsets the color registers to 0x0d`() {
        val command =
            Htr3212Command.build(
                bus = 3,
                address = 0x3c,
                colors = listOf(RgbColor(0x10, 0x20, 0x30)),
                logicalToDriverOrder = listOf(0),
                previous = listOf(RgbColor(1, 1, 1)),
                rgbStartRegister = 0x0d,
                blockWrite = true,
            )

        assertEquals(
            "i2cset -f -y 3 0x3c 0x0d 0x10 0x20 0x30 i && " +
                "i2cset -f -y 3 0x3c 0x25 0x00 i",
            command,
        )
    }

    @Test
    fun `unchanged frame produces no command`() {
        val colors = List(4) { RgbColor(1, 2, 3) }

        assertNull(Htr3212Command.build(1, 0x3c, colors, listOf(0, 1, 3, 2), colors))
    }
}
