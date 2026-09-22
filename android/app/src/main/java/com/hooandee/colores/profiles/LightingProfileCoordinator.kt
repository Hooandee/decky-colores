package com.hooandee.colores.profiles

import com.hooandee.colores.apps.ForegroundAppObserver
import com.hooandee.colores.apps.ForegroundAppState
import com.hooandee.colores.apps.UsageAccess
import com.hooandee.colores.control.AppMode
import com.hooandee.colores.control.LightingController
import com.hooandee.colores.control.ProfileApplication
import com.hooandee.colores.control.ServiceGate
import com.hooandee.colores.control.ServiceOwner
import com.hooandee.colores.gradient.GradientInterpolator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class ProfileAutomationStatus {
    DISABLED,
    PERMISSION_REQUIRED,
    ACTIVE,
}

data class ProfileRuntimeState(
    val automationEnabled: Boolean = false,
    val automationStatus: ProfileAutomationStatus = ProfileAutomationStatus.DISABLED,
)

sealed interface ProfileTarget {
    data object Global : ProfileTarget

    data class ForegroundApp(
        val packageName: String,
    ) : ProfileTarget

    data class Preview(
        val scope: ProfileScope,
    ) : ProfileTarget
}

fun resolveProfileTarget(
    preview: ProfileScope?,
    foregroundPackage: String?,
    foregroundOverridesPreview: Boolean = false,
): ProfileTarget =
    when {
        foregroundOverridesPreview && foregroundPackage != null -> ProfileTarget.ForegroundApp(foregroundPackage)
        preview != null -> ProfileTarget.Preview(preview)
        foregroundPackage != null -> ProfileTarget.ForegroundApp(foregroundPackage)
        else -> ProfileTarget.Global
    }

