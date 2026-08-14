package com.hooandee.colores.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SupportDestinationTest {
    @Test
    fun `normalizes an at-prefixed creator across support platforms`() {
        assertEquals("https://ko-fi.com/hooandee", supportUrl(SupportPlatform.KOFI, "@hooandee"))
        assertEquals("https://paypal.me/hooandee", supportUrl(SupportPlatform.PAYPAL, "@hooandee"))
        assertEquals("https://www.patreon.com/hooandee", supportUrl(SupportPlatform.PATREON, "@hooandee"))
    }

    @Test
    fun `rejects an empty creator handle`() {
        assertThrows(IllegalArgumentException::class.java) {
            supportUrl(SupportPlatform.KOFI, " @ ")
        }
    }
}
