package com.yomu.app.ui.home

import com.yomu.app.db.entities.ModelStatus
import com.yomu.app.db.entities.ModelType
import com.yomu.app.service.ModelManager
import com.yomu.app.service.ModelSlotSelection
import com.yomu.app.service.ReadingModelSelection
import com.yomu.app.translation.LlmModelCatalog
import com.yomu.app.translation.MapSharedPreferences
import com.yomu.app.translation.TargetLanguageStore
import com.yomu.app.translation.TranslationModelSelection
import com.yomu.core.Constants
import com.yomu.core.TargetLanguage
import com.yomu.ml.LlamaTranslationBridge
import com.yomu.pipeline.TranslationPipeline
import com.yomu.pipeline.bubble.BubbleDetector
import com.yomu.pipeline.context.ContextAssembler
import com.yomu.pipeline.ocr.OcrEngine
import com.yomu.pipeline.translation.TranslationEngine
import com.yomu.pipeline.typesetting.Typesetter
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito

/** Setup's first step (#547): the language, which decides the translator in the kit. */
class SetupLanguageTest {
    private val eightGb = 8L * 1024 * 1024 * 1024
    private val prefs = MapSharedPreferences()
    private val translation = TranslationModelSelection(Mockito.mock(LlamaTranslationBridge::class.java), prefs, File("models"))
    private val slots = ModelSlotSelection(
        translation,
        ReadingModelSelection(prefs),
        TranslationPipeline(
            Mockito.mock(BubbleDetector::class.java),
            Mockito.mock(OcrEngine::class.java),
            ContextAssembler(),
            TranslationEngine(translation::current, translation::close),
            Mockito.mock(Typesetter::class.java)
        ),
        Mockito.mock(ModelManager::class.java),
        TargetLanguageStore(prefs)
    )
    private val state = MutableStateFlow(HomeUiState(fitBudget = slots.fitBudget(eightGb)))
    private val language = SetupLanguage(slots, prefs, state)

    @Test
    fun `setup starts on English with the default translator in the kit`() {
        assertEquals(TargetLanguage.ENGLISH, slots.chosenLanguage())
        assertEquals(LlmModelCatalog.DEFAULT.id, slots.chosenId(ModelType.LLM))
    }

    @Test
    fun `a language the kit's translator can write is set and the translator kept`() = runTest {
        language.choose(TargetLanguage.BRAZILIAN_PORTUGUESE)

        assertEquals(TargetLanguage.BRAZILIAN_PORTUGUESE, slots.targetLanguage())
        assertEquals(LlmModelCatalog.DEFAULT.id, slots.chosenId(ModelType.LLM))
    }

    @Test
    fun `a language an English-only translator can't write puts the default translator in the kit`() = runTest {
        slots.pick(ModelType.LLM, Constants.CAT_TRANSLATION_MODEL_ID, ModelStatus.AVAILABLE, eightGb)

        language.choose(TargetLanguage.SPANISH)

        assertEquals(LlmModelCatalog.DEFAULT.id, slots.chosenId(ModelType.LLM))
        assertEquals(TargetLanguage.SPANISH, slots.chosenLanguage())
    }

    @Test
    fun `confirming the language is remembered, and reopening it is not`() {
        assertFalse(language.wasChosen())

        language.confirm()

        assertTrue(state.value.setupLanguageChosen)
        assertTrue(SetupLanguage(slots, prefs, MutableStateFlow(HomeUiState())).wasChosen())

        language.reopen()

        assertFalse(state.value.setupLanguageChosen)
        assertTrue(language.wasChosen())
    }
}
