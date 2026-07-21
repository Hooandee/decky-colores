package com.hooandee.colores.led

internal class FakeSysfsAccess(
    private val writable: Set<String>,
    val values: MutableMap<String, String> = mutableMapOf(),
) : SysfsAccess {
    override fun read(path: String): String? = values[path]

    override fun exists(path: String): Boolean = path in writable || path in values

    override fun canWrite(path: String): Boolean = path in writable

    override fun write(
        path: String,
        value: String,
    ): Boolean {
        if (path !in writable) return false
        values[path] = value.trim()
        return true
    }
}
