package com.hooandee.colores.led

import android.annotation.SuppressLint
import android.content.Context
import android.os.IBinder
import android.os.Parcel
import android.provider.Settings
import java.io.File

internal interface PServerCommandExecutor {
    val available: Boolean

    fun execute(command: String): Boolean
}

internal class PServerSystemSettingsStore(
    private val readValue: (String) -> String?,
    private val executor: PServerCommandExecutor,
) : SystemSettingsStore {
    constructor(
        context: Context,
        executor: PServerCommandExecutor = AndroidPServerCommandExecutor(),
    ) : this(
        readValue = { key -> Settings.System.getString(context.contentResolver, key) },
        executor = executor,
    )

    override val available: Boolean
        get() = executor.available

    override fun get(key: String): String? = readValue(key)

    override fun put(
        key: String,
        value: String,
    ): Boolean {
        if (!available) return false
        if (!executor.execute("settings put system ${key.shellQuoted()} ${value.shellQuoted()}")) return false
        return readValue(key) == value
    }
}

@SuppressLint("DiscouragedPrivateApi", "PrivateApi")
internal class AndroidPServerCommandExecutor : PServerCommandExecutor {
    @Volatile
    private var binder: IBinder? = findBinder()

    override val available: Boolean
        get() = activeBinder() != null

    override fun execute(command: String): Boolean {
        val service = activeBinder() ?: return false
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeStringArray(arrayOf(command, "0"))
            service.transact(0, data, reply, 0)
        } catch (_: Throwable) {
            if (binder === service) binder = null
            false
        } finally {
            data.recycle()
            reply.recycle()
        }
    }

    private fun activeBinder(): IBinder? =
        binder?.takeIf(IBinder::isBinderAlive)
            ?: findBinder().also { binder = it }

    private fun findBinder(): IBinder? =
        runCatching {
            val serviceManager = Class.forName("android.os.ServiceManager")
            val getService = serviceManager.getDeclaredMethod("getService", String::class.java)
            getService.invoke(serviceManager, "PServerBinder") as? IBinder
        }.getOrNull()
}

internal class VerifiedPServerCommandExecutor(
    private val delegate: PServerCommandExecutor,
    private val statusFile: File,
    private val attempts: Int = 200,
    private val pollIntervalMs: Long = 10,
) : PServerCommandExecutor {
    override val available: Boolean
        get() = delegate.available

    override fun execute(command: String): Boolean {
        val scriptFile = File(statusFile.parentFile, "${statusFile.name}.sh")
        val errorFile = File(statusFile.parentFile, "${statusFile.name}.err")
        if (!available || !prepareFiles(scriptFile, errorFile, command)) return false
        return try {
            if (!delegate.execute("/system/bin/sh ${scriptFile.absolutePath.shellQuoted()}")) return false
            repeat(attempts.coerceAtLeast(1)) {
                statusFile.readText().trim().toIntOrNull()?.let { return it == 0 }
                if (pollIntervalMs > 0) Thread.sleep(pollIntervalMs)
            }
            false
        } catch (_: Throwable) {
            false
        } finally {
            statusFile.restoreOwnerAccess()
            scriptFile.restoreOwnerAccess()
            errorFile.restoreOwnerAccess()
        }
    }

    private fun prepareFiles(
        scriptFile: File,
        errorFile: File,
        command: String,
    ): Boolean =
        runCatching {
            statusFile.writeText("")
            val statusPath = statusFile.absolutePath.shellQuoted()
            errorFile.writeText("")
            val errorPath = errorFile.absolutePath.shellQuoted()
            scriptFile.writeText("{\n$command\n} 2> $errorPath\ncolores_status=\$?\nprintf '%s' \"\$colores_status\" > $statusPath\n")
            statusFile.shareWithPServer(writable = true)
            scriptFile.shareWithPServer(writable = false)
            errorFile.shareWithPServer(writable = true)
        }.isSuccess
}

private fun File.shareWithPServer(writable: Boolean) {
    setReadable(false, false)
    setReadable(true, false)
    setWritable(false, false)
    setWritable(true, !writable)
}

private fun File.restoreOwnerAccess() {
    setReadable(false, false)
    setReadable(true, true)
    setWritable(false, false)
    setWritable(true, true)
}

private fun String.shellQuoted(): String = "'${replace("'", "'\"'\"'")}'"
