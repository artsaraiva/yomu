package com.yomu.app.ui.home

import com.yomu.app.db.entities.ModelType
import com.yomu.app.service.ModelManager
import com.yomu.app.service.ModelSlotSelection
import com.yomu.app.service.ReadingModelSelection
import com.yomu.app.translation.MapSharedPreferences
import com.yomu.app.translation.TargetLanguageStore
import com.yomu.app.translation.TranslationModelSelection
import com.yomu.app.translation.Translator
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
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito

/** Setup's Download button fetches the phone model only while the phone will take the pages (#536). */
class SetupDownloadTest {
    private val prefs = MapSharedPreferences()
    private val models = Mockito.mock(ModelManager::class.java) { invocation ->
        if (invocation.method.name == "downloadModel") true else Mockito.RETURNS_DEFAULTS.answer(invocation)
    }
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
        models,
        TargetLanguageStore(prefs)
    )

    private fun downloaded(): List<Any?> =
        Mockito.mockingDetails(models).invocations.filter { it.method.name == "downloadModel" }.map { it.arguments[0] }

    @Test
    fun `setup on another translator downloads only the reading models`() = runTest {
        val state = MutableStateFlow(HomeUiState(setupTranslatorId = Translator.NETWORK_ID))

        assertTrue(SetupDownload(models, slots, state).run())

        assertEquals(listOf(ModelType.DETECTION, ModelType.OCR).map(ModelManager.SLOT_DEFAULTS::getValue), downloaded())
    }

    @Test
    fun `setup downloads the phone model too while the phone takes the pages`() = runTest {
        val state = MutableStateFlow(HomeUiState())

        assertTrue(SetupDownload(models, slots, state).run())

        assertEquals(ModelType.entries.map(ModelManager.SLOT_DEFAULTS::getValue), downloaded())
    }
}
