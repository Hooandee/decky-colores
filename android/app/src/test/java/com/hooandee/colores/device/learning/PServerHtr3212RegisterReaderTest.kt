package com.hooandee.colores.device.learning

import com.hooandee.colores.led.PServerCommandExecutor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PServerHtr3212RegisterReaderTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `reads the audited PWM bank through PServer`() {
        val output = temporaryFolder.newFile("htr-registers")
        val executor =
            object : PServerCommandExecutor {
                override val available = true

                override fun execute(command: String): Boolean {
                    val expectedReads =
                        (0x0d..0x18).all { register ->
                            "i2cget -f -y 3 0x3c 0x%02x".format(register) in command
                        }
                    if (expectedReads && output.absolutePath in command) {
                        output.writeText((1..12).joinToString("\n") { "0x%02x".format(it) })
                    }
                    return true
                }
            }
        val reader = PServerHtr3212RegisterReader(executor, output)

        val values = reader.read(3, 0x3c, (0x0d..0x18).toList())

        assertEquals((1..12).toList(), values)
    }

    @Test
    fun `rejects reads outside the audited PWM bank`() {
        val output = temporaryFolder.newFile("htr-registers")
        val executor =
            object : PServerCommandExecutor {
                override val available = true

                override fun execute(command: String): Boolean {
                    output.writeText("0x01")
                    return true
                }
            }
        val reader = PServerHtr3212RegisterReader(executor, output)

        assertNull(reader.read(3, 0x3c, listOf(0x01)))
    }

    @Test
    fun `rejects malformed output instead of shifting register values`() {
        val output = temporaryFolder.newFile("htr-registers")
        val executor =
            object : PServerCommandExecutor {
                override val available = true

                override fun execute(command: String): Boolean {
                    output.writeText((1..12).joinToString("\n", postfix = "\nnot-a-byte") { "0x%02x".format(it) })
                    return true
                }
            }
        val reader = PServerHtr3212RegisterReader(executor, output)

        assertNull(reader.read(3, 0x3c, (0x0d..0x18).toList()))
    }
}
