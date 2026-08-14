package com.hooandee.colores.device.learning

import android.content.Context
import android.content.SharedPreferences
import com.hooandee.colores.device.AndroidDeviceIdentity
import com.hooandee.colores.device.DeviceCapabilities
import java.security.MessageDigest
import org.json.JSONArray
import org.json.JSONObject

class HardwareLearningStore(
    private val read: (String) -> String?,
    private val write: (String, String) -> Boolean,
    private val remove: (String) -> Boolean,
) {
    private constructor(preferences: SharedPreferences) : this(
        read = { key -> preferences.getString(key, null) },
        write = { key, value -> preferences.edit().putString(key, value).commit() },
        remove = { key -> preferences.edit().remove(key).commit() },
    )

    constructor(context: Context) : this(context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE))

    fun saveRollback(record: RollbackRecord): Boolean = write(ROLLBACK_KEY, record.toJson().toString())

    fun loadRollback(): RollbackRecord? = read(ROLLBACK_KEY)?.let { runCatching { JSONObject(it).toRollbackRecord() }.getOrNull() }

    fun hasRollback(): Boolean = read(ROLLBACK_KEY) != null

    fun clearRollback(): Boolean = remove(ROLLBACK_KEY)

    fun saveBinding(binding: LearnedDeviceBinding): Boolean = write(BINDING_KEY, binding.toJson().toString())

    fun loadBinding(): LearnedDeviceBinding? = read(BINDING_KEY)?.let { runCatching { JSONObject(it).toLearnedBinding() }.getOrNull() }

    fun clearBinding(): Boolean = remove(BINDING_KEY)

    fun saveAttempt(attempt: HardwareLearningAttempt): Boolean = write(ATTEMPT_KEY, attempt.toJson().toString())

    fun loadAttempt(): HardwareLearningAttempt? =
        read(ATTEMPT_KEY)?.let { runCatching { JSONObject(it).toHardwareLearningAttempt() }.getOrNull() }

    fun clearAttempt(): Boolean = remove(ATTEMPT_KEY)

    private companion object {
        const val FILE_NAME = "hardware_learning"
        const val ROLLBACK_KEY = "rollback"
        const val BINDING_KEY = "binding"
        const val ATTEMPT_KEY = "attempt"
    }
}

internal fun learningIdentityHash(identity: AndroidDeviceIdentity): String {
    val normalized =
        buildList {
            add(identity.manufacturer)
            add(identity.model)
            add(identity.device)
            identity.productProperties.toSortedMap().forEach { (key, value) -> add("$key=$value") }
        }.joinToString("\u0000") { it.trim().lowercase() }
    return MessageDigest.getInstance("SHA-256")
        .digest(normalized.toByteArray())
        .joinToString("") { "%02x".format(it) }
}

internal fun HardwareLearningAttempt.resultsFor(identity: AndroidDeviceIdentity): List<HardwareLearningResult> =
    results.takeIf { identityHash == learningIdentityHash(identity) }.orEmpty()

private fun RollbackRecord.toJson(): JSONObject =
    JSONObject()
        .put("schema", 1)
        .put("session_id", sessionId)
        .put("cartridge_id", cartridgeId)
        .put("cartridge_version", cartridgeVersion)
        .put("descriptor", descriptorJson)
        .put(
            "snapshot",
            JSONObject()
                .put("values", JSONObject(snapshot.values)),
        )

private fun JSONObject.toRollbackRecord(): RollbackRecord {
    require(getInt("schema") == 1)
    val snapshotJson = getJSONObject("snapshot")
    return RollbackRecord(
        sessionId = getString("session_id").also { require(it.isNotBlank()) },
        cartridgeId = getString("cartridge_id").also { require(it.isNotBlank()) },
        cartridgeVersion = getInt("cartridge_version").also { require(it > 0) },
        descriptorJson = getString("descriptor"),
        snapshot =
            ProbeSnapshot(
                values = snapshotJson.getJSONObject("values").stringMap(),
            ),
    )
}

private fun LearnedDeviceBinding.toJson(): JSONObject =
    JSONObject()
        .put("schema", 1)
        .put("identity_hash", identityHash)
        .put("cartridge_id", cartridgeId)
        .put("cartridge_version", cartridgeVersion)
        .put("descriptor", descriptorJson)
        .put(
            "capabilities",
            JSONObject()
                .put("color", capabilities.color)
                .put("brightness", capabilities.brightness)
                .put("per_zone", capabilities.perZone)
                .put("zones", capabilities.zones)
                .put("power", capabilities.power),
        ).put("app_version", appVersion)
        .put("learned_at", learnedAtEpochMs)

