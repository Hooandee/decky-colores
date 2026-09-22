package com.hooandee.colores.report

import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.GZIPOutputStream
import com.hooandee.colores.device.diagnostics.HardwareInventory
import com.hooandee.colores.device.learning.ArchivedRollbackSummary
import com.hooandee.colores.device.learning.DetectionOutcome
import com.hooandee.colores.device.learning.HardwareFact
import com.hooandee.colores.device.learning.HardwareLearningResult
import com.hooandee.colores.device.learning.HardwareLearningStatus
import com.hooandee.colores.device.learning.ProbeCandidate
import com.hooandee.colores.device.learning.RollbackStatus
import com.hooandee.colores.led.LedDescriptor
import com.hooandee.colores.led.SettingsProviderDescriptor
import com.hooandee.colores.led.SingleAdcJoypadDescriptor
import com.hooandee.colores.led.SysfsRgbDescriptor
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

data class ReportDiagnostics(
    val detectionOutcome: String? = null,
    val facts: List<HardwareFact> = emptyList(),
    val candidates: List<ProbeCandidate> = emptyList(),
    val inventory: HardwareInventory? = null,
    val archivedRollback: ArchivedRollbackSummary? = null,
)

fun reportDiagnostics(
    outcome: DetectionOutcome?,
    inventory: HardwareInventory?,
    archivedRollback: ArchivedRollbackSummary? = null,
): ReportDiagnostics =
    ReportDiagnostics(
        detectionOutcome =
            when (outcome) {
                is DetectionOutcome.Resolved -> "resolved"
                is DetectionOutcome.UnavailableKnownDevice -> "unavailable_known_device"
                is DetectionOutcome.Candidates -> "candidates"
                is DetectionOutcome.Unsupported -> "unsupported"
                null -> null
            },
        facts = outcome?.facts.orEmpty(),
        candidates =
            when (outcome) {
                is DetectionOutcome.Resolved -> outcome.candidates
                is DetectionOutcome.UnavailableKnownDevice -> outcome.candidates
                is DetectionOutcome.Candidates -> outcome.candidates
                is DetectionOutcome.Unsupported, null -> emptyList()
            },
        inventory = inventory,
        archivedRollback = archivedRollback,
    )

data class ReportSubmissionState(
    val sending: Boolean = false,
    val result: ReportResult? = null,
)

