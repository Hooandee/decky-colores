package com.hooandee.colores.device.learning

import com.hooandee.colores.led.Htr3212Descriptor
import com.hooandee.colores.led.SettingsProviderDescriptor
import java.io.File

internal const val HTR3212_INFORMATION_ID = "android-i2c-htr3212"
internal const val HTR3212_PROBE_ID = "htr3212-multipoint"
internal const val HTR3212_PROBE_VERSION = 3

data class I2cController(
    val bus: Int,
    val address: Int,
    val driver: String,
)

fun interface I2cTopologyReader {
    fun read(): List<I2cController>
}

class SysfsI2cTopologyReader(
    private val root: File = File("/sys/bus/i2c/devices"),
) : I2cTopologyReader {
    override fun read(): List<I2cController> =
        root.listFiles()
            .orEmpty()
            .mapNotNull { node ->
                val match = DEVICE_NAME.matchEntire(node.name) ?: return@mapNotNull null
                val driver = runCatching { File(node, "name").readText().trim() }.getOrNull()?.takeIf(String::isNotBlank)
                    ?: return@mapNotNull null
                I2cController(
                    bus = match.groupValues[1].toIntOrNull() ?: return@mapNotNull null,
                    address = match.groupValues[2].toIntOrNull(16) ?: return@mapNotNull null,
                    driver = driver.lowercase(),
                )
            }.sortedWith(compareBy(I2cController::bus, I2cController::address, I2cController::driver))

    private companion object {
        val DEVICE_NAME = Regex("(\\d+)-([0-9a-fA-F]{4})")
    }
}

class Htr3212InformationCartridge(
    private val topologyReader: I2cTopologyReader = SysfsI2cTopologyReader(),
) : InformationCartridge {
    override val id = HTR3212_INFORMATION_ID
    override val version = 1
    override val requiredFactKeys = setOf(FACT_SETTINGS_PSERVER)

    override fun inspect(context: HardwareLearningContext): InformationCartridgeResult {
        val controllers = topologyReader.read()
        val left = controllers.singleOrNull { it.driver == LEFT_DRIVER && it.address == ADDRESS }
        val right = controllers.singleOrNull { it.driver == RIGHT_DRIVER && it.address == ADDRESS }
        val facts =
            buildList {
                left?.let { add(it.toFact(FACT_HTR3212_LEFT)) }
                right?.let { add(it.toFact(FACT_HTR3212_RIGHT)) }
                if (left != null && right != null && left.bus != right.bus) {
                    add(HardwareFact(FACT_HTR3212_PAIR, "2", FactEvidence.OBSERVED, id))
                }
            }
        val base =
            context.candidates
                .firstOrNull { it.surface == ProbeSurface.SETTINGS_PSERVER }
                ?.descriptor as? SettingsProviderDescriptor
        val candidate =
            if (base != null && left != null && right != null && left.bus != right.bus) {
                ProbeCandidate(
                    cartridgeId = HTR3212_PROBE_ID,
                    cartridgeVersion = HTR3212_PROBE_VERSION,
                    surface = ProbeSurface.HTR3212,
                    descriptor =
                        base.copy(
                            driver = "htr3212",
                            zones = TOTAL_ZONES,
                            htr3212 =
                                Htr3212Descriptor(
                                    leftBus = left.bus,
                                    rightBus = right.bus,
                                    address = ADDRESS,
                                    leftOrder = DEFAULT_ORDER,
                                    rightOrder = DEFAULT_ORDER,
                                    rgbStartRegister = AYN_RGB_START_REGISTER,
                                    explicitInitialization = context.identity.isValidatedRp5(),
                                ),
                        ),
                    signalKeys = setOf("htr3212_left", "htr3212_right", "htr3212_pair", "htr3212_bank_0d"),
                )
            } else {
                null
            }
        return InformationCartridgeResult(facts = facts, candidates = listOfNotNull(candidate))
    }

    private fun I2cController.toFact(key: String): HardwareFact =
        HardwareFact(key, "bus=$bus,address=0x%02x".format(address), FactEvidence.OBSERVED, id)

    private fun com.hooandee.colores.device.AndroidDeviceIdentity.isValidatedRp5(): Boolean =
        model.equals("Retroid Pocket 5", ignoreCase = true)

    private companion object {
        const val LEFT_DRIVER = "htr3212l"
        const val RIGHT_DRIVER = "htr3212r"
        const val ADDRESS = 0x3c
        const val TOTAL_ZONES = 8
        const val AYN_RGB_START_REGISTER = 0x0d
        val DEFAULT_ORDER = listOf(0, 1, 2, 3)
    }
}
