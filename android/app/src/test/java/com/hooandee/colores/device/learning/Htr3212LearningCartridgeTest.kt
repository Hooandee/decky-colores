package com.hooandee.colores.device.learning

import com.hooandee.colores.device.GenericVendorLed
import com.hooandee.colores.device.DeviceCapabilities
import com.hooandee.colores.led.Htr3212Descriptor
import com.hooandee.colores.led.PServerCommandExecutor
import com.hooandee.colores.led.SettingsProviderDescriptor
import com.hooandee.colores.led.SystemSettingsStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Htr3212LearningCartridgeTest {
    @Test
    fun `multipoint probe writes only the audited HTR banks`() {
        val store = FakeSettingsStore(originalSettings())
        val executor = FakeExecutor()
        val cartridge = Htr3212LearningCartridge(store, executor, settleVendor = {})
        val candidate = candidate()

        assertTrue(cartridge.execute(candidate, ProbeStep.COLOR))
        assertEquals(2, executor.commands.size)
        assertTrue(executor.commands[0].startsWith("i2cset -f -y 3 0x3c 0x0d"))
        assertTrue(executor.commands[1].startsWith("i2cset -f -y 5 0x3c 0x0d"))
        assertTrue(executor.commands.all { "0x25 0x00" in it })
    }

    @Test
    fun `zone probe isolates one of eight logical zones`() {
        val store = FakeSettingsStore(originalSettings())
        val executor = FakeExecutor()
        val cartridge = Htr3212LearningCartridge(store, executor, settleVendor = {})

        assertTrue(cartridge.execute(candidate(), ProbeStep.ZONE, zone = 4))
        assertEquals(2, executor.commands.size)
        assertFalse(executor.commands[0].contains("0x0d 0x8c"))
        assertTrue(executor.commands[1].contains("0x0d 0x8c"))
    }

    @Test
    fun `restore returns vendor settings and reasserts their solid colors`() {
        val original = originalSettings()
        val store = FakeSettingsStore(original.toMutableMap())
        val executor = FakeExecutor()
        val cartridge = Htr3212LearningCartridge(store, executor, settleVendor = {})
        val candidate = candidate()
        val snapshot = requireNotNull(cartridge.snapshot(candidate))

        assertTrue(cartridge.execute(candidate, ProbeStep.COLOR))
        assertEquals(RollbackStatus.RESTORED_WITHOUT_HARDWARE_READBACK, cartridge.restore(candidate, snapshot))
        assertEquals(original, store.values)
        assertTrue(executor.commands.takeLast(2).all { "0x25 0x00" in it })
    }

    @Test
    fun `candidate with another register bank is rejected`() {
        val store = FakeSettingsStore(originalSettings())
        val cartridge = Htr3212LearningCartridge(store, FakeExecutor(), settleVendor = {})
        val descriptor = candidate().descriptor as SettingsProviderDescriptor
        val unsafe = candidate().copy(descriptor = descriptor.copy(htr3212 = descriptor.htr3212?.copy(rgbStartRegister = 0x01)))

        assertFalse(cartridge.accepts(unsafe))
        assertFalse(cartridge.execute(unsafe, ProbeStep.COLOR))
    }

    @Test
    fun `eight unique physical observations calibrate driver order`() {
        val cartridge = Htr3212LearningCartridge(FakeSettingsStore(originalSettings()), FakeExecutor(), settleVendor = {})
        val locations =
            listOf(
                ZoneLocation.LEFT_BOTTOM_RIGHT,
                ZoneLocation.LEFT_TOP_LEFT,
                ZoneLocation.LEFT_TOP_RIGHT,
                ZoneLocation.LEFT_BOTTOM_LEFT,
                ZoneLocation.RIGHT_TOP_RIGHT,
                ZoneLocation.RIGHT_BOTTOM_RIGHT,
                ZoneLocation.RIGHT_BOTTOM_LEFT,
                ZoneLocation.RIGHT_TOP_LEFT,
            )
        val evidence =
            locations.mapIndexed { zone, location ->
                ProbeEvidence(ProbeStep.ZONE, zone, EvidenceLevel.USER_CONFIRMED, UserObservation.YES, location)
            }

        val calibrated = cartridge.bindingCandidate(candidate(), evidence)
        val hardware = (calibrated.descriptor as SettingsProviderDescriptor).htr3212

        assertEquals(listOf(1, 3, 0, 2), hardware?.leftOrder)
        assertEquals(listOf(3, 2, 1, 0), hardware?.rightOrder)
        assertTrue(cartridge.canBind(calibrated, DeviceCapabilities(true, false, true, 8), evidence))
    }

    @Test
    fun `duplicate physical observations cannot promote multipoint`() {
        val cartridge = Htr3212LearningCartridge(FakeSettingsStore(originalSettings()), FakeExecutor(), settleVendor = {})
        val evidence =
            (0 until 8).map { zone ->
                ProbeEvidence(ProbeStep.ZONE, zone, EvidenceLevel.USER_CONFIRMED, UserObservation.YES, ZoneLocation.LEFT_TOP_LEFT)
            }

        assertFalse(cartridge.canBind(candidate(), DeviceCapabilities(true, false, true, 8), evidence))
    }

    @Test
    fun `topology without confirmed color cannot promote multipoint`() {
        val cartridge = Htr3212LearningCartridge(FakeSettingsStore(originalSettings()), FakeExecutor(), settleVendor = {})
        val locations = ZoneLocation.entries
        val evidence =
            locations.mapIndexed { zone, location ->
                ProbeEvidence(ProbeStep.ZONE, zone, EvidenceLevel.USER_CONFIRMED, UserObservation.YES, location)
            }

        assertFalse(cartridge.canBind(candidate(), DeviceCapabilities(false, false, true, 8), evidence))
    }

    private fun candidate(): ProbeCandidate =
        ProbeCandidate(
            cartridgeId = HTR3212_PROBE_ID,
            cartridgeVersion = 1,
            surface = ProbeSurface.HTR3212,
            descriptor =
                GenericVendorLed.descriptor(8).copy(
                    driver = "htr3212",
                    htr3212 =
                        Htr3212Descriptor(
                            leftBus = 3,
                            rightBus = 5,
                            address = 0x3c,
                            leftOrder = listOf(0, 1, 2, 3),
                            rightOrder = listOf(0, 1, 2, 3),
                            rgbStartRegister = 0x0d,
                        ),
                ),
            signalKeys = setOf("htr3212_pair"),
        )

    private fun originalSettings() =
        linkedMapOf(
            "joystick_led_light_picker_color" to "#FF010203,#FF040506",
            "led_light_brightness_percent" to "0.72",
            "joystick_light_enabled" to "1,1",
            "left_joystick_light_enabled" to "1",
            "right_joystick_light_enabled" to "1",
        )

    private class FakeSettingsStore(
        val values: MutableMap<String, String>,
    ) : SystemSettingsStore {
        override val available = true

        override fun get(key: String): String? = values[key]

        override fun put(key: String, value: String): Boolean {
            if (key !in values) return false
            values[key] = value
            return true
        }
    }

    private class FakeExecutor : PServerCommandExecutor {
        override val available = true
        val commands = mutableListOf<String>()

        override fun execute(command: String): Boolean {
            commands += command
            return true
        }
    }
}
