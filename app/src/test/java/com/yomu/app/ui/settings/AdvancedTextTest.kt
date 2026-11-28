package com.yomu.app.ui.settings

import com.yomu.app.translation.ResourceLimit
import org.junit.Assert.assertEquals
import org.junit.Test

class AdvancedTextTest {

    @Test
    fun `a limit at its default shows its value and no default`() {
        assertEquals(SliderText("2048 tokens", default = null), limitText(ResourceLimit.CONTEXT_TOKENS, 2048))
    }

    @Test
    fun `a limit moved off its default shows the default beside the value`() {
        assertEquals(SliderText("1536 tokens", default = "2048 tokens"), limitText(ResourceLimit.CONTEXT_TOKENS, 1536))
        assertEquals(SliderText("75% of this phone", default = "60% of this phone"), limitText(ResourceLimit.FIT_BUDGET_PERCENT, 75))
    }

    @Test
    fun `threads are counted against the phone's cores`() {
        assertEquals("1 of 8 cores", limitText(ResourceLimit.THREADS, 1, cores = 8).value)
    }
}
