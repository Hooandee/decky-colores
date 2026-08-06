package com.hooandee.colores.led

internal object Htr3212Command {
    fun build(
        bus: Int,
        address: Int,
        colors: List<RgbColor>,
        logicalToDriverOrder: List<Int>,
        previous: List<RgbColor>?,
        rgbStartRegister: Int = RGB_START_REGISTER,
        blockWrite: Boolean = false,
    ): String? {
        val mappedColors =
            colors.mapIndexedNotNull { logicalIndex, color ->
                logicalToDriverOrder.getOrNull(logicalIndex)?.let { driverGroup -> driverGroup to color }
            }
        val changedColors =
            colors.mapIndexedNotNull { logicalIndex, color ->
                if (previous?.getOrNull(logicalIndex) == color) return@mapIndexedNotNull null
                logicalToDriverOrder.getOrNull(logicalIndex)?.let { driverGroup -> driverGroup to color }
            }
        if (changedColors.isEmpty()) return null

        val driverColors = mappedColors.sortedBy { it.first }
        val commands =
            if (blockWrite && driverColors.map { it.first } == driverColors.indices.toList()) {
                mutableListOf(
                    blockCommand(
                        bus = bus,
                        address = address,
                        register = rgbStartRegister,
                        values = driverColors.flatMap { (_, color) -> listOf(color.red, color.green, color.blue) },
                    ),
                )
            } else {
                changedColors.flatMap { (driverGroup, color) ->
                    val register = rgbStartRegister + driverGroup * CHANNELS_PER_GROUP
                    listOf(
                        registerCommand(bus, address, register, color.red),
                        registerCommand(bus, address, register + 1, color.green),
                        registerCommand(bus, address, register + 2, color.blue),
                    )
                }.toMutableList()
            }
        commands += registerCommand(bus, address, APPLY_REGISTER, 0)
        return commands.joinToString(" && ")
    }

    private fun blockCommand(
        bus: Int,
        address: Int,
        register: Int,
        values: List<Int>,
    ): String =
        "i2cset -f -y $bus ${address.hexByte()} ${register.hexByte()} " +
            values.joinToString(" ") { it.coerceIn(0, 255).hexByte() } +
            " i"

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