fun buildReportBundle(
    snapshot: AndroidReportSnapshot,
    categories: List<String>,
    text: String,
    diagnostics: ReportDiagnostics = ReportDiagnostics(),
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
        .put("kernel", diagnostics.inventory?.kernel ?: JSONObject())
        .put("sysfs", diagnostics.inventory?.sysfs ?: JSONObject())
        .put("hardware", diagnostics.inventory?.hardware ?: JSONObject())
        .put("discovery", discoveryJson(diagnostics))
        .apply { diagnostics.archivedRollback?.let { put("rollback_archive", archivedRollbackJson(it)) } }

private fun archivedRollbackJson(archive: ArchivedRollbackSummary): JSONObject =
    JSONObject()
        .put("reason", archive.reason.name.lowercase())
        .put("attempts", archive.attempts)
        .put("cartridge_id", archive.cartridgeId)
        .put("cartridge_version", archive.cartridgeVersion)
        .put("archived_at", archive.archivedAtEpochMs)
        .put("surface", archive.surface?.name?.lowercase())

private fun discoveryJson(diagnostics: ReportDiagnostics): JSONObject =
    JSONObject()
        .put("outcome", diagnostics.detectionOutcome)
        .put("facts", factsJson(diagnostics.facts))
        .put(
            "candidates",
            JSONArray(
                diagnostics.candidates.take(MAX_REPORTED_CANDIDATES).map { candidate ->
                    JSONObject()
                        .put("cartridge_id", candidate.cartridgeId.take(120))
                        .put("cartridge_version", candidate.cartridgeVersion)
                        .put("surface", candidate.surface.name.lowercase())
                        .put("driver", candidate.descriptor.reportDriver())
                        .put("zones", candidate.descriptor.reportZones())
                        .put("signal_keys", JSONArray(candidate.signalKeys.sorted().map { it.take(80) }))
                },
            ),
        )

private fun factsJson(facts: List<HardwareFact>): JSONArray =
    JSONArray(
        facts.take(MAX_REPORTED_FACTS).map { fact ->
            JSONObject()
                .put("key", fact.key.take(120))
                .put("value", redactReportText(fact.value).take(200))
                .put("evidence", fact.evidence.name.lowercase())
                .put("cartridge_id", fact.cartridgeId.take(120))
        },
    )

private fun LedDescriptor.reportDriver(): String =
    when (this) {
        is SettingsProviderDescriptor -> "${driver}:${colorFormat}"
        is SysfsRgbDescriptor -> "sysfs:${kind.name.lowercase()}"
        is SingleAdcJoypadDescriptor -> "singleadc"
    }

private fun LedDescriptor.reportZones(): Int =
    when (this) {
        is SettingsProviderDescriptor -> zones
        is SysfsRgbDescriptor -> zones
        is SingleAdcJoypadDescriptor -> 1
    }

private const val MAX_REPORTED_CANDIDATES = 24
private const val MAX_REPORTED_FACTS = 64

fun buildHardwareLearningBundle(
    snapshot: AndroidReportSnapshot,
    results: List<HardwareLearningResult>,
    text: String,
    forcedRestoreFailure: Boolean = false,
    forcedSafetyFailure: Boolean = false,
    facts: List<HardwareFact> = emptyList(),
    diagnostics: ReportDiagnostics = ReportDiagnostics(facts = facts),
): JSONObject {
    val restoreFailed = forcedRestoreFailure || results.any { it.rollbackStatus == RollbackStatus.RESTORE_FAILED }
    val safetyFailed = restoreFailed || forcedSafetyFailure
    val adapted = results.any { it.status == HardwareLearningStatus.ADAPTED }
    val severity = if (safetyFailed) "critical" else if (!adapted) "high" else "normal"
    val labels =
        buildList {
            add("hardware-learning")
            add(if (adapted) "compatibility-candidate" else "compatibility-blocked")
            if (restoreFailed) add("restore-failed")
            if (forcedSafetyFailure) add("safety-failed")
        }
    return buildReportBundle(snapshot, listOf("learning"), text, diagnostics)
        .put("report_kind", "hardware_learning")
        .put(
            "triage",
            JSONObject()
                .put("severity", severity)
                .put("requested_labels", JSONArray(labels)),
        ).put(
            "learning",
            JSONObject()
                .put("discovery_facts", factsJson(facts))
                .put(
                    "attempts",
                    JSONArray(
                        results.map { result ->
                            JSONObject()
                                .put("cartridge_id", result.candidate.cartridgeId)
                                .put("cartridge_version", result.candidate.cartridgeVersion)
                                .put("surface", result.candidate.surface.name.lowercase())
                                .put("status", result.status.name.lowercase())
                                .put("rollback", result.rollbackStatus.name.lowercase())
                                .put("signal_keys", JSONArray(result.candidate.signalKeys.sorted()))
                                .put(
                                    "evidence",
                                    JSONArray(
                                        result.evidence.map { evidence ->
                                            JSONObject()
                                                .put("step", evidence.step.name.lowercase())
                                                .put("zone", evidence.zone)
                                                .put("level", evidence.level.name.lowercase())
                                                .put("observation", evidence.observation?.name?.lowercase())
                                                .put("location", evidence.location?.name?.lowercase())
                                        },
                                    ),
                                ).put(
                                    "confirmed_capabilities",
                                    JSONArray(
                                        buildList {
                                            if (result.capabilities.color) add("color")
                                            if (result.capabilities.brightness) add("brightness")
                                            if (result.capabilities.perZone) add("per_zone")
                                            if (result.capabilities.power) add("power")
                                        },
                                    ),
                                )
                        },
                    ),
                ),
        )
}

fun buildReportBundleForSubmission(
    snapshot: AndroidReportSnapshot,
    categories: List<String>,
    text: String,
    learningResults: List<HardwareLearningResult>,
    restoreFailure: Boolean = false,
    criticalSafetyFailure: Boolean = false,
    learningFacts: List<HardwareFact> = emptyList(),
    diagnostics: ReportDiagnostics = ReportDiagnostics(facts = learningFacts),
): JSONObject =
    if (
        "learning" in categories &&
        (
            learningResults.isNotEmpty() ||
                learningFacts.isNotEmpty() ||
                restoreFailure ||
                criticalSafetyFailure ||
                diagnostics.archivedRollback != null
        )
    ) {
        buildHardwareLearningBundle(
            snapshot,
            learningResults,
            text,
            forcedRestoreFailure = restoreFailure,
            forcedSafetyFailure = criticalSafetyFailure,
            facts = learningFacts,
            diagnostics = diagnostics,
        )
    } else {
        buildReportBundle(snapshot, categories, text, diagnostics)
    }

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

private val emailPattern = Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(?:\\.[A-Za-z0-9-]+)*\\.[A-Za-z]{2,}")
private const val PHONE_BEFORE = "(?<![\\w/:.#-])"
private const val PHONE_AFTER = "(?![\\w/:#-]|\\.\\d)"
private val phonePatterns =
    listOf(
        Regex("$PHONE_BEFORE\\+\\d{1,3}(?:[\\s.-]?\\(?\\d{1,4}\\)?){2,5}$PHONE_AFTER"),
        Regex("$PHONE_BEFORE[6-9]\\d{2}(?:[\\s.-]?\\d{3}){2}$PHONE_AFTER"),
        Regex("$PHONE_BEFORE[6-9]\\d{2}(?:[\\s.-]\\d{2}){3}$PHONE_AFTER"),
        Regex("$PHONE_BEFORE\\(?\\d{3}\\)?[\\s.-]\\d{3}[\\s.-]\\d{4}$PHONE_AFTER"),
    )
private val macPattern = Regex("\\b(?:[0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}\\b")
private val uuidPattern = Regex("\\b[0-9A-Fa-f]{8}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{12}\\b")
private val serialPattern = Regex("(?i)((?:board|product|chassis|system|baseboard)?[ _-]?serial(?:\\s*number)?)(\\s*[:=]\\s*)(\\S+)")

fun redactReportText(text: String): String {
    val redacted =
        text
            .replace(emailPattern, "[email]")
            .replace(macPattern, "[mac]")
            .replace(uuidPattern, "[uuid]")
            .replace(serialPattern) { "${it.groupValues[1]}${it.groupValues[2]}[serial]" }
    return phonePatterns.fold(redacted) { current, pattern ->
        current.replace(pattern) { if (it.value.count(Char::isDigit) >= 9) "[phone]" else it.value }
    }
}
