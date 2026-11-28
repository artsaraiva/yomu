package com.yomu.app.ui.settings

import com.yomu.app.db.entities.ModelType
import com.yomu.app.service.SlotDeliverable
import com.yomu.app.translation.ResourceLimit
import com.yomu.core.GenerationBound
import com.yomu.core.GenerationParams
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    @Test
    fun `the detection threshold shows two decimals and its default once moved`() {
        assertEquals(SliderText("0.25", default = null), thresholdText(0.25f))
        assertEquals(SliderText("0.40", default = "0.25"), thresholdText(0.40f))
    }

    @Test
    fun `a sampler value shows as many decimals as its step has`() {
        assertEquals(SliderText("0.7", default = "0.2"), generationText(GenerationBound.TEMPERATURE, 0.7f, default = 0.2f))
        assertEquals(SliderText("40", default = null), generationText(GenerationBound.TOP_K, 40f, default = 40f))
        assertEquals(SliderText("0.95", default = "0.90"), generationText(GenerationBound.TOP_P, 0.95f, default = 0.9f))
    }

    @Test
    fun `a model default between two steps still shows once the slider leaves it`() {
        assertEquals(SliderText("0.1", default = "0.05"), generationText(GenerationBound.TEMPERATURE, 0.1f, default = 0.05f))
    }

    @Test
    fun `a sampler value a rounding error away from its default counts as the default`() {
        assertEquals(SliderText("0.9", default = null), generationText(GenerationBound.TEMPERATURE, 9 * 0.1f, default = 0.9f))
    }

    private val state = SettingsUiState(
        deliverables = mapOf(
            ModelType.DETECTION to listOf(SlotDeliverable("det", "Comic text detector", 1, "MIT")),
            ModelType.OCR to listOf(SlotDeliverable("ocr", "manga-ocr", 1, "Apache-2.0"))
        ),
        selectedIds = mapOf(ModelType.DETECTION to "det", ModelType.OCR to "ocr")
    )

    @Test
    fun `closed bubble finding names the detector and whether the threshold moved`() {
        assertEquals("Comic text detector · threshold at default", state.summary(AdvancedSection.BubbleFinding))
        assertEquals("Comic text detector · threshold 0.40", state.copy(detectionThreshold = 0.40f).summary(AdvancedSection.BubbleFinding))
    }

    @Test
    fun `closed text reading names the reading model, or says none is picked`() {
        assertEquals("manga-ocr", state.summary(AdvancedSection.TextReading))
        assertEquals("No model selected", state.copy(selectedIds = emptyMap()).summary(AdvancedSection.TextReading))
    }

    @Test
    fun `closed generation says whether the sampler left the model's defaults`() {
        assertEquals("Sampler · this model's defaults", state.summary(AdvancedSection.Generation))
        assertEquals(
            "Sampler · changed from this model's defaults",
            state.copy(generation = GenerationParams(temperature = 0.7f)).summary(AdvancedSection.Generation)
        )
    }

    @Test
    fun `closed performance lists the three limits`() {
        val limits = mapOf(ResourceLimit.THREADS to 2, ResourceLimit.CONTEXT_TOKENS to 1536, ResourceLimit.FIT_BUDGET_PERCENT to 75)

        assertEquals("2 threads · 1536 tokens · 75% memory", state.copy(resourceLimits = limits).summary(AdvancedSection.Performance))
    }

    @Test
    fun `one thread is singular`() {
        val limits = state.resourceLimits + (ResourceLimit.THREADS to 1)

        assertTrue(state.copy(resourceLimits = limits).summary(AdvancedSection.Performance).startsWith("1 thread · "))
    }

    @Test
    fun `closed live usage says what it measures`() {
        assertEquals("Memory, CPU and the last page's time", state.summary(AdvancedSection.LiveUsage))
    }

    @Test
    fun `a section can be reset only while something in it differs from its default`() {
        AdvancedSection.entries.forEach { assertFalse(it.name, state.canReset(it)) }

        assertTrue(state.copy(detectionThreshold = 0.40f).canReset(AdvancedSection.BubbleFinding))
        assertTrue(state.copy(generation = GenerationParams(topK = 10)).canReset(AdvancedSection.Generation))
        assertTrue(state.copy(resourceLimits = state.resourceLimits + (ResourceLimit.CONTEXT_TOKENS to 1536)).canReset(AdvancedSection.Performance))
    }

    @Test
    fun `a recovery warning names every setting that fell back, and none means no warning`() {
        assertEquals(
            "Some saved settings were invalid and are using their defaults: Temperature, Top-k.",
            recoveryWarning(listOf("Temperature", "Top-k"))
        )
        assertNull(recoveryWarning(emptyList()))
    }
}
