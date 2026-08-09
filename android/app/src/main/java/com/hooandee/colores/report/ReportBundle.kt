package com.hooandee.colores.report

import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.GZIPOutputStream
import org.json.JSONArray
import org.json.JSONObject

data class AndroidReportSnapshot(
    val appVersion: String,
    val manufacturer: String,
    val model: String,
    val androidRelease: String,
    val sdk: Int,
    val deviceId: String?,
    val deviceName: String?,
    val driver: String?,
    val transport: String?,
    val color: Boolean,
    val brightness: Boolean,
    val perZone: Boolean,
    val zones: Int,
    val controlStatus: String,
    val mode: String,
    val brightnessValue: Int,
    val power: Boolean,
    val configuredProfiles: Int,
    val automationStatus: String,
    val ambientStatus: String? = null,
    val ambientCaptureFps: Int? = null,
    val ambientSamplingMode: String? = null,
)

sealed interface ReportResult {
    data class Success(
        val code: String,
        val issueUrl: String?,
    ) : ReportResult

    data class Failure(
        val error: String,
        val savedPath: String? = null,
    ) : ReportResult
}

data class ReportSubmissionState(
    val sending: Boolean = false,
    val result: ReportResult? = null,
)

fun buildReportBundle(
    snapshot: AndroidReportSnapshot,
    categories: List<String>,
    text: String,
): JSONObject =
    JSONObject()
        .put("schema", 1)
        .put("app", "colores")
        .put("categories", JSONArray(categories.filter(REPORT_CATEGORIES::contains)))
        .put("text", redactReportText(text).take(4000))
        .put(
            "environment",
            JSONObject()
                .put("app_version", snapshot.appVersion)
                .put("plugin_version", snapshot.appVersion)
                .put("platform", "android")
                .put("manufacturer", redactReportText(snapshot.manufacturer))
                .put("model", redactReportText(snapshot.model))
                .put("product_name", redactReportText(snapshot.model))
                .put("android", snapshot.androidRelease)
                .put("os", "Android ${snapshot.androidRelease}")
                .put("sdk", snapshot.sdk)
                .put("device_key", snapshot.deviceId?.let(::redactReportText))
                .put("device_name", snapshot.deviceName?.let(::redactReportText)),
        ).put(
            "capabilities",
            JSONObject()
                .put("driver", snapshot.driver)
                .put("route", snapshot.transport)
                .put("color", snapshot.color)
                .put("brightness", snapshot.brightness)
                .put("per_zone", snapshot.perZone)
                .put("zones", snapshot.zones),
        ).put(
            "state",
            JSONObject()
                .put("control_status", snapshot.controlStatus)
                .put("mode", snapshot.mode)
                .put("brightness", snapshot.brightnessValue)
                .put("power", snapshot.power)
                .put("automation_status", snapshot.automationStatus)
                .put(
                    "ambient",
                    JSONObject()
                        .put("status", snapshot.ambientStatus)
                        .put("capture_fps", snapshot.ambientCaptureFps)
                        .put("sampling_mode", snapshot.ambientSamplingMode),
                ),
        ).put("stores", JSONObject().put("profiles_configured", snapshot.configuredProfiles))
        .put("logs", JSONArray())
        .put("kernel", JSONObject())
        .put("sysfs", JSONObject())

fun encodeReportPayload(bundle: JSONObject): JSONObject {
    val compressed =
        ByteArrayOutputStream().use { output ->
            GZIPOutputStream(output).use { it.write(bundle.toString().toByteArray(Charsets.UTF_8)) }
            output.toByteArray()
        }
    return JSONObject()
        .put("app", bundle.optString("app"))
        .put("schema", bundle.optInt("schema"))
        .put("enc", "gzip")
        .put("payload", Base64.getEncoder().encodeToString(compressed))
}

fun parseReportResponse(
    status: Int,
    body: String,
): ReportResult {
    val response = runCatching { JSONObject(body) }.getOrElse { JSONObject() }
    val code = response.optString("code").takeIf(String::isNotBlank)
    if (status in 200..299 && response.optBoolean("ok") && code != null) {
        val issueUrl =
            response.optString("issueUrl").ifBlank { response.optString("issue_url") }.takeIf(String::isNotBlank)
        return ReportResult.Success(code, issueUrl)
    }
    return ReportResult.Failure(response.optString("error").ifBlank { "HTTP $status" })
}

private val macPattern = Regex("\\b(?:[0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}\\b")
private val uuidPattern = Regex("\\b[0-9A-Fa-f]{8}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{12}\\b")
private val serialPattern = Regex("(?i)((?:board|product|chassis|system|baseboard)?[ _-]?serial(?:\\s*number)?)(\\s*[:=]\\s*)(\\S+)")

fun redactReportText(text: String): String =
    text
        .replace(macPattern, "[mac]")
        .replace(uuidPattern, "[uuid]")
        .replace(serialPattern) { "${it.groupValues[1]}${it.groupValues[2]}[serial]" }
