package com.hooandee.colores.apps

import android.content.Context
import com.hooandee.colores.led.AndroidPServerCommandExecutor
import com.hooandee.colores.led.PServerCommandExecutor
import java.io.File

fun interface FocusedAppResolver {
    fun resolve(): String?
}

data object NoFocusedAppResolver : FocusedAppResolver {
    override fun resolve(): String? = null
}

internal class PServerFocusedAppResolver internal constructor(
    private val output: File,
    private val executor: PServerCommandExecutor,
) : FocusedAppResolver {
    constructor(context: Context) : this(
        output = File(context.cacheDir, "focused_activity"),
        executor = AndroidPServerCommandExecutor(),
    )

    override fun resolve(): String? {
        if (!executor.available) return null
        val prepared =
            runCatching {
                output.writeText("")
                output.setReadable(false, false)
                output.setReadable(true, false)
                output.setWritable(false, false)
                output.setWritable(true, false)
            }.isSuccess
        if (!prepared) return null
        val path = output.absolutePath.shellQuoted()
        return try {
            if (!executor.execute("dumpsys activity activities | grep -m 1 'ResumedActivity:' > $path")) return null
            runCatching { parseFocusedPackage(output.readText()) }.getOrNull()
        } finally {
            output.setReadable(false, false)
            output.setReadable(true, true)
            output.setWritable(false, false)
            output.setWritable(true, true)
        }
    }
}

private fun String.shellQuoted(): String = "'${replace("'", "'\"'\"'")}'"
