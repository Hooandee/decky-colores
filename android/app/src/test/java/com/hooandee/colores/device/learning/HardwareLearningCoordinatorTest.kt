package com.hooandee.colores.device.learning

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HardwareLearningCoordinatorTest {
    @Test
    fun `active learning owns the transport until its terminal operation finishes`() = runTest {
        val coordinator = HardwareLearningCoordinator()

        assertTrue(coordinator.begin { true })
        assertNull(coordinator.whenIdle { "runtime" })
        assertEquals("probe", coordinator.run { "probe" })
        assertEquals("restored", coordinator.finish { "restored" })
        assertEquals("runtime", coordinator.whenIdle { "runtime" })
    }

    @Test
    fun `failed preparation never claims the transport`() = runTest {
        val coordinator = HardwareLearningCoordinator()

        assertFalse(coordinator.begin { false })
        assertEquals("runtime", coordinator.whenIdle { "runtime" })
    }

    @Test
    fun `preparation exception leaves the transport available`() = runTest {
        val coordinator = HardwareLearningCoordinator()

        assertFalse(coordinator.begin { error("prepare") })
        assertEquals("runtime", coordinator.whenIdle { "runtime" })
    }
}
