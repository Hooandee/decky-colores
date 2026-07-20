package com.hooandee.colores.led

internal object Htr3212Command {
    fun build(
        bus: Int,
        address: Int,
        colors: List<RgbColor>,
        logicalToDriverOrder: List<Int>,
        previous: List<RgbColor>?,
    ): String? {
        val commands =
            colors.mapIndexedNotNull { logicalIndex, color ->
                if (previous?.getOrNull(logicalIndex) == color) return@mapIndexedNotNull null
                val driverGroup = logicalToDriverOrder.getOrNull(logicalIndex) ?: return@mapIndexedNotNull null
                val register = RGB_START_REGISTER + driverGroup * CHANNELS_PER_GROUP
                listOf(
                    registerCommand(bus, address, register, color.red),
                    registerCommand(bus, address, register + 1, color.green),
                    registerCommand(bus, address, register + 2, color.blue),
                )
            }.flatten().toMutableList()
        if (commands.isEmpty()) return null
        commands += registerCommand(bus, address, APPLY_REGISTER, 0)
        return commands.joinToString(" && ")
    }

    private fun registerCommand(
        bus: Int,
        address: Int,
        register: Int,
        value: Int,
    ): String =
        "i2cset -f -y $bus ${address.hexByte()} ${register.hexByte()} ${value.coerceIn(0, 255).hexByte()} i"

    private fun Int.hexByte(): String = "0x%02x".format(this)

    private const val RGB_START_REGISTER = 0x01
    private const val CHANNELS_PER_GROUP = 3
    private const val APPLY_REGISTER = 0x25
}
