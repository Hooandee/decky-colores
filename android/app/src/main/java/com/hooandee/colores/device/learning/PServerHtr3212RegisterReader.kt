package com.hooandee.colores.device.learning

import com.hooandee.colores.led.Htr3212Command
import com.hooandee.colores.led.PServerCommandExecutor
import com.hooandee.colores.led.restoreOwnerAccess
import com.hooandee.colores.led.shareWithPServer
import com.hooandee.colores.led.shellQuoted
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
        if (!executor.available || bus !in BUS_RANGE || address != HTR3212_I2C_ADDRESS || registers !in AUDITED_BANKS) return null
        return try {
            outputFile.writeText("")
            outputFile.shareWithPServer(writable = true)
            val reads = registers.joinToString("; ") { register -> "i2cget -f -y $bus 0x%02x 0x%02x".format(address, register) }
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

    private companion object {
        val BUS_RANGE = 0..31
        val PWM_REGISTERS = (0x0d..0x18).toList()
        val AUDITED_BANKS = listOf(PWM_REGISTERS, Htr3212Command.CONTROL_REGISTERS)
    }
}
