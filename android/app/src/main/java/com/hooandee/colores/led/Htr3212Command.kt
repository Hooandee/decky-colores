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
        explicitInitialization: Boolean = false,
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
        val commands = mutableListOf<String>()
        if (previous == null && explicitInitialization) {
            commands += initializationCommands(bus, address)
        }
        commands +=
            if (blockWrite && driverColors.map { it.first } == driverColors.indices.toList()) {
                listOf(
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
                        registerCommand(bus, address, register, color.red, explicitInitialization),
                        registerCommand(bus, address, register + 1, color.green, explicitInitialization),
                        registerCommand(bus, address, register + 2, color.blue, explicitInitialization),
                    )
                }
            }
        commands += registerCommand(bus, address, APPLY_REGISTER, 0, explicitInitialization)
        if (previous == null && explicitInitialization) {
            commands += registerCommand(bus, address, SHUTDOWN_REGISTER, SHUTDOWN_ON, byteWrite = true)
        }
        return commands.joinToString(" && ")
    }

    private fun initializationCommands(
        bus: Int,
        address: Int,
    ): List<String> =
        buildList {
            add(registerCommand(bus, address, GLOBAL_CONTROL_REGISTER, GLOBAL_CONTROL_ENABLE, byteWrite = true))
            add(registerCommand(bus, address, OUTPUT_FREQUENCY_REGISTER, OUTPUT_FREQUENCY_22_KHZ, byteWrite = true))
            repeat(CHANNEL_COUNT) { channel ->
                add(registerCommand(bus, address, CONTROL_START_REGISTER + channel, CONTROL_LED_ON, byteWrite = true))
            }
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
        byteWrite: Boolean = false,
    ): String =
        "i2cset -f -y $bus ${address.hexByte()} ${register.hexByte()} ${value.coerceIn(0, 255).hexByte()} " +
            if (byteWrite) "b" else "i"

    private fun Int.hexByte(): String = "0x%02x".format(this)

    private const val RGB_START_REGISTER = 0x0d
    private const val CHANNELS_PER_GROUP = 3
    private const val CHANNEL_COUNT = 12
    private const val APPLY_REGISTER = 0x25
    private const val CONTROL_START_REGISTER = 0x32
    private const val GLOBAL_CONTROL_REGISTER = 0x4a
    private const val GLOBAL_CONTROL_ENABLE = 0x00
    private const val OUTPUT_FREQUENCY_REGISTER = 0x4b
    private const val OUTPUT_FREQUENCY_22_KHZ = 0x01
    private const val SHUTDOWN_REGISTER = 0x00
    private const val SHUTDOWN_ON = 0x01
    private const val CONTROL_LED_ON = 0x01
}
