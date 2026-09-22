package com.hooandee.colores.report

import android.content.Context
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import org.json.JSONObject

data class HttpResponse(
    val status: Int,
    val body: String,
)

data class PendingReport(
    val bundle: JSONObject,
    val savedAtEpochMs: Long,
)

class ReportSender(
    private val post: (JSONObject) -> HttpResponse,
    private val save: (JSONObject) -> String?,
    private val clear: () -> Unit,
    private val load: () -> PendingReport? = { null },
    private val nowEpochMs: () -> Long = System::currentTimeMillis,
) {
    constructor(context: Context) : this(
        post = { payload -> postReport(REPORT_SERVICE_URL, payload) },
        save = { bundle -> saveReport(context, bundle, System.currentTimeMillis()) },
        clear = { clearSavedReport(context) },
        load = { loadSavedReport(context) },
    )

    private val lock = Any()

    fun submit(bundle: JSONObject): ReportResult =
        synchronized(lock) {
            val result = send(bundle)
            if (result is ReportResult.Success) {
                resendPendingLocked(except = bundle)
                return result
            }
            val failure = result as ReportResult.Failure
            failure.copy(savedPath = save(bundle))
        }

    fun resendPending(): ReportResult? = synchronized(lock) { resendPendingLocked(except = null) }

    private fun resendPendingLocked(except: JSONObject?): ReportResult? {
        val pending = runCatching(load).getOrNull() ?: return null
        val age = nowEpochMs() - pending.savedAtEpochMs
        if (age !in 0..PENDING_MAX_AGE_MS || (except != null && pending.bundle.toString() == except.toString())) {
            clear()
            return null
        }
        val result = send(pending.bundle)
        if (result is ReportResult.Success) clear()
        return result
    }

    private fun send(bundle: JSONObject): ReportResult =
        runCatching {
            val response = post(encodeReportPayload(bundle))
            parseReportResponse(response.status, response.body)
        }.getOrElse { ReportResult.Failure(it.message ?: "network") }
}

internal const val PENDING_MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000

internal fun decodePendingReport(
    raw: String,
    fallbackSavedAtEpochMs: Long,
): PendingReport? =
    runCatching {
        val json = JSONObject(raw)
        val bundle = json.optJSONObject("bundle")
        if (bundle != null && json.has("saved_at")) {
            PendingReport(bundle, json.getLong("saved_at"))
        } else {
            PendingReport(json, fallbackSavedAtEpochMs)
        }
    }.getOrNull()

internal fun encodePendingReport(
    bundle: JSONObject,
    savedAtEpochMs: Long,
): String = JSONObject().put("saved_at", savedAtEpochMs).put("bundle", bundle).toString()

private fun postReport(
    serviceUrl: String,
    payload: JSONObject,
): HttpResponse {
    val connection = URL(serviceUrl).openConnection() as HttpURLConnection
    return try {
        connection.requestMethod = "POST"
        connection.connectTimeout = 20_000
        connection.readTimeout = 20_000
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/json")
        connection.setRequestProperty("User-Agent", "colores-android-reporter")
        connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
        val status = connection.responseCode
        val stream = if (status in 200..299) connection.inputStream else connection.errorStream
        HttpResponse(status, stream?.bufferedReader()?.use { it.readText() }.orEmpty())
    } finally {
        connection.disconnect()
    }
}

private fun saveReport(
    context: Context,
    bundle: JSONObject,
    savedAtEpochMs: Long,
): String? =
    runCatching {
        val target = savedReportFile(context)
        val directory = target.parentFile.apply { mkdirs() }
        val temporary = File(directory, "${target.name}.tmp")
        temporary.writeText(encodePendingReport(bundle, savedAtEpochMs))
        runCatching {
            Files.move(
                temporary.toPath(),
                target.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        }.getOrElse {
            Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
        target.absolutePath
    }.getOrNull()

private fun savedReportFile(context: Context): File = File(File(context.filesDir, "reports"), "report-offline.json")

private fun clearSavedReport(context: Context) {
    savedReportFile(context).delete()
}

private fun loadSavedReport(context: Context): PendingReport? {
    val file = savedReportFile(context)
    if (!file.isFile) return null
    return decodePendingReport(file.readText(), file.lastModified())
}

private const val REPORT_SERVICE_URL = "https://bug-collector-khaki.vercel.app/api/report"
