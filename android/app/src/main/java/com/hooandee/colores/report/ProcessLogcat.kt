package com.hooandee.colores.report

import java.util.concurrent.TimeUnit

/** Own-process logcat: apps can only read their own uid, and the ring buffer is lost on reboot. */
object ProcessLogcat {
    fun read(
        pid: Int,
        maxLines: Int = 800,
        maxChars: Int = 150 * 1024,
        timeoutMs: Long = 2_000,
    ): String? =
        runCatching {
            val process =
                ProcessBuilder("logcat", "-d", "-v", "threadtime", "-t", maxLines.toString(), "--pid", pid.toString())
                    .redirectErrorStream(true)
                    .start()
            val output = StringBuilder()
            val reader = Thread {
                runCatching { process.inputStream.bufferedReader().forEachLine { output.appendLine(it) } }
            }.apply {
                isDaemon = true
                start()
            }
            if (!process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)) process.destroyForcibly()
            reader.join(timeoutMs)
            synchronized(output) { output.toString() }.takeLast(maxChars)
        }.getOrNull()
}
