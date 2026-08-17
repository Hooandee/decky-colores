package com.hooandee.colores.device.learning

import com.hooandee.colores.led.PServerCommandExecutor
import java.io.File

internal class PServerHtr3212RegisterReader(
    private val executor: PServerCommandExecutor,
    private val outputFile: File,
) : Htr3212RegisterReader {
    override fun read(
        bus: Int,
        address: Int,
        registers: List<Int>,
    ): List<Int>? {
        if (!executor.available || bus !in BUS_RANGE || address != ADDRESS || registers != PWM_REGISTERS) return null
        return try {
            outputFile.writeText("")
            outputFile.shareWithPServer()
            val reads = registers.joinToString("; ") { register -> "i2cget -f -y $bus 0x3c 0x%02x".format(register) }
            if (!executor.execute("{ $reads; } > ${outputFile.absolutePath.shellQuoted()}")) return null
            val tokens = outputFile.readText().split(Regex("\\s+")).filter(String::isNotBlank)
            if (tokens.size != registers.size) return null
            tokens.map { parseByte(it) ?: return null }
        } catch (_: Throwable) {
            null
        } finally {
            outputFile.restoreOwnerAccess()
        }
    }

    private fun parseByte(value: String): Int? =
        value.removePrefix("0x").toIntOrNull(16)?.takeIf { it in 0..255 }

    private fun File.shareWithPServer() {
        setReadable(false, false)
        setReadable(true, false)
        setWritable(false, false)
        setWritable(true, false)
    }

    private fun File.restoreOwnerAccess() {
        setReadable(false, false)
        setReadable(true, true)
        setWritable(false, false)
        setWritable(true, true)
    }

    private fun String.shellQuoted(): String = "'${replace("'", "'\"'\"'")}'"

    private companion object {
        val BUS_RANGE = 0..31
        const val ADDRESS = 0x3c
        val PWM_REGISTERS = (0x0d..0x18).toList()
    }
}
