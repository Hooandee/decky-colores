package com.hooandee.colores.device

import java.io.File
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlatformSupportContractTest {
    private val shared = File("../../shared")

    @Test
    fun `platform support registry has unique features and valid references`() {
        val root = JSONObject(shared.resolve("platform-support.json").readText())
        val statuses = root.getJSONArray("statuses").strings().toSet()
        val features = root.getJSONArray("features")
        val ids = mutableSetOf<String>()

        assertEquals(1, root.getInt("schemaVersion"))
        assertEquals(
            setOf("validated", "implemented", "planned", "deferred", "unsupported"),
            statuses,
        )

        repeat(features.length()) { index ->
            val feature = features.getJSONObject(index)
            val platforms = feature.getJSONObject("platforms")

            assertTrue(ids.add(feature.getString("id")))
            assertEquals(setOf("decky", "android", "windows"), platforms.keys().asSequence().toSet())
            platforms.keys().forEach { platform ->
                assertTrue(platforms.getString(platform) in statuses)
            }
            feature.getJSONArray("contracts").strings().forEach { path ->
                assertTrue("Missing shared contract: $path", shared.resolve(path).isFile)
            }
        }
    }
}

private fun org.json.JSONArray.strings(): List<String> =
    (0 until length()).map(::getString)
