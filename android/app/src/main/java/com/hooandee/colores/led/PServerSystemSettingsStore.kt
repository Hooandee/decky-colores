package com.hooandee.colores.led

import android.annotation.SuppressLint
import android.content.Context
import android.os.IBinder
import android.os.Parcel
import android.provider.Settings

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

private fun String.shellQuoted(): String = "'${replace("'", "'\"'\"'")}'"