private fun JSONObject.toLearnedBinding(): LearnedDeviceBinding {
    require(getInt("schema") == 1)
    val capabilitiesJson = getJSONObject("capabilities")
    val zones = capabilitiesJson.getInt("zones").also { require(it > 0) }
    val identityHash = getString("identity_hash").also { require(it.matches(Regex("[0-9a-f]{64}"))) }
    return LearnedDeviceBinding(
        identityHash = identityHash,
        cartridgeId = getString("cartridge_id").also { require(it.isNotBlank()) },
        cartridgeVersion = getInt("cartridge_version").also { require(it > 0) },
        descriptorJson = getString("descriptor"),
        capabilities =
            DeviceCapabilities(
                color = capabilitiesJson.getBoolean("color"),
                brightness = capabilitiesJson.getBoolean("brightness"),
                perZone = capabilitiesJson.getBoolean("per_zone"),
                zones = zones,
                power = capabilitiesJson.optBoolean("power", false),
            ),
        appVersion = getString("app_version"),
        learnedAtEpochMs = getLong("learned_at"),
    )
}

private fun HardwareLearningAttempt.toJson(): JSONObject =
    JSONObject()
        .put("schema", 1)
        .put("identity_hash", identityHash)
        .put("results", JSONArray(results.map(HardwareLearningResult::toJson)))
        .put("app_version", appVersion)
        .put("completed_at", completedAtEpochMs)

private fun JSONObject.toHardwareLearningAttempt(): HardwareLearningAttempt {
    require(getInt("schema") == 1)
    val identityHash = getString("identity_hash").also { require(it.matches(Regex("[0-9a-f]{64}"))) }
    val resultValues = getJSONArray("results")
    val results = (0 until resultValues.length()).map { resultValues.getJSONObject(it).toHardwareLearningResult() }
    require(results.isNotEmpty())
    return HardwareLearningAttempt(
        identityHash = identityHash,
        results = results,
        appVersion = getString("app_version"),
        completedAtEpochMs = getLong("completed_at"),
    )
}

private fun HardwareLearningResult.toJson(): JSONObject =
    JSONObject()
        .put("status", status.name)
        .put(
            "candidate",
            JSONObject()
                .put("cartridge_id", candidate.cartridgeId)
                .put("cartridge_version", candidate.cartridgeVersion)
                .put("surface", candidate.surface.name)
                .put("descriptor", encodeLearningDescriptor(candidate.descriptor))
                .put("signal_keys", JSONArray(candidate.signalKeys.toList().sorted())),
        ).put(
            "evidence",
            JSONArray(
                evidence.map {
                    JSONObject()
                        .put("step", it.step.name)
                        .put("zone", it.zone ?: JSONObject.NULL)
                        .put("level", it.level.name)
                        .put("observation", it.observation?.name ?: JSONObject.NULL)
                        .put("location", it.location?.name ?: JSONObject.NULL)
                },
            ),
        ).put("capabilities", capabilities.toJson())
        .put("rollback_status", rollbackStatus.name)

private fun JSONObject.toHardwareLearningResult(): HardwareLearningResult {
    val candidateJson = getJSONObject("candidate")
    val descriptor = requireNotNull(decodeLearningDescriptor(candidateJson.getString("descriptor")))
    val evidenceValues = getJSONArray("evidence")
    return HardwareLearningResult(
        status = HardwareLearningStatus.valueOf(getString("status")),
        candidate =
            ProbeCandidate(
                cartridgeId = candidateJson.getString("cartridge_id").also { require(it.isNotBlank()) },
                cartridgeVersion = candidateJson.getInt("cartridge_version").also { require(it > 0) },
                surface = ProbeSurface.valueOf(candidateJson.getString("surface")),
                descriptor = descriptor,
                signalKeys = candidateJson.getJSONArray("signal_keys").strings().toSet(),
            ),
        evidence =
            (0 until evidenceValues.length()).map { index ->
                val value = evidenceValues.getJSONObject(index)
                ProbeEvidence(
                    step = ProbeStep.valueOf(value.getString("step")),
                    zone = if (value.isNull("zone")) null else value.getInt("zone"),
                    level = EvidenceLevel.valueOf(value.getString("level")),
                    observation =
                        if (value.isNull("observation")) {
                            null
                        } else {
                            UserObservation.valueOf(value.getString("observation"))
                        },
                    location =
                        if (value.isNull("location")) {
                            null
                        } else {
                            ZoneLocation.valueOf(value.getString("location"))
                        },
                )
            },
        capabilities = getJSONObject("capabilities").toDeviceCapabilities(),
        rollbackStatus = RollbackStatus.valueOf(getString("rollback_status")),
    )
}

private fun DeviceCapabilities.toJson(): JSONObject =
    JSONObject()
        .put("color", color)
        .put("brightness", brightness)
        .put("per_zone", perZone)
        .put("zones", zones)
        .put("power", power)

private fun JSONObject.toDeviceCapabilities(): DeviceCapabilities =
    DeviceCapabilities(
        color = getBoolean("color"),
        brightness = getBoolean("brightness"),
        perZone = getBoolean("per_zone"),
        zones = getInt("zones").also { require(it > 0) },
        power = getBoolean("power"),
    )

private fun JSONArray.strings(): List<String> = (0 until length()).map(::getString)

private fun JSONObject.stringMap(): Map<String, String> = keys().asSequence().associateWith(::getString)
