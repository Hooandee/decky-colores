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

class ReportSender(
    private val post: (JSONObject) -> HttpResponse,
    private val save: (JSONObject) -> String?,
) {
    constructor(context: Context) : this(
        post = { payload -> postReport(REPORT_SERVICE_URL, payload) },
        save = { bundle -> saveReport(context, bundle) },
    )

    fun submit(bundle: JSONObject): ReportResult {
        val result =
            runCatching {
                val response = post(encodeReportPayload(bundle))
                parseReportResponse(response.status, response.body)
            }.getOrElse { ReportResult.Failure(it.message ?: "network") }
        if (result is ReportResult.Success) return result
        val failure = result as ReportResult.Failure
        return failure.copy(savedPath = save(bundle))
    }
}

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
): String? =
    runCatching {
        val directory = File(context.filesDir, "reports").apply { mkdirs() }
        val target = File(directory, "report-offline.json")
        val temporary = File(directory, "report-offline.json.tmp")
        temporary.writeText(bundle.toString(2))
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

private const val REPORT_SERVICE_URL = "https://bug-collector-khaki.vercel.app/api/report"
