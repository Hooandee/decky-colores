package com.hooandee.colores.device

import org.json.JSONObject

enum class DevicePresentationSource {
    EXACT_PROFILE,
    KNOWN_IDENTITY,
    BUILD_MODEL,
    UNKNOWN,
}

data class DevicePresentation(
    val id: String?,
    val friendlyName: String,
    val source: DevicePresentationSource,
) {
    val isKnown: Boolean
        get() = source == DevicePresentationSource.EXACT_PROFILE || source == DevicePresentationSource.KNOWN_IDENTITY

    companion object {
        val UNKNOWN = DevicePresentation(null, "", DevicePresentationSource.UNKNOWN)
    }
}

private data class AndroidIdentityDefinition(
    val id: String,
    val friendlyName: String,
    val manufacturers: List<String>,
    val models: List<String>,
)

class AndroidDeviceIdentityCatalog private constructor(
    private val devices: List<AndroidIdentityDefinition>,
) {
    fun resolve(
        identity: AndroidDeviceIdentity,
        exact: DetectedAndroidDevice?,
    ): DevicePresentation {
        if (exact != null) {
            return DevicePresentation(
                id = exact.id,
                friendlyName = exact.friendlyName,
                source = DevicePresentationSource.EXACT_PROFILE,
            )
        }
        devices.firstOrNull { it.matches(identity) }?.let {
            return DevicePresentation(
                id = it.id,
                friendlyName = it.friendlyName,
                source = DevicePresentationSource.KNOWN_IDENTITY,
            )
        }
        val model = identity.model.toDisplayName()
        return if (model.isBlank()) {
            DevicePresentation.UNKNOWN
        } else {
            DevicePresentation(null, model, DevicePresentationSource.BUILD_MODEL)
        }
    }

    companion object {
        fun parse(json: String): AndroidDeviceIdentityCatalog =
            runCatching {
                val root = JSONObject(json)
                require(root.getInt("schemaVersion") == 1)
                val entries = root.getJSONArray("devices")
                AndroidDeviceIdentityCatalog(
                    (0 until entries.length()).mapNotNull { index ->
                        runCatching {
                            val entry = entries.getJSONObject(index)
                            AndroidIdentityDefinition(
                                id = entry.getString("id").trim().also { require(it.isNotBlank()) },
                                friendlyName = entry.getString("friendlyName").trim().also { require(it.isNotBlank()) },
                                manufacturers = entry.stringList("manufacturers").also { require(it.isNotEmpty()) },
                                models = entry.stringList("models").also { require(it.isNotEmpty()) },
                            )
                        }.getOrNull()
                    },
                )
            }.getOrElse { AndroidDeviceIdentityCatalog(emptyList()) }
    }
}

private fun AndroidIdentityDefinition.matches(identity: AndroidDeviceIdentity): Boolean =
    manufacturers.any { it.identityKey() == identity.manufacturer.identityKey() } &&
        models.any { it.identityKey() == identity.model.identityKey() }

private fun String.identityKey(): String = lowercase().filter(Char::isLetterOrDigit)

private fun String.toDisplayName(): String =
    trim()
        .replace(Regex("[_-]+"), " ")
        .replace(Regex("\\s+"), " ")

private fun JSONObject.stringList(key: String): List<String> {
    val values = getJSONArray(key)
    return (0 until values.length()).map(values::getString).map(String::trim).filter(String::isNotBlank)
}
