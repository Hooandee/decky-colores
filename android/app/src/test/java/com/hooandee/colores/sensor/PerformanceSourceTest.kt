package com.hooandee.colores.sensor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PerformanceSourceTest {
    @Test
    fun `cpu sampler seeds on the first read then reports load from deltas`() {
        val samples =
            ArrayDeque(
                listOf(
                    "cpu  100 0 100 800 0 0 0 0 0 0\n",
                    "cpu  150 0 150 900 0 0 0 0 0 0\n",
                ),
            )
        val source = CpuStatPerformanceSource { samples.removeFirstOrNull() ?: samples.lastOrNull() }
        assertNull("first read seeds the baseline", source.read())
        val load = source.read()!!
        assertEquals(50.0, load, 0.001)
    }

    @Test
    fun `cpu sampler is unavailable without a cpu line`() {
        assertFalse(CpuStatPerformanceSource { "intr 1 2 3\n" }.available)
        assertTrue(CpuStatPerformanceSource { "cpu  1 2 3 4 5 6\n" }.available)
    }

    @Test
    fun `gpu probe activates only on a plausible busy value`() {
        assertTrue(KgslGpuPerformanceSource { "42\n" }.available)
        assertEquals(42.0, KgslGpuPerformanceSource { "42 100\n" }.read()!!, 0.0)
        assertFalse(KgslGpuPerformanceSource { null }.available)
        assertFalse(KgslGpuPerformanceSource { "999\n" }.available)
    }

    @Test
    fun `detect prefers gpu when present and falls back to cpu`() {
        val gpu = KgslGpuPerformanceSource { "30\n" }
        val cpu = CpuStatPerformanceSource { "cpu  1 2 3 4\n" }
        assertEquals(PerformanceMetric.GPU, PerformanceSources.detect(gpu, cpu)!!.metric)

        val noGpu = KgslGpuPerformanceSource { null }
        assertEquals(PerformanceMetric.CPU, PerformanceSources.detect(noGpu, cpu)!!.metric)

        val noCpu = CpuStatPerformanceSource { null }
        assertNull(PerformanceSources.detect(noGpu, noCpu))
    }
}
