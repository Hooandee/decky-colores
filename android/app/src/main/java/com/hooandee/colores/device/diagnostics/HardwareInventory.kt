package com.hooandee.colores.device.diagnostics

import com.hooandee.colores.report.redactReportText
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.json.JSONArray
import org.json.JSONObject

data class HardwareInventory(
    val kernel: JSONObject,
    val sysfs: JSONObject,
    val hardware: JSONObject,
)

interface HardwareInventorySource {
    fun buildFields(): Map<String, String>

    fun systemProperty(name: String): String?

    fun kernelVersion(): String?

    fun list(path: String): List<String>?

    fun read(path: String): String?

    fun exists(path: String): Boolean

    fun canWrite(path: String): Boolean

    fun pserverAvailable(): Boolean

    fun runPServerScript(
        script: String,
        timeoutMs: Long,
    ): String?

    fun packageVersion(packageName: String): PackagePresence
}

sealed interface PackagePresence {
    data object Absent : PackagePresence

    data class Installed(
        val versionName: String?,
    ) : PackagePresence
}

class HardwareInventoryCollector(
    private val source: HardwareInventorySource,
    private val budgetMs: Long = 3_000,
    private val readTimeoutMs: Long = 300,
    private val pserverTimeoutMs: Long = 1_500,
    private val nowMs: () -> Long = { System.nanoTime() / 1_000_000 },
) {
    fun collect(): HardwareInventory {
        val executor = daemonExecutor()
        val run = Run(executor, nowMs() + budgetMs)
        return try {
            val build = run.build()
            val packages = run.vendorPackages()
            val kernel = JSONObject().put("version", run.text(run.bounded { source.kernelVersion() }, 200))
            val sysfs =
                JSONObject()
                    .put("leds", run.ledNodes())
                    .put("i2c", run.i2cDevices())
                    .put("singleadc_joypad", run.singleAdc())
                    .put("platform_devices", run.platformDevices())
            val hardware =
                JSONObject()
                    .put("build", build)
                    .put("vendor_packages", packages)
                    .put("pserver", run.pserver())
            hardware.put(
                "collection",
                JSONObject()
                    .put("budget_ms", budgetMs)
                    .put("elapsed_ms", (nowMs() - (run.deadline - budgetMs)).coerceAtLeast(0))
                    .put("budget_exhausted", run.exhausted)
                    .put("timeouts", run.timeouts)
                    .put("truncated", JSONArray(run.truncated.sorted())),
            )
            fitTotal(HardwareInventory(kernel, sysfs, hardware))
        } finally {
            executor.shutdownNow()
        }
    }

    private fun Run.build(): JSONObject {
        val fields = bounded { source.buildFields() }.orEmpty()
        val build = JSONObject()
        BUILD_KEYS.forEach { key -> fields[key]?.let { build.put(key, text(it, 200)) } }
        text(bounded { source.systemProperty("ro.board.platform") }, 80)?.let { build.put("board_platform", it) }
        return build
    }

    private fun Run.ledNodes(): JSONArray {
        val names = bounded { source.list(LEDS_ROOT) }.orEmpty().sorted()
        val nodes = CappedArray(LEDS_CAP)
        names.take(MAX_LED_NODES).forEach { name ->
            if (!hasTime()) return@forEach
            val path = "$LEDS_ROOT/$name"
            val files = bounded { source.list(path) }.orEmpty().sorted()
            val entry =
                JSONObject()
                    .put("name", text(name, 80))
                    .put("files", JSONArray(files.take(MAX_NODE_FILES).map { text(it, 48) }))
                    .put("brightness_writable", bounded { source.canWrite("$path/brightness") } == true)
            if ("multi_intensity" in files) {
                entry.put("multi_intensity_writable", bounded { source.canWrite("$path/multi_intensity") } == true)
            }
            if ("max_brightness" in files) entry.put("max_brightness", text(bounded { source.read("$path/max_brightness") }, 16))
            if ("multi_index" in files) entry.put("multi_index", text(bounded { source.read("$path/multi_index") }, 160))
            if ("trigger" in files) entry.put("trigger", text(activeTrigger(bounded { source.read("$path/trigger") }), 48))
            if (!nodes.add(entry)) truncated += "sysfs.leds"
        }
        if (names.size > MAX_LED_NODES) truncated += "sysfs.leds"
        return nodes.array
    }

    private fun Run.i2cDevices(): JSONObject {
        val names = bounded { source.list(I2C_ROOT) }.orEmpty().sorted()
        val devices = CappedArray(I2C_CAP)
        names.filter(I2C_DEVICE::matches).take(MAX_I2C_DEVICES).forEach { name ->
            if (!hasTime()) return@forEach
            val entry = JSONObject().put("device", name).put("name", text(bounded { source.read("$I2C_ROOT/$name/name") }, 64))
            if (!devices.add(entry)) truncated += "sysfs.i2c"
        }
        return JSONObject()
            .put("readable", names.isNotEmpty())
            .put("adapters", JSONArray(names.filter(I2C_ADAPTER::matches).take(MAX_I2C_DEVICES)))
            .put("devices", devices.array)
    }

    private fun Run.singleAdc(): JSONObject {
        val present = bounded { source.exists(SINGLEADC_ROOT) } == true
        val result = JSONObject().put("present", present)
        if (present) {
            result.put("files", JSONArray(bounded { source.list(SINGLEADC_ROOT) }.orEmpty().sorted().take(MAX_NODE_FILES * 2).map { text(it, 48) }))
        }
        return result
    }

    private fun Run.platformDevices(): JSONArray =
        JSONArray(
            bounded { source.list(PLATFORM_ROOT) }
                .orEmpty()
                .filter { PLATFORM_DEVICE.containsMatchIn(it) }
                .sorted()
                .take(MAX_PLATFORM_DEVICES)
                .map { text(it, 80) },
        )

    private fun Run.vendorPackages(): JSONArray =
        JSONArray(
            KNOWN_VENDOR_PACKAGES.map { packageName ->
                val presence = bounded { source.packageVersion(packageName) } ?: PackagePresence.Absent
                JSONObject()
                    .put("package", packageName)
                    .put("installed", presence is PackagePresence.Installed)
                    .put("version", (presence as? PackagePresence.Installed)?.versionName?.let { text(it, 48) })
            },
        )

    private fun Run.pserver(): JSONObject {
        val present = bounded { source.pserverAvailable() } == true
        val result = JSONObject().put("present", present)
        if (!present) return result
        val timeout = minOf(pserverTimeoutMs, remaining())
        if (timeout <= 0) {
            truncated += "hardware.pserver"
            return result.put("status", "skipped")
        }
        val output = bounded(timeout) { source.runPServerScript(PSERVER_SCRIPT, timeout) }
        if (output == null) return result.put("status", "no_output")
        val sections = parseSections(output)
        return result
            .put("status", "ok")
            .put(
                "settings",
                JSONObject()
                    .put("system", settingsLines(sections["settings_system"], "hardware.pserver.settings.system"))
                    .put("global", settingsLines(sections["settings_global"], "hardware.pserver.settings.global"))
                    .put("secure", settingsLines(sections["settings_secure"], "hardware.pserver.settings.secure")),
            ).put("leds", lines(sections["leds"], PSERVER_LIST_CAP, "hardware.pserver.leds"))
            .put("i2c_ls", lines(sections["i2c_ls"], PSERVER_I2C_CAP, "hardware.pserver.i2c_ls"))
            .put("i2c_names", lines(sections["i2c_names"], PSERVER_LIST_CAP, "hardware.pserver.i2c_names"))
            .put(
                "services",
                lines(sections["services"]?.filter { SERVICE_FILTER.containsMatchIn(it) }, PSERVER_LIST_CAP, "hardware.pserver.services"),
            )
    }

    private fun Run.settingsLines(
        raw: List<String>?,
        section: String,
    ): JSONArray {
        val filtered =
            raw.orEmpty().mapNotNull { line ->
                val key = line.substringBefore('=', "").trim()
                val value = line.substringAfter('=', "").trim()
                if (!isLightingSetting(key, value)) return@mapNotNull null
                "${key.take(80)}=${value.take(SETTINGS_VALUE_LIMIT)}"
            }
        return lines(filtered, PSERVER_SETTINGS_CAP, section)
    }

    private fun Run.lines(
        raw: List<String>?,
        cap: Int,
        section: String,
    ): JSONArray {
        val array = CappedArray(cap)
        raw.orEmpty().forEach { line ->
            val value = text(line, 200) ?: return@forEach
            if (value.isNotBlank() && !array.add(value)) truncated += section
        }
        return array.array
    }

    private fun fitTotal(inventory: HardwareInventory): HardwareInventory {
        if (inventory.size() <= TOTAL_CAP) return inventory
        inventory.hardware.optJSONObject("pserver")?.let { pserver ->
            inventory.hardware.put("pserver", JSONObject().put("present", pserver.optBoolean("present")).put("status", "omitted_size"))
        }
        if (inventory.size() <= TOTAL_CAP) return inventory
        inventory.sysfs.put("leds", JSONArray().put(JSONObject().put("omitted", "size")))
        if (inventory.size() <= TOTAL_CAP) return inventory
        return HardwareInventory(JSONObject(), JSONObject(), JSONObject().put("omitted", "size"))
    }

    private fun HardwareInventory.size(): Int =
        kernel.toString().toByteArray().size + sysfs.toString().toByteArray().size + hardware.toString().toByteArray().size

    private inner class Run(
        private val executor: ExecutorService,
        val deadline: Long,
    ) {
        var exhausted = false
        var timeouts = 0
        val truncated = mutableSetOf<String>()

        fun remaining(): Long = deadline - nowMs()

        fun hasTime(): Boolean = (remaining() > 0).also { if (!it) exhausted = true }

        fun <T> bounded(
            timeoutMs: Long = readTimeoutMs,
            block: () -> T?,
        ): T? {
            val limit = minOf(timeoutMs, remaining())
            if (limit <= 0) {
                exhausted = true
                return null
            }
            val future = runCatching { executor.submit<T?> { block() } }.getOrNull() ?: return null
            return try {
                future.get(limit, TimeUnit.MILLISECONDS)
            } catch (_: java.util.concurrent.TimeoutException) {
                timeouts += 1
                future.cancel(true)
                null
            } catch (_: Throwable) {
                future.cancel(true)
                null
            }
        }

        fun text(
            value: String?,
            limit: Int,
        ): String? = value?.trim()?.let(::redactReportText)?.take(limit)
    }

    private class CappedArray(
        private val cap: Int,
    ) {
        val array = JSONArray()
        private var used = 2

        fun add(value: Any): Boolean {
            val size = value.toString().toByteArray().size + 3
            if (used + size > cap) return false
            used += size
            array.put(value)
            return true
        }
    }

    companion object {
        const val TOTAL_CAP = 48 * 1024
        const val LEDS_ROOT = "/sys/class/leds"
        const val I2C_ROOT = "/sys/bus/i2c/devices"
        const val PLATFORM_ROOT = "/sys/bus/platform/devices"
        const val SINGLEADC_ROOT = "/sys/bus/platform/devices/singleadc-joypad"
        val KNOWN_VENDOR_PACKAGES = listOf("com.rp.gameassistant", "com.odin.gameassistant")
        val BUILD_KEYS =
            listOf(
                "manufacturer",
                "brand",
                "model",
                "device",
                "product",
                "board",
                "hardware",
                "soc_manufacturer",
                "soc_model",
                "fingerprint",
                "release",
                "sdk",
                "security_patch",
            )
        private const val LEDS_CAP = 14 * 1024
        private const val I2C_CAP = 5 * 1024
        private const val PSERVER_SETTINGS_CAP = 3 * 1024
        private const val PSERVER_LIST_CAP = 2 * 1024
        private const val PSERVER_I2C_CAP = 4 * 1024
        private const val SETTINGS_VALUE_LIMIT = 120
        private const val MAX_LED_NODES = 64
        private const val MAX_NODE_FILES = 32
        private const val MAX_I2C_DEVICES = 96
        private const val MAX_PLATFORM_DEVICES = 64
        private val I2C_DEVICE = Regex("\\d+-[0-9a-fA-F]{4}")
        private val I2C_ADAPTER = Regex("i2c-\\d+")
        private val PLATFORM_DEVICE = Regex("joy|pad|led|rgb|light|stick|htr|aw2|sn3|is31", RegexOption.IGNORE_CASE)
        private val SETTINGS_KEY_TOKENS =
            setOf("led", "leds", "light", "lights", "rgb", "joystick", "handle", "stick", "color", "colour", "breath", "breathing")
        private val SETTINGS_KEY_SEPARATORS = Regex("[_.-]+")
        private val SETTINGS_KEY_DENIED_PREFIXES = listOf("enabled_", "disabled_")
        private val SETTINGS_KEY_DENIED_SUFFIXES = listOf("_services")
        private val SETTINGS_KEY_DENIED_FRAGMENTS =
            listOf(
                "input_method",
                "listeners",
                "packages",
                "component",
                "accessibility",
                "notification",
                "account",
                "bluetooth_name",
                "device_name",
                "wifi",
            )
        private val COMPONENT_VALUE = Regex("[\\w.]+/[\\w.$]+")
        private val PACKAGE_VALUE = Regex("^[a-z]\\w*(\\.\\w+){2,}")
        private val SERVICE_FILTER = Regex("led|light|rgb|game|joy|pserver|vendor", RegexOption.IGNORE_CASE)
        private const val SETTINGS_GREP =
            "grep -iE '^[^=]*(^|[_.-])(leds?|lights?|rgb|joystick|handle|stick|colou?r|breath(ing)?)([_.-]|=)' | " +
                "grep -viE '^(enabled_|disabled_)|^[^=]*(_services|input_method|listeners|packages|component|accessibility|notification|account|bluetooth_name|device_name|wifi)[^=]*=' | " +
                "head -n 120"
        val PSERVER_SCRIPT =
            listOf(
                "echo '## settings_system'",
                "settings list system 2>/dev/null | $SETTINGS_GREP",
                "echo '## settings_global'",
                "settings list global 2>/dev/null | $SETTINGS_GREP",
                "echo '## settings_secure'",
                "settings list secure 2>/dev/null | $SETTINGS_GREP",
                "echo '## leds'",
                "ls /sys/class/leds 2>/dev/null | head -n 96",
                "echo '## i2c_ls'",
                "ls -l /sys/bus/i2c/devices 2>/dev/null | head -n 128",
                "echo '## i2c_names'",
                "for d in /sys/bus/i2c/devices/*-*; do [ -e \"\$d/name\" ] && echo \"\${d##*/}=\$(cat \"\$d/name\" 2>/dev/null)\"; done 2>/dev/null | head -n 128",
                "echo '## services'",
                "service list 2>/dev/null | grep -iE 'led|light|rgb|game|joy|pserver|vendor' | head -n 80",
            ).joinToString("\n")

        internal fun isLightingSetting(
            key: String,
            value: String,
        ): Boolean {
            val normalized = key.lowercase()
            if (normalized.isEmpty()) return false
            if (SETTINGS_KEY_DENIED_PREFIXES.any(normalized::startsWith)) return false
            if (SETTINGS_KEY_DENIED_SUFFIXES.any(normalized::endsWith)) return false
            if (SETTINGS_KEY_DENIED_FRAGMENTS.any(normalized::contains)) return false
            if (normalized.split(SETTINGS_KEY_SEPARATORS).none(SETTINGS_KEY_TOKENS::contains)) return false
            if (COMPONENT_VALUE.containsMatchIn(value) || PACKAGE_VALUE.containsMatchIn(value)) return false
            return true
        }

        internal fun parseSections(output: String): Map<String, List<String>> {
            val sections = linkedMapOf<String, MutableList<String>>()
            var current: MutableList<String>? = null
            output.lineSequence().forEach { line ->
                if (line.startsWith("## ")) {
                    current = mutableListOf<String>().also { sections[line.removePrefix("## ").trim()] = it }
                } else if (line.isNotBlank()) {
                    current?.add(line)
                }
            }
            return sections
        }

        internal fun activeTrigger(raw: String?): String? {
            val value = raw?.trim()?.takeIf(String::isNotEmpty) ?: return null
            return Regex("\\[([^\\]]+)]").find(value)?.groupValues?.get(1)?.trim() ?: value.takeIf { it.none(Char::isWhitespace) }
        }

        private fun daemonExecutor(): ExecutorService =
            Executors.newCachedThreadPool { runnable ->
                Thread(runnable, "colores-inventory").apply { isDaemon = true }
            }
    }
}
