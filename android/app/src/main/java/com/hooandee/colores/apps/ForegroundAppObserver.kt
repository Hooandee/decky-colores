package com.hooandee.colores.apps

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow

data class ForegroundUsageEvent(
    val packageName: String,
    val timestamp: Long,
    val resumed: Boolean,
)

fun updateActivePackages(
    previous: Map<String, Long>,
    events: List<ForegroundUsageEvent>,
): Map<String, Long> =
    previous.toMutableMap().apply {
        events.sortedBy(ForegroundUsageEvent::timestamp).forEach { event ->
            if (event.resumed) {
                this[event.packageName] = event.timestamp
            } else {
                remove(event.packageName)
            }
        }
    }

fun selectForeground(
    activePackages: Map<String, Long>,
    ownPackage: String,
    preferredPackages: Set<String>,
): String? {
    val external = activePackages.filterKeys { it != ownPackage }
    val preferred = external.filterKeys { it in preferredPackages }
    val candidates = preferred.ifEmpty { external }
    return candidates.maxWithOrNull(compareBy<Map.Entry<String, Long>> { it.value }.thenBy { it.key })?.key
}

fun parseFocusedPackage(raw: String): String? =
    Regex("""ResumedActivity: ActivityRecord\{.* u\d+ ([^/\s]+)/""")
        .find(raw)
        ?.groupValues
        ?.getOrNull(1)

fun resolveForeground(
    authoritativePackage: String?,
    activePackages: Map<String, Long>,
    ownPackage: String,
    preferredPackages: Set<String>,
): String? = resolveForegroundSelection(authoritativePackage, activePackages, ownPackage, preferredPackages).packageName

fun resolveForegroundSelection(
    authoritativePackage: String?,
    activePackages: Map<String, Long>,
    ownPackage: String,
    preferredPackages: Set<String>,
): ForegroundSelection {
    val authoritativeExternal =
        authoritativePackage
            ?.trim()
            ?.takeIf { it.isNotEmpty() && it != ownPackage }
    return ForegroundSelection(
        packageName = authoritativeExternal ?: selectForeground(activePackages, ownPackage, preferredPackages),
        authoritativeExternal = authoritativeExternal != null,
    )
}

fun foregroundQueryStart(
    end: Long,
    lastQueryEnd: Long?,
): Long =
    if (lastQueryEnd == null) {
        end - INITIAL_LOOKBACK_MS
    } else {
        (minOf(lastQueryEnd, end) - POLL_LOOKBACK_MS).coerceAtLeast(end - INITIAL_LOOKBACK_MS)
    }

fun foregroundPollDelayMs(state: ForegroundAppState): Long =
    when (state) {
        is ForegroundAppState.Active -> ACTIVE_POLL_MS
        ForegroundAppState.Disabled,
        ForegroundAppState.PermissionRequired,
        -> IDLE_POLL_MS
    }

sealed interface ForegroundAppState {
    data object Disabled : ForegroundAppState

    data object PermissionRequired : ForegroundAppState

    data class Active(
        val packageName: String?,
        val authoritativeExternal: Boolean = false,
    ) : ForegroundAppState
}

data class ForegroundSelection(
    val packageName: String?,
    val authoritativeExternal: Boolean,
)

fun latestForeground(
    events: List<ForegroundUsageEvent>,
    ownPackage: String,
): String? {
    val active = updateActivePackages(emptyMap(), events)
    return selectForeground(active, ownPackage, emptySet())
}

fun nextForeground(
    previous: String?,
    events: List<ForegroundUsageEvent>,
    ownPackage: String,
): String? {
    if (events.isEmpty()) return previous
    val active = updateActivePackages(previous?.let { mapOf(it to Long.MIN_VALUE) }.orEmpty(), events)
    return selectForeground(active, ownPackage, emptySet())
}

fun reduceUsage(
    granted: Boolean,
    events: List<ForegroundUsageEvent>,
    ownPackage: String,
): ForegroundAppState =
    if (!granted) {
        ForegroundAppState.PermissionRequired
    } else {
        ForegroundAppState.Active(latestForeground(events, ownPackage))
    }

class ForegroundAppObserver(
    private val context: Context,
    private val usageAccess: UsageAccess = UsageAccess(context),
    private val clock: () -> Long = System::currentTimeMillis,
    private val focusedAppResolver: FocusedAppResolver = NoFocusedAppResolver,
    private val interactive: StateFlow<Boolean> = MutableStateFlow(true),
) {
    private var currentPackage: String? = null
    private var activePackages = emptyMap<String, Long>()
    private var lastQueryEnd: Long? = null

    fun observe(
        enabled: () -> Boolean,
        preferredPackages: () -> Set<String> = ::emptySet,
        authoritativeFocusEnabled: () -> Boolean = { false },
    ): Flow<ForegroundAppState> =
        flow {
            while (true) {
                interactive.first { it }
                val state =
                    if (!enabled()) {
                        currentPackage = null
                        activePackages = emptyMap()
                        lastQueryEnd = null
                        ForegroundAppState.Disabled
                    } else if (!usageAccess.isGranted()) {
                        currentPackage = null
                        activePackages = emptyMap()
                        lastQueryEnd = null
                        ForegroundAppState.PermissionRequired
                    } else {
                        val selection = readLatest(preferredPackages(), authoritativeFocusEnabled())
                        currentPackage = selection.packageName
                        ForegroundAppState.Active(currentPackage, selection.authoritativeExternal)
                    }
                emit(state)
                delay(foregroundPollDelayMs(state))
            }
        }.distinctUntilChanged()

    private fun readLatest(
        preferredPackages: Set<String>,
        authoritativeFocusEnabled: Boolean,
    ): ForegroundSelection {
        val end = clock()
        val manager = context.getSystemService(UsageStatsManager::class.java)
        val events = manager.queryEvents(foregroundQueryStart(end, lastQueryEnd), end)
        val event = UsageEvents.Event()
        val collected = mutableListOf<ForegroundUsageEvent>()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val packageName = event.packageName ?: continue
            val resumed =
                when (event.eventType) {
                    UsageEvents.Event.ACTIVITY_RESUMED -> true
                    UsageEvents.Event.ACTIVITY_PAUSED,
                    UsageEvents.Event.ACTIVITY_STOPPED,
                    -> false
                    else -> continue
                }
            collected +=
                ForegroundUsageEvent(
                    packageName = packageName,
                    timestamp = event.timeStamp,
                    resumed = resumed,
                )
        }
        activePackages = updateActivePackages(activePackages, collected)
        lastQueryEnd = end
        val authoritativePackage = if (authoritativeFocusEnabled) focusedAppResolver.resolve() else null
        return resolveForegroundSelection(authoritativePackage, activePackages, context.packageName, preferredPackages)
    }

}

private const val ACTIVE_POLL_MS = 1_000L
private const val IDLE_POLL_MS = 5_000L
private const val POLL_LOOKBACK_MS = 5_000L
private const val INITIAL_LOOKBACK_MS = 86_400_000L
