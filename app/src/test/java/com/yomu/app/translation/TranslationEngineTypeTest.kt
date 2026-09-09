package com.yomu.app.translation

import org.junit.Assert.assertEquals
import org.junit.Test

class TranslationEngineTypeTest {

    @Test
    fun `each engine declares whether it is the gate or a floor`() {
        assertEquals(EngineRole.FLOOR, TranslationEngineType.ML_KIT.role)
        assertEquals(EngineRole.FLOOR, TranslationEngineType.OPUS_MT.role)
        assertEquals(EngineRole.GATE, TranslationEngineType.LLM.role)
    }

    @Test
    fun `persisted ids are unchanged`() {
        assertEquals("ml_kit", TranslationEngineType.ML_KIT.id)
        assertEquals("opus_mt", TranslationEngineType.OPUS_MT.id)
        assertEquals("llm", TranslationEngineType.LLM.id)
    }
}
