package com.hooandee.colores.led

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlin.math.roundToInt

data class SingleAdcJoypadDescriptor(
    val basePath: String = DEFAULT_BASE_PATH,
) : LedDescriptor {
    companion object {
        const val DEFAULT_BASE_PATH = "/sys/bus/platform/devices/singleadc-joypad"
    }
}

class SingleAdcJoypadLedDevice internal constructor(
    private val descriptor: SingleAdcJoypadDescriptor,
    private val access: SysfsAccess,
    scope: CoroutineScope,
) : LedDevice {
    constructor(
        descriptor: SingleAdcJoypadDescriptor,
        scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    ) : this(descriptor, FileSysfsAccess, scope)

    private val writer = ConflatedLedWriter(scope, WRITE_INTERVAL_MS, write = ::writeFrame)

    override val available: Boolean
        get() = access.canWrite(node("custum_rgb_r")) && access.canWrite(node("led_set"))

    override val supportsPerZone: Boolean = false

    override val hardwareEffects: List<HardwareEffect> =
        EFFECTS.map { HardwareEffect(it.id, it.colorStops, it.defaultSpeed, it.previewColors) }

    override suspend fun readState(): LedState {
        val color =
            RgbColor(
                red = readInt("custum_rgb_r", 0),
                green = readInt("custum_rgb_g", 0),
                blue = readInt("custum_rgb_b", 0),
            )
        return LedState(
            zoneColors = listOf(color),
            brightness = readInt("led_level", 100).coerceIn(0, 100),
            power = readInt("led_switch", 1) != 0,
        )
    }

    override suspend fun applyZones(
        colors: List<RgbColor>,
        brightness: Int,
        power: Boolean,
    ): Boolean =
        (colors.firstOrNull() ?: RgbColor(255, 255, 255)).let { c ->
            writer.submit(
                Frame(
                    color = c,
                    secondColor = c,
                    brightness = brightness.coerceIn(0, 100),
                    power = power,
                    ledMode = STATIC_MODE,
                    speed = 0,
                    colored = true,
                ),
            )
        }

    override suspend fun applySolid(
        color: RgbColor,
        brightness: Int,
        power: Boolean,
    ): Boolean = applyZones(listOf(color), brightness, power)

    override suspend fun applyHardwareEffect(
        effectId: String,
        colors: List<RgbColor>,
        brightness: Int,
        speed: Int,
        power: Boolean,
    ): Boolean {
        val spec = EFFECTS.firstOrNull { it.id == effectId } ?: return false
        val first = colors.firstOrNull() ?: RgbColor(255, 255, 255)
        val second = colors.getOrNull(1) ?: first
        return writer.submit(
            Frame(
                color = first,
                secondColor = second,
                brightness = brightness.coerceIn(0, 100),
                power = power,
                ledMode = spec.ledMode,
                speed = mapSpeed(speed),
                colored = spec.colorStops > 0,
            ),
        )
    }

    override fun invalidate() = Unit

    private fun writeFrame(frame: Frame): Boolean {
        var succeeded = true
        fun put(
            name: String,
            value: Int,
        ) {
            if (!access.write(node(name), "$value\n")) succeeded = false
        }
        if (!frame.power) {
            put("led_switch", 0)
            put("led_set", 1)
            return succeeded
        }
        val main = frame.color
        val follow = if (frame.colored) frame.secondColor else main
        put("custum_rgb_r", main.red.coerceIn(0, 255))
        put("custum_rgb_g", main.green.coerceIn(0, 255))
        put("custum_rgb_b", main.blue.coerceIn(0, 255))
        val slotMain = if (frame.colored) main else RgbColor(0, 0, 0)
        val slotFollow = if (frame.colored) follow else RgbColor(0, 0, 0)
        put("Led_rgb_r2", slotMain.red.coerceIn(0, 255))
        put("Led_rgb_g2", slotMain.green.coerceIn(0, 255))
        put("Led_rgb_b2", slotMain.blue.coerceIn(0, 255))
        put("Led_rgb_r1", slotFollow.red.coerceIn(0, 255))
        put("Led_rgb_g1", slotFollow.green.coerceIn(0, 255))
        put("Led_rgb_b1", slotFollow.blue.coerceIn(0, 255))
        put("led_level", frame.brightness)
        put("led_speed", frame.speed)
        put("led_mode", frame.ledMode)
        put("led_switch", 1)
        put("led_set", 1)
        return succeeded
    }

    private fun mapSpeed(percent: Int): Int = ((percent.coerceIn(0, 100) / 100.0) * MAX_SPEED).roundToInt().coerceIn(0, MAX_SPEED)

    private fun readInt(
        name: String,
        fallback: Int,
    ): Int = access.read(node(name))?.trim()?.toIntOrNull() ?: fallback

    private fun node(name: String): String = "${descriptor.basePath}/$name"

    private data class Frame(
        val color: RgbColor,
        val secondColor: RgbColor,
        val brightness: Int,
        val power: Boolean,
        val ledMode: Int,
        val speed: Int,
        val colored: Boolean,
    )

    private data class EffectSpec(
        val id: String,
        val ledMode: Int,
        val colorStops: Int,
        val defaultSpeed: Int,
        val previewColors: List<RgbColor>,
    )

    private companion object {
        const val WRITE_INTERVAL_MS = 80L
        const val STATIC_MODE = 1
        const val MAX_SPEED = 8

        private val RAINBOW =
            listOf(RgbColor(255, 0, 0), RgbColor(0, 255, 0), RgbColor(0, 0, 255))

        val EFFECTS =
            listOf(
                EffectSpec("breathing", ledMode = 2, colorStops = 2, defaultSpeed = 50, previewColors = listOf(RgbColor(255, 0, 0), RgbColor(0, 0, 255))),
                EffectSpec("rainbow", ledMode = 3, colorStops = 0, defaultSpeed = 50, previewColors = RAINBOW),
                EffectSpec("marquee", ledMode = 4, colorStops = 0, defaultSpeed = 50, previewColors = RAINBOW),
                EffectSpec("chasing", ledMode = 5, colorStops = 2, defaultSpeed = 50, previewColors = listOf(RgbColor(255, 0, 0), RgbColor(0, 0, 255))),
                EffectSpec("gaming", ledMode = 6, colorStops = 0, defaultSpeed = 50, previewColors = RAINBOW),
            )
    }
}
