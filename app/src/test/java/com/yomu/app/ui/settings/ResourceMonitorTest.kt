package com.yomu.app.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ResourceMonitorTest {

    @Test
    fun `cpu percent is the share of every core used over the interval`() {
        assertEquals(50, cpuPercent(cpuDeltaMs = 2_000, wallDeltaMs = 1_000, cores = 4))
        assertEquals(100, cpuPercent(cpuDeltaMs = 4_000, wallDeltaMs = 1_000, cores = 4))
        assertEquals(0, cpuPercent(cpuDeltaMs = 0, wallDeltaMs = 1_000, cores = 8))
    }

    @Test
    fun `cpu percent is clamped to 0 to 100`() {
        assertEquals(100, cpuPercent(cpuDeltaMs = 9_000, wallDeltaMs = 1_000, cores = 2))
        assertEquals(0, cpuPercent(cpuDeltaMs = -5, wallDeltaMs = 1_000, cores = 2))
    }

    @Test
    fun `no elapsed time gives no cpu reading`() {
        assertNull(cpuPercent(cpuDeltaMs = 10, wallDeltaMs = 0, cores = 4))
    }
}
