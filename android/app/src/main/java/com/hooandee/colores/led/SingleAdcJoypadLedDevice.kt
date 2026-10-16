package com.hooandee.colores.led

import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

data class SingleAdcJoypadDescriptor(
    val basePath: String = DEFAULT_BASE_PATH,
    val vendorEffects: Boolean = false,
) : LedDescriptor {
    companion object {
        const val DEFAULT_BASE_PATH = "/sys/bus/platform/devices/singleadc-joypad"
    }
}

class SingleAdcJoypadLedDevice internal constructor(
    private val descriptor: SingleAdcJoypadDescriptor,
    private val access: SysfsAccess,
    private val scope: CoroutineScope,
    private val nowMs: () -> Long = SystemClock::elapsedRealtime,
) : LedDevice {
    constructor(
        descriptor: SingleAdcJoypadDescriptor,
        scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    ) : this(descriptor, FileSysfsAccess, scope)

    override val recommendedFrameIntervalMs: Long =
        if (descriptor.vendorEffects) WRITE_INTERVAL_MS else CONSERVATIVE_FRAME_INTERVAL_MS

    @Volatile
    private var applied: Frame? = null

    @Volatile
    private var requested: Frame? = null

    @Volatile
    private var generation = 0L

    @Volatile
    private var appliedAtMs = 0L

    private val writer =
        ConflatedLedWriter(
            scope,
            label = "singleadc",
            intervalMs = recommendedFrameIntervalMs,
            unchanged = ::alreadyApplied,
            write = ::writeFrame,
        )

    override val available: Boolean
        get() = access.canWrite(node("custum_rgb_r")) && access.canWrite(node("led_set"))

    override val supportsPerZone: Boolean = false

    override val softwareEffects: Boolean = descriptor.vendorEffects

    override val hardwareEffects: List<HardwareEffect> =
        if (descriptor.vendorEffects) {
            EFFECTS.map { HardwareEffect(it.id, it.colorStops, it.defaultSpeed, it.previewColors) }
        } else {
            emptyList()
        }

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
            submit(
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
        if (!descriptor.vendorEffects) return false
        val spec = EFFECTS.firstOrNull { it.id == effectId } ?: return false
        val first = colors.firstOrNull() ?: RgbColor(255, 255, 255)
        val second = colors.getOrNull(1) ?: first
        return submit(
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

    override suspend fun close() = writer.close()

    override fun invalidate() {
        forget()
        val token = generation
        scope.launch {
            // The vendor restores its own color shortly after screen-on, even in modes that write once.
            VENDOR_RESTORE_RESENDS_MS.forEach { waitMs ->
                delay(waitMs)
                if (generation != token) return@launch
                val frame = requested ?: return@launch
                forget()
                writer.submit(frame)
            }
        }
    }

    private fun submit(frame: Frame): Boolean {
        requested = frame
        return writer.submit(frame)
    }

    private fun forget() {
        generation += 1
        applied = null
    }

    // A repeated frame is rewritten periodically rather than trusted forever.
    private fun alreadyApplied(frame: Frame): Boolean = frame == applied && nowMs() - appliedAtMs < REASSERT_MS

    private fun writeFrame(frame: Frame): Boolean {
        val token = generation
        return writeNodes(frame).also { succeeded ->
            if (generation != token) return@also
            applied = frame.takeIf { succeeded }
            appliedAtMs = nowMs()
        }
    }

    private fun writeNodes(frame: Frame): Boolean {
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
        put("custum_rgb_r", main.red.coerceIn(0, 255))
        put("custum_rgb_g", main.green.coerceIn(0, 255))
        put("custum_rgb_b", main.blue.coerceIn(0, 255))
        if (!descriptor.vendorEffects) {
            put("led_level", frame.brightness)
            put("led_mode", STATIC_MODE)
            put("led_switch", 1)
            put("led_set", 1)
            return succeeded
        }
        val slotMain = if (frame.colored) main else RgbColor(0, 0, 0)
        val slotFollow = if (frame.colored) frame.secondColor else RgbColor(0, 0, 0)
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
        const val CONSERVATIVE_FRAME_INTERVAL_MS = 200L
        const val REASSERT_MS = 250L
        val VENDOR_RESTORE_RESENDS_MS = listOf(300L, 700L)
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
