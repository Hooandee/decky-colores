package com.hooandee.colores.profiles

import android.content.Context
import android.content.SharedPreferences
import com.hooandee.colores.control.AppMode
import com.hooandee.colores.led.RgbColor
import org.json.JSONArray
import org.json.JSONObject

data class LightingProfile(
    val mode: AppMode = AppMode.COLOR,
    val effectId: String = "breathing",
    val speed: Int = 50,
    val gradientSpeed: Int = 30,
    val effectUsesGradient: Boolean = false,
    val solidColor: RgbColor = RgbColor(93, 81, 255),
    val staticColors: List<RgbColor> = listOf(RgbColor(93, 81, 255), RgbColor(93, 81, 255)),
    val gradientStops: List<RgbColor> = listOf(RgbColor(93, 81, 255), RgbColor(93, 81, 255)),
    val brightness: Int = 100,
    val batteryBreathe: Boolean = true,
    val temperatureBreathe: Boolean = true,
)

data class ProfilePatch(
    val mode: AppMode? = null,
    val effectId: String? = null,
    val speed: Int? = null,
    val gradientSpeed: Int? = null,
    val effectUsesGradient: Boolean? = null,
    val solidColor: RgbColor? = null,
    val staticColors: List<RgbColor>? = null,
    val gradientStops: List<RgbColor>? = null,
    val brightness: Int? = null,
    val batteryBreathe: Boolean? = null,
    val temperatureBreathe: Boolean? = null,
)

sealed interface ProfileScope {
    data object Global : ProfileScope

    data class App(
        val packageName: String,
    ) : ProfileScope
}

data class ProfileScopeState(
    val hasAppProfile: Boolean,
    val followsGlobal: Boolean,
    val activeProfile: String = "default",
)

private data class AppProfileEntry(
    val profile: LightingProfile,
    val followsGlobal: Boolean,
)

private data class ProfileEnvelope(
    val global: LightingProfile = LightingProfile(),
    val apps: Map<String, AppProfileEntry> = emptyMap(),
)

class LightingProfileStore(
    private val read: (String) -> String?,
    private val write: (String, String) -> Unit,
) {
    private constructor(preferences: SharedPreferences) : this(
        read = { key -> preferences.getString(key, null) },
        write = { key, value -> preferences.edit().putString(key, value).apply() },
    )

    constructor(context: Context) : this(context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE))

    fun global(deviceId: String): LightingProfile = load(deviceId).global

    fun migrateIfMissing(
        deviceId: String,
        legacy: LightingProfile,
    ): Boolean {
        if (read(key(deviceId)) != null) return false
        save(deviceId, ProfileEnvelope(global = legacy.patched(ProfilePatch())))
        return true
    }

    fun effective(
        deviceId: String,
        packageName: String?,
    ): LightingProfile {
        val envelope = load(deviceId)
        val app = packageName?.let(envelope.apps::get)
        return if (app == null || app.followsGlobal) envelope.global else app.profile
    }

    fun patch(
        deviceId: String,
        scope: ProfileScope,
        patch: ProfilePatch,
    ): LightingProfile {
        val envelope = load(deviceId)
        val current =
            when (scope) {
                ProfileScope.Global -> envelope.global
                is ProfileScope.App -> envelope.apps[scope.packageName]?.profile ?: envelope.global
            }
        val updated = current.patched(patch)
        val next =
            when (scope) {
                ProfileScope.Global -> envelope.copy(global = updated)
                is ProfileScope.App ->
                    envelope.copy(
                        apps = envelope.apps + (scope.packageName.requirePackage() to AppProfileEntry(updated, false)),
                    )
            }
        save(deviceId, next)
        return updated
    }

    fun setFollowGlobal(
        deviceId: String,
        packageName: String,
        follow: Boolean,
    ): ProfileScopeState {
        val key = packageName.requirePackage()
        val envelope = load(deviceId)
        val current = envelope.apps[key] ?: AppProfileEntry(envelope.global, true)
        save(deviceId, envelope.copy(apps = envelope.apps + (key to current.copy(followsGlobal = follow))))
        return scopeState(deviceId, key)
    }

    fun forget(
        deviceId: String,
        packageName: String,
    ) {
        val envelope = load(deviceId)
        save(deviceId, envelope.copy(apps = envelope.apps - packageName))
    }

    fun configuredPackages(deviceId: String): Set<String> = load(deviceId).apps.keys

    fun scopeState(
        deviceId: String,
        packageName: String,
    ): ProfileScopeState {
        val entry = load(deviceId).apps[packageName]
        return ProfileScopeState(
            hasAppProfile = entry != null,
            followsGlobal = entry?.followsGlobal ?: true,
        )
    }

    fun isAutomationEnabled(): Boolean = read(AUTOMATION_KEY)?.toBooleanStrictOrNull() ?: false

    fun setAutomationEnabled(enabled: Boolean) = write(AUTOMATION_KEY, enabled.toString())

    private fun load(deviceId: String): ProfileEnvelope =
        read(key(deviceId))?.let(::decode) ?: ProfileEnvelope()

    private fun save(
        deviceId: String,
        envelope: ProfileEnvelope,
    ) = write(key(deviceId), encode(envelope).toString())

    private fun decode(raw: String): ProfileEnvelope =
        runCatching {
            val root = JSONObject(raw)
            if (root.optInt("schemaVersion") != SCHEMA_VERSION) return@runCatching ProfileEnvelope()
            val global = root.optJSONObject("global").entryProfile(LightingProfile())
            val rawApps = root.optJSONObject("apps") ?: JSONObject()
            val apps =
                rawApps.keys().asSequence().mapNotNull { packageName ->
                    runCatching {
                        val entry = rawApps.getJSONObject(packageName)
                        packageName to
                            AppProfileEntry(
                                profile = entry.entryProfile(global),
                                followsGlobal = entry.optBoolean("followsGlobal", true),
                            )
                    }.getOrNull()
                }.toMap()
            ProfileEnvelope(global, apps)
        }.getOrDefault(ProfileEnvelope())

    private fun encode(envelope: ProfileEnvelope): JSONObject =
        JSONObject()
            .put("schemaVersion", SCHEMA_VERSION)
            .put("global", entry(envelope.global))
            .put(
                "apps",
                JSONObject().apply {
                    envelope.apps.forEach { (packageName, app) ->
                        put(packageName, entry(app.profile).put("followsGlobal", app.followsGlobal))
                    }
                },
            )

    private fun entry(profile: LightingProfile) =
        JSONObject()
            .put("profiles", JSONObject().put("default", profile.toJson()))
            .put("activeProfile", "default")

    private fun JSONObject?.entryProfile(fallback: LightingProfile): LightingProfile {
        val raw = this?.optJSONObject("profiles")?.optJSONObject("default") ?: return fallback
        return raw.toProfile(fallback)
    }

    private fun key(deviceId: String) = "profiles:$deviceId"

    private companion object {
        const val FILE_NAME = "lighting_profiles"
        const val SCHEMA_VERSION = 1
        const val AUTOMATION_KEY = "automation_enabled"
    }
}

