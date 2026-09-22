package com.hooandee.colores.device

import com.hooandee.colores.device.learning.learningIdentityHash
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidDeviceIdentityReadTest {
    private val names = listOf("ro.product.model", "ro.product.board", "ro.board.platform")

    @Test
    fun `complete reads keep blank properties out of the identity`() {
        val identity =
            assembleAndroidDeviceIdentity(
                model = "Mystery",
                device = "mystery",
                manufacturer = "Maker",
                fingerprint = " maker/device:14/build ",
                propertyNames = names,
            ) { name -> if (name == "ro.board.platform") "" else "value-$name" }

        assertTrue(identity.complete)
        assertEquals("maker/device:14/build", identity.fingerprint)
        assertEquals(setOf("ro.product.model", "ro.product.board"), identity.productProperties.keys)
    }

    @Test
    fun `a failed property read marks the identity incomplete`() {
        val identity =
            assembleAndroidDeviceIdentity("Mystery", "mystery", "Maker", "", names) { name ->
                when (name) {
                    "ro.product.board" -> null
                    "ro.board.platform" -> error("timeout")
                    else -> "value"
                }
            }

        assertFalse(identity.complete)
        assertEquals(mapOf("ro.product.model" to "value"), identity.productProperties)
    }

    @Test
    fun `fingerprint does not change the learning identity hash`() {
        val base = assembleAndroidDeviceIdentity("Mystery", "mystery", "Maker", "a", names) { "value" }

        assertEquals(learningIdentityHash(base), learningIdentityHash(base.copy(fingerprint = "b")))
    }
}
