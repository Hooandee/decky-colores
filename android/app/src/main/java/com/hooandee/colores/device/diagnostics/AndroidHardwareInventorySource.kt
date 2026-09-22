package com.hooandee.colores.device.diagnostics

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.hooandee.colores.led.AndroidPServerCommandExecutor
import com.hooandee.colores.led.PServerCommandExecutor
import com.hooandee.colores.led.restoreOwnerAccess
import com.hooandee.colores.led.shareWithPServer
import com.hooandee.colores.led.shellQuoted
import java.io.File
import java.util.concurrent.TimeUnit

internal class AndroidHardwareInventorySource(
    private val context: Context,
    private val executor: PServerCommandExecutor = AndroidPServerCommandExecutor(),
    private val workDirectory: File = context.cacheDir,
) : HardwareInventorySource {
    override fun buildFields(): Map<String, String> =
        buildMap {
            put("manufacturer", Build.MANUFACTURER.orEmpty())
            put("brand", Build.BRAND.orEmpty())
            put("model", Build.MODEL.orEmpty())
            put("device", Build.DEVICE.orEmpty())
            put("product", Build.PRODUCT.orEmpty())
            put("board", Build.BOARD.orEmpty())
            put("hardware", Build.HARDWARE.orEmpty())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                put("soc_manufacturer", Build.SOC_MANUFACTURER.orEmpty())
                put("soc_model", Build.SOC_MODEL.orEmpty())
            }
            put("fingerprint", Build.FINGERPRINT.orEmpty())
            put("release", Build.VERSION.RELEASE.orEmpty())
            put("sdk", Build.VERSION.SDK_INT.toString())
            put("security_patch", Build.VERSION.SECURITY_PATCH.orEmpty())
        }.filterValues(String::isNotBlank)

    override fun systemProperty(name: String): String? =
        runCatching {
            val process = ProcessBuilder("/system/bin/getprop", name).redirectErrorStream(true).start()
            if (!process.waitFor(PROPERTY_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                process.destroyForcibly()
                return@runCatching null
            }
            process.inputStream.bufferedReader().use { it.readText().trim() }
        }.getOrNull()?.takeIf(String::isNotBlank)

    override fun kernelVersion(): String? = System.getProperty("os.version")

    override fun list(path: String): List<String>? = File(path).list()?.toList()

    override fun read(path: String): String? =
        File(path).bufferedReader().use { reader ->
            val buffer = CharArray(READ_LIMIT)
            val count = reader.read(buffer)
            if (count <= 0) "" else String(buffer, 0, count)
        }

    override fun exists(path: String): Boolean = File(path).exists()

    override fun canWrite(path: String): Boolean = File(path).canWrite()

    override fun pserverAvailable(): Boolean = executor.available

    override fun runPServerScript(
        script: String,
        timeoutMs: Long,
    ): String? {
        val scriptFile = File(workDirectory, "colores_inventory.sh")
        val outputFile = File(workDirectory, "colores_inventory.out")
        val statusFile = File(workDirectory, "colores_inventory.status")
        val deadline = System.nanoTime() / 1_000_000 + timeoutMs
        return try {
            outputFile.writeText("")
            statusFile.writeText("")
            scriptFile.writeText(
                "{\n$script\n} 2>/dev/null | head -c $OUTPUT_LIMIT > ${outputFile.absolutePath.shellQuoted()}\n" +
                    "printf done > ${statusFile.absolutePath.shellQuoted()}\n",
            )
            scriptFile.shareWithPServer(writable = false)
            outputFile.shareWithPServer(writable = true)
            statusFile.shareWithPServer(writable = true)
            if (!executor.execute("/system/bin/sh ${scriptFile.absolutePath.shellQuoted()}")) return null
            while (System.nanoTime() / 1_000_000 < deadline) {
                if (statusFile.readText().trim() == "done") return outputFile.readText().take(OUTPUT_LIMIT)
                Thread.sleep(POLL_MS)
            }
            null
        } catch (_: Throwable) {
            null
        } finally {
            listOf(scriptFile, outputFile, statusFile).forEach { file ->
                file.restoreOwnerAccess()
                file.delete()
            }
        }
    }

    override fun packageVersion(packageName: String): PackagePresence =
        try {
            PackagePresence.Installed(context.packageManager.getPackageInfo(packageName, 0).versionName)
        } catch (_: PackageManager.NameNotFoundException) {
            PackagePresence.Absent
        }

    private companion object {
        const val PROPERTY_TIMEOUT_MS = 250L
        const val READ_LIMIT = 4096
        const val OUTPUT_LIMIT = 131072
        const val POLL_MS = 20L
    }
}