private fun LightingProfile.patched(patch: ProfilePatch) =
    copy(
        mode = patch.mode ?: mode,
        effectId = patch.effectId?.takeIf(String::isNotBlank) ?: effectId,
        speed = (patch.speed ?: speed).coerceIn(0, 100),
        gradientSpeed = (patch.gradientSpeed ?: gradientSpeed).coerceIn(0, 100),
        effectUsesGradient = patch.effectUsesGradient ?: effectUsesGradient,
        solidColor = (patch.solidColor ?: solidColor).sanitized(),
        staticColors = (patch.staticColors ?: staticColors).ifEmpty { staticColors }.map(RgbColor::sanitized),
        gradientStops = (patch.gradientStops ?: gradientStops).ifEmpty { gradientStops }.map(RgbColor::sanitized),
        brightness = (patch.brightness ?: brightness).coerceIn(0, 100),
        batteryBreathe = patch.batteryBreathe ?: batteryBreathe,
        temperatureBreathe = patch.temperatureBreathe ?: temperatureBreathe,
    )

private fun LightingProfile.toJson() =
    JSONObject()
        .put("mode", mode.name)
        .put("effectId", effectId)
        .put("speed", speed.coerceIn(0, 100))
        .put("gradientSpeed", gradientSpeed.coerceIn(0, 100))
        .put("effectUsesGradient", effectUsesGradient)
        .put("solidColor", solidColor.sanitized().toJson())
        .put("staticColors", JSONArray().apply { staticColors.forEach { put(it.sanitized().toJson()) } })
        .put("gradientStops", JSONArray().apply { gradientStops.forEach { put(it.sanitized().toJson()) } })
        .put("brightness", brightness.coerceIn(0, 100))
        .put("batteryBreathe", batteryBreathe)
        .put("temperatureBreathe", temperatureBreathe)

private fun JSONObject.toProfile(fallback: LightingProfile): LightingProfile =
    LightingProfile(
        mode = runCatching { AppMode.valueOf(optString("mode", fallback.mode.name)) }.getOrDefault(fallback.mode),
        effectId = optString("effectId", fallback.effectId).ifBlank { fallback.effectId },
        speed = optInt("speed", fallback.speed).coerceIn(0, 100),
        gradientSpeed = optInt("gradientSpeed", fallback.gradientSpeed).coerceIn(0, 100),
        effectUsesGradient = optBoolean("effectUsesGradient", fallback.effectUsesGradient),
        solidColor = optJSONObject("solidColor")?.toColor(fallback.solidColor) ?: fallback.solidColor,
        staticColors =
            optJSONArray("staticColors")?.let { array ->
                (0 until array.length()).mapNotNull { index -> array.optJSONObject(index)?.toColor(fallback.solidColor) }
            }.orEmpty().ifEmpty { fallback.staticColors },
        gradientStops =
            optJSONArray("gradientStops")?.let { array ->
                (0 until array.length()).mapNotNull { index ->
                    array.optJSONObject(index)?.toColor(fallback.solidColor)
                }
            }.orEmpty().ifEmpty { fallback.gradientStops },
        brightness = optInt("brightness", fallback.brightness).coerceIn(0, 100),
        batteryBreathe = optBoolean("batteryBreathe", fallback.batteryBreathe),
        temperatureBreathe = optBoolean("temperatureBreathe", fallback.temperatureBreathe),
    )

private fun JSONObject.toColor(fallback: RgbColor) =
    RgbColor(
        optInt("r", fallback.red).coerceIn(0, 255),
        optInt("g", fallback.green).coerceIn(0, 255),
        optInt("b", fallback.blue).coerceIn(0, 255),
    )

private fun RgbColor.toJson() = JSONObject().put("r", red).put("g", green).put("b", blue)

private fun RgbColor.sanitized() = RgbColor(red.coerceIn(0, 255), green.coerceIn(0, 255), blue.coerceIn(0, 255))

private fun String.requirePackage(): String {
    require(isNotBlank()) { "package name is required" }
    return this
}