class LightingProfileCoordinator(
    private val scope: CoroutineScope,
    private val store: LightingProfileStore,
    private val usageAccess: UsageAccess,
    private val observer: ForegroundAppObserver,
    private val controller: LightingController,
    private val serviceGate: ServiceGate,
) {
    private val mutableState = MutableStateFlow(ProfileRuntimeState())
    val state: StateFlow<ProfileRuntimeState> = mutableState.asStateFlow()

    private val lock = Any()
    private var deviceId: String? = null
    private var zones = 1
    private var gradientSupported = true
    private var foregroundPackage: String? = null
    private var foregroundOverridesPreview = false
    private var previewScope: ProfileScope? = null
    private var observerJob: Job? = null

    @Volatile
    private var globalModeCache: AppMode? = null

    init {
        startObserver()
    }

    fun bindDevice(
        deviceId: String,
        zones: Int,
        gradientSupported: Boolean = true,
    ) = synchronized(lock) {
        this.deviceId = deviceId
        this.zones = zones.coerceAtLeast(1)
        this.gradientSupported = gradientSupported
        applyResolved()
    }

    fun refreshAccess() =
        synchronized(lock) {
            updateAutomationState()
            applyResolved()
        }

    fun setAutomationEnabled(enabled: Boolean) =
        synchronized(lock) {
            store.setAutomationEnabled(enabled)
            if (!enabled) {
                foregroundPackage = null
                foregroundOverridesPreview = false
            }
            updateAutomationState()
            applyResolved()
        }

    fun beginPreview(scope: ProfileScope) =
        synchronized(lock) {
            previewScope = scope
            applyResolved()
        }

    fun endPreview() =
        synchronized(lock) {
            previewScope = null
            applyResolved()
        }

    fun edit(
        scope: ProfileScope,
        patch: ProfilePatch,
    ): LightingProfile? =
        synchronized(lock) {
            val id = deviceId ?: return null
            val profile = store.patch(id, scope, patch)
            if (scope == ProfileScope.Global) globalModeCache = profile.mode
            if (previewScope == scope || previewScope == null && targetMatches(scope)) applyResolved()
            profile
        }

    fun setFollowGlobal(
        packageName: String,
        follow: Boolean,
    ): ProfileScopeState? =
        synchronized(lock) {
            val id = deviceId ?: return null
            val state = store.setFollowGlobal(id, packageName, follow)
            applyResolved()
            state
        }

    fun forget(packageName: String) =
        synchronized(lock) {
            val id = deviceId ?: return
            store.forget(id, packageName)
            applyResolved()
        }

    fun selectedProfile(scope: ProfileScope): LightingProfile? =
        synchronized(lock) {
            val id = deviceId ?: return null
            profile(id, scope)
        }

    fun globalMode(): AppMode? = globalModeCache

    private fun startObserver() {
        observerJob?.cancel()
        observerJob =
            scope.launch {
                observer.observe(
                    store::isAutomationEnabled,
                    ::configuredPackages,
                    ::authoritativeFocusEnabled,
                ).collect { foreground ->
                    synchronized(lock) {
                        when (foreground) {
                            ForegroundAppState.Disabled,
                            ForegroundAppState.PermissionRequired,
                            -> {
                                foregroundPackage = null
                                foregroundOverridesPreview = false
                            }
                            is ForegroundAppState.Active -> {
                                foregroundPackage = foreground.packageName
                                foregroundOverridesPreview = foreground.authoritativeExternal
                            }
                        }
                        updateAutomationState()
                        applyResolved()
                    }
                }
            }
    }

    private fun configuredPackages(): Set<String> =
        synchronized(lock) { deviceId }?.let(store::configuredPackages).orEmpty()

    private fun authoritativeFocusEnabled(): Boolean = synchronized(lock) { deviceId } == "ayn-thor"

    private fun updateAutomationState() {
        val enabled = store.isAutomationEnabled()
        val granted = usageAccess.isGranted()
        val status =
            when {
                !enabled -> ProfileAutomationStatus.DISABLED
                !granted -> ProfileAutomationStatus.PERMISSION_REQUIRED
                else -> ProfileAutomationStatus.ACTIVE
            }
        serviceGate.setRequired(ServiceOwner.APP_PROFILES, status == ProfileAutomationStatus.ACTIVE)
        mutableState.value =
            ProfileRuntimeState(
                automationEnabled = enabled,
                automationStatus = status,
            )
    }

    private fun targetMatches(scope: ProfileScope): Boolean =
        when (scope) {
            ProfileScope.Global -> foregroundPackage == null
            is ProfileScope.App -> foregroundPackage == scope.packageName
        }

    private fun applyResolved() {
        val id = deviceId ?: return
        val target = resolveProfileTarget(previewScope, foregroundPackage, foregroundOverridesPreview)
        val global = store.global(id)
        globalModeCache = global.mode
        val profile =
            when (target) {
                ProfileTarget.Global -> global
                is ProfileTarget.ForegroundApp -> store.effective(id, target.packageName)
                is ProfileTarget.Preview -> profile(id, target.scope)
            }
        apply(profile)
    }

    private fun profile(
        deviceId: String,
        scope: ProfileScope,
    ): LightingProfile =
        when (scope) {
            ProfileScope.Global -> store.global(deviceId)
            is ProfileScope.App -> store.effective(deviceId, scope.packageName)
        }

    private fun apply(profile: LightingProfile) {
        controller.applyProfile(profileApplication(profile, zones, gradientSupported))
    }
}

internal fun profileApplication(
    profile: LightingProfile,
    zones: Int,
    gradientSupported: Boolean,
): ProfileApplication {
    val appliedMode =
        if (profile.mode == AppMode.GRADIENT && !gradientSupported) AppMode.COLOR else profile.mode
    val static =
        if (appliedMode == AppMode.GRADIENT) {
            GradientInterpolator.interpolate(profile.gradientStops, zones)
        } else {
            GradientInterpolator.interpolate(profile.staticColors, zones)
        }
    return ProfileApplication(
        mode = appliedMode,
        solidColor = profile.solidColor,
        gradientStops = profile.gradientStops,
        staticColors = static,
        effectId = profile.effectId,
        speed = profile.speed,
        gradientSpeed = profile.gradientSpeed,
        effectUsesGradient = profile.effectUsesGradient,
        brightness = profile.brightness,
        batteryBreathe = profile.batteryBreathe,
        temperatureBreathe = profile.temperatureBreathe,
    )
}
