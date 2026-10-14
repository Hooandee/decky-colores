package com.hooandee.colores.report

import android.util.Log

object ColoresLog {
    @Volatile
    var journal: Journal? = null

    fun i(
        tag: String,
        message: String,
    ) {
        runCatching { Log.i(tag, message) }
        journal?.log("I", tag, message)
    }

    fun w(
        tag: String,
        message: String,
        error: Throwable? = null,
    ) {
        runCatching { Log.w(tag, message, error) }
        journal?.log("W", tag, message, error)
    }

    fun e(
        tag: String,
        message: String,
        error: Throwable? = null,
    ) {
        runCatching { Log.e(tag, message, error) }
        journal?.log("E", tag, message, error)
    }

    fun state(
        key: String,
        value: Any?,
    ) {
        journal?.state(key, value)
    }

    fun action(
        name: String,
        fields: Map<String, Any?> = emptyMap(),
    ) {
        journal?.action(name, fields)
    }

    fun hardware(
        event: String,
        by: String,
        fields: Map<String, Any?> = emptyMap(),
    ) {
        journal?.hardware(event, by, fields)
    }
}

class WriteHealth(
    private val by: String,
) {
    @Volatile
    private var failures = 0

    fun succeeded(fields: Map<String, Any?> = emptyMap()) {
        if (failures > 0) ColoresLog.hardware("write_recovered", by, fields + ("failures" to failures))
        failures = 0
    }

    fun failed(
        reason: String,
        fields: Map<String, Any?> = emptyMap(),
    ) {
        if (failures++ == 0) ColoresLog.hardware("write_failed", by, fields + ("err" to reason.take(500)))
    }

    fun record(
        result: Result<*>,
        fields: Map<String, Any?> = emptyMap(),
    ) {
        val error = result.exceptionOrNull()
        if (error == null) succeeded(fields) else failed("${error.javaClass.simpleName}: ${error.message.orEmpty()}", fields)
    }
}
