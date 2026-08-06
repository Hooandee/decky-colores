package com.hooandee.colores.led

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class Htr3212CommandTest {
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
    fun `Retroid Pocket 5 keeps its validated register writes`() {
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
                previous = null,
            )

        assertEquals(
            "i2cset -f -y 1 0x3c 0x01 0x01 i && " +
                "i2cset -f -y 1 0x3c 0x02 0x02 i && " +
                "i2cset -f -y 1 0x3c 0x03 0x03 i && " +
                "i2cset -f -y 1 0x3c 0x04 0x04 i && " +
                "i2cset -f -y 1 0x3c 0x05 0x05 i && " +
                "i2cset -f -y 1 0x3c 0x06 0x06 i && " +
                "i2cset -f -y 1 0x3c 0x0a 0x07 i && " +
                "i2cset -f -y 1 0x3c 0x0b 0x08 i && " +
                "i2cset -f -y 1 0x3c 0x0c 0x09 i && " +
                "i2cset -f -y 1 0x3c 0x07 0x0a i && " +
                "i2cset -f -y 1 0x3c 0x08 0x0b i && " +
                "i2cset -f -y 1 0x3c 0x09 0x0c i && " +
                "i2cset -f -y 1 0x3c 0x25 0x00 i",
            command,
        )
    }

    @Test
    fun `later write sends one complete block when any zone changes`() {
        val previous = List(4) { RgbColor(10, 20, 30) }
        val colors = previous.toMutableList().also { it[2] = RgbColor(40, 50, 60) }

        assertEquals(
            "i2cset -f -y 0 0x3c 0x01 0x0a 0x14 0x1e 0x0a 0x14 0x1e " +
                "0x0a 0x14 0x1e 0x28 0x32 0x3c i && " +
                "i2cset -f -y 0 0x3c 0x25 0x00 i",
            Htr3212Command.build(0, 0x3c, colors, listOf(1, 2, 3, 0), previous, blockWrite = true),
        )
    }

    @Test
    fun `values are clamped to bytes`() {
        assertEquals(
            "i2cset -f -y 1 0x3c 0x01 0x00 i && " +
                "i2cset -f -y 1 0x3c 0x02 0xff i && " +
                "i2cset -f -y 1 0x3c 0x03 0x80 i && " +
                "i2cset -f -y 1 0x3c 0x25 0x00 i",
            Htr3212Command.build(
                bus = 1,
                address = 0x3c,
                colors = listOf(RgbColor(-1, 300, 128)),
                logicalToDriverOrder = listOf(0),
                previous = null,
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
                previous = null,
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
