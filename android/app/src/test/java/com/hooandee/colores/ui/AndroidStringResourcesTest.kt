package com.hooandee.colores.ui

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidStringResourcesTest {
    @Test
    fun `english and spanish resources have matching keys and format arguments`() {
        val spanish = stringsFrom("src/main/res/values/strings.xml")
        val english = stringsFrom("src/main/res/values-en/strings.xml")

        assertEquals(spanish.keys, english.keys)
        spanish.forEach { (key, value) ->
            assertEquals("Format arguments differ for $key", formatArguments(value), formatArguments(english.getValue(key)))
        }
    }

    @Test
    fun `hardware learning describes the disruptive magenta signal`() {
        val spanish = stringsFrom("src/main/res/values/strings.xml").learningSignalText()
        val english = stringsFrom("src/main/res/values-en/strings.xml").learningSignalText()

        assertTrue(spanish.contains("magenta"))
        assertTrue(english.contains("magenta"))
        assertFalse(spanish.contains("azul"))
        assertFalse(english.contains("blue"))
    }

    private fun stringsFrom(path: String): Map<String, String> {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File(path))
        val nodes = document.getElementsByTagName("string")
        return buildMap {
            repeat(nodes.length) { index ->
                val node = nodes.item(index)
                put(node.attributes.getNamedItem("name").nodeValue, node.textContent)
            }
        }
    }

    private fun formatArguments(value: String): List<String> =
        FORMAT_ARGUMENT.findAll(value).map { it.value }.sorted().toList()

    private fun Map<String, String>.learningSignalText(): String =
        filterKeys { it in LEARNING_SIGNAL_KEYS }.values.joinToString(" ").lowercase()

    private companion object {
        val FORMAT_ARGUMENT = Regex("%(?:\\d+\\$)?(?:\\.\\d+)?[a-zA-Z]")
        val LEARNING_SIGNAL_KEYS =
            setOf(
                "hardware_learning_probe_color",
                "hardware_learning_probe_color_body",
                "hardware_learning_probe_zone_body",
                "hardware_learning_probe_power_on_body",
                "hardware_learning_saw_light",
            )
    }
}
