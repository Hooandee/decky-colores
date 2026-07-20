package com.hooandee.colores.led

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class Htr3212CommandTest {
    @Test
    fun `first write sends every RGB register in calibrated driver order and applies`() {
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
            listOf(
                "i2cset -f -y 1 0x3c 0x01 0x01 i",
                "i2cset -f -y 1 0x3c 0x02 0x02 i",
                "i2cset -f -y 1 0x3c 0x03 0x03 i",
                "i2cset -f -y 1 0x3c 0x04 0x04 i",
                "i2cset -f -y 1 0x3c 0x05 0x05 i",
                "i2cset -f -y 1 0x3c 0x06 0x06 i",
                "i2cset -f -y 1 0x3c 0x0a 0x07 i",
                "i2cset -f -y 1 0x3c 0x0b 0x08 i",
                "i2cset -f -y 1 0x3c 0x0c 0x09 i",
                "i2cset -f -y 1 0x3c 0x07 0x0a i",
                "i2cset -f -y 1 0x3c 0x08 0x0b i",
                "i2cset -f -y 1 0x3c 0x09 0x0c i",
                "i2cset -f -y 1 0x3c 0x25 0x00 i",
            ).joinToString(" && "),
            command,
        )
    }

    @Test
    fun `later write sends only changed zone registers and apply`() {
        val previous = List(4) { RgbColor(10, 20, 30) }
        val colors = previous.toMutableList().also { it[2] = RgbColor(40, 50, 60) }

        assertEquals(
            "i2cset -f -y 0 0x3c 0x0a 0x28 i && " +
                "i2cset -f -y 0 0x3c 0x0b 0x32 i && " +
                "i2cset -f -y 0 0x3c 0x0c 0x3c i && " +
                "i2cset -f -y 0 0x3c 0x25 0x00 i",
            Htr3212Command.build(0, 0x3c, colors, listOf(1, 2, 3, 0), previous),
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
    fun `unchanged frame produces no command`() {
        val colors = List(4) { RgbColor(1, 2, 3) }

        assertNull(Htr3212Command.build(1, 0x3c, colors, listOf(0, 1, 3, 2), colors))
    }
}
