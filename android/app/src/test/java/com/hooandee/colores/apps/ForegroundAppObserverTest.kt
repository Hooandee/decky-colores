package com.hooandee.colores.apps

import com.hooandee.colores.led.PServerCommandExecutor
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ForegroundAppObserverTest {
    @Test
    fun `latest resumed external package wins`() {
        val events =
            listOf(
                ForegroundUsageEvent("org.old", 10, true),
                ForegroundUsageEvent("com.hooandee.colores", 20, true),
                ForegroundUsageEvent("org.game", 30, true),
            )

        assertEquals("org.game", latestForeground(events, "com.hooandee.colores"))
    }

    @Test
    fun `paused transient app cannot replace an app that remains resumed`() {
        val events =
            listOf(
                ForegroundUsageEvent("org.game", 10, true),
                ForegroundUsageEvent("org.settings", 20, true),
                ForegroundUsageEvent("org.settings", 30, false),
            )

        assertEquals("org.game", latestForeground(events, "com.hooandee.colores"))
    }

    @Test
    fun `configured app wins when two displays keep apps resumed`() {
        val active =
            updateActivePackages(
                emptyMap(),
                listOf(
                    ForegroundUsageEvent("app.gamenative", 10, true),
                    ForegroundUsageEvent("rip.moth.cocoonshell", 20, true),
                ),
            )

        assertEquals(
            "app.gamenative",
            selectForeground(
                activePackages = active,
                ownPackage = "com.hooandee.colores",
                preferredPackages = setOf("app.gamenative"),
            ),
        )
    }

    @Test
    fun `pause removes a package from retained multi display state`() {
        val active =
            updateActivePackages(
                mapOf("app.gamenative" to 10L, "org.settings" to 20L),
                listOf(ForegroundUsageEvent("org.settings", 30, false)),
            )

        assertEquals(
            "app.gamenative",
            selectForeground(active, "com.hooandee.colores", emptySet()),
        )
    }

    @Test
    fun `first observation reaches an app resumed before the polling window`() {
        val now = 86_400_100L
        val earlierResume = 1_000L

        val initialStart = foregroundQueryStart(now, initialized = false)
        val pollingStart = foregroundQueryStart(now, initialized = true)

        assertTrue(initialStart <= earlierResume)
        assertTrue(pollingStart > earlierResume)
    }

    @Test
    fun `Thor global resumed activity identifies the focused display app`() {
        val activityState =
            "  ResumedActivity: ActivityRecord{fb8b11d u0 app.gamenative/.MainActivityAliasDefault} t837}"

        assertEquals("app.gamenative", parseFocusedPackage(activityState))
    }

    @Test
    fun `authoritative focused display wins over incomplete usage events`() {
        assertEquals(
            "app.gamenative",
            resolveForeground(
                authoritativePackage = "app.gamenative",
                activePackages = mapOf("rip.moth.cocoonshell" to 20L),
                ownPackage = "com.hooandee.colores",
                preferredPackages = setOf("app.gamenative"),
            ),
        )
    }

    @Test
    fun `external authoritative focus overrides profile preview`() {
        assertEquals(
            ForegroundSelection("app.gamenative", true),
            resolveForegroundSelection(
                authoritativePackage = "app.gamenative",
                activePackages = mapOf("app.gamenative" to 20L),
                ownPackage = "com.hooandee.colores",
                preferredPackages = setOf("app.gamenative"),
            ),
        )
    }

    @Test
    fun `own authoritative focus preserves profile preview`() {
        assertEquals(
            ForegroundSelection("app.gamenative", false),
            resolveForegroundSelection(
                authoritativePackage = "com.hooandee.colores",
                activePackages = mapOf("app.gamenative" to 20L),
                ownPackage = "com.hooandee.colores",
                preferredPackages = setOf("app.gamenative"),
            ),
        )
    }

    @Test
    fun `PServer writes focus into an app owned temporary file`() {
        val output = File.createTempFile("colores-focus", ".txt").apply(File::delete)
        var fileExistedWhenPServerRan = false
        val executor =
            object : PServerCommandExecutor {
                override val available = true

                override fun execute(command: String): Boolean {
                    fileExistedWhenPServerRan = output.exists()
                    if (fileExistedWhenPServerRan) {
                        output.writeText(
                            "  ResumedActivity: ActivityRecord{fb8b11d u0 app.gamenative/.MainActivityAliasDefault} t837}",
                        )
                    }
                    return true
                }
            }
        val resolver = PServerFocusedAppResolver(output, executor)

        assertEquals("app.gamenative", resolver.resolve())
        assertTrue(fileExistedWhenPServerRan)
        output.delete()
    }

    @Test
    fun `revoked access reports unavailable instead of stale package`() {
        val state = reduceUsage(
            granted = false,
            events = listOf(ForegroundUsageEvent("org.game", 30, true)),
            ownPackage = "com.hooandee.colores",
        )

        assertEquals(ForegroundAppState.PermissionRequired, state)
    }

    @Test
    fun `paused and own events do not become foreground targets`() {
        val events =
            listOf(
                ForegroundUsageEvent("org.paused", 40, false),
                ForegroundUsageEvent("com.hooandee.colores", 50, true),
            )

        assertEquals(ForegroundAppState.Active(null), reduceUsage(true, events, "com.hooandee.colores"))
    }

    @Test
    fun `no new resumed event preserves the last foreground package`() {
        assertEquals(
            "org.game",
            nextForeground("org.game", emptyList(), "com.hooandee.colores"),
        )
    }
}
