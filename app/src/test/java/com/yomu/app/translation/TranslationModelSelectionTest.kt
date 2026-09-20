package com.yomu.app.translation

import android.content.SharedPreferences
import com.yomu.core.Constants
import com.yomu.core.GenerationBound
import com.yomu.core.GenerationParams
import com.yomu.core.ModelProfile
import com.yomu.core.RuntimeLimits
import com.yomu.core.TranslationPromptMode
import com.yomu.core.TranslationSlot
import com.yomu.ml.LlamaTranslationBridge
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito

class TranslationModelSelectionTest {

    @Test
    fun `selection is not a translation slot`() {
        assertFalse(TranslationSlot::class.java.isAssignableFrom(TranslationModelSelection::class.java))
    }

    @Test
    fun `current returns the LLM slot`() {
        val llama = Mockito.mock(LlamaTranslationBridge::class.java)

        assertSame(llama, selection(llama = llama).current())
    }

    @Test
    fun `currentLlmModel defaults when nothing is picked`() {
        val prefs = Mockito.mock(SharedPreferences::class.java)
        Mockito.`when`(prefs.getString(Constants.PREF_LLM_MODEL, null))
            .thenReturn(null)

        assertSame(LlmModelCatalog.DEFAULT, selection(prefs = prefs).currentLlmModel())
    }

    @Test
    fun `selectLlmModel persists and applies the catalog profile`() = runTest {
        val editor = editor()
        val prefs = Mockito.mock(SharedPreferences::class.java).also {
            Mockito.`when`(it.edit()).thenReturn(editor)
        }
        val llama = Mockito.mock(LlamaTranslationBridge::class.java)
        val selection = selection(llama = llama, prefs = prefs)
        val option = LlmModelCatalog.fromId(Constants.CAT_TRANSLATION_MODEL_ID)!!

        selection.selectLlmModel(option)

        Mockito.verify(editor).putString(Constants.PREF_LLM_MODEL, option.id)
        val profile = Mockito.mockingDetails(llama).invocations.single().arguments[0] as ModelProfile
        assertEquals(option.ggufFileName, File(profile.modelPath).name)
        assertEquals(option.idKeyedBatch, profile.idKeyedBatch)
        assertEquals(TranslationPromptMode.MODEL_CARD, profile.promptMode)
    }

    @Test
    fun `selectLlmModel carries the stored profile and leaves it stored`() = runTest {
        val llama = Mockito.mock(LlamaTranslationBridge::class.java)
        val selection = selection(llama = llama)
        selection.saveGeneration(GenerationBound.TOP_K, 12f)
        Mockito.clearInvocations(llama)

        selection.selectLlmModel(LlmModelCatalog.fromId(Constants.CAT_TRANSLATION_MODEL_ID)!!)
        selection.selectLlmModel(LlmModelCatalog.DEFAULT)

        val applied = Mockito.mockingDetails(llama).invocations.map { (it.arguments[0] as ModelProfile).generation }
        assertEquals(listOf(GenerationParams(topK = 12), GenerationParams(topK = 12)), applied)
        assertEquals(GenerationParams(topK = 12), selection.generationProfile().params)
    }

    @Test
    fun `switching deliverables re-resolves maker defaults under the reader's saved fields`() = runTest {
        val llama = Mockito.mock(LlamaTranslationBridge::class.java)
        val selection = selection(llama = llama)
        val maker = GenerationParams(temperature = 0.7f, topK = 20, topP = 0.8f, penaltyPresent = 1.5f)
        val option = LlmModelCatalog.DEFAULT.copy(id = "maker-tuned", generationDefaults = maker)
        selection.saveGeneration(GenerationBound.TOP_K, 12f)
        Mockito.clearInvocations(llama)

        selection.selectLlmModel(option)
        selection.selectLlmModel(LlmModelCatalog.DEFAULT)

        val applied = Mockito.mockingDetails(llama).invocations.map { (it.arguments[0] as ModelProfile).generation }
        assertEquals(listOf(maker.copy(topK = 12), GenerationParams(topK = 12)), applied)
    }

    @Test
    fun `saveGeneration applies an accepted value to the slot and refuses a bad one`() = runTest {
        val llama = Mockito.mock(LlamaTranslationBridge::class.java)
        val selection = selection(llama = llama)

        assertTrue(selection.saveGeneration(GenerationBound.TEMPERATURE, 0.5f))
        assertFalse(selection.saveGeneration(GenerationBound.TEMPERATURE, 2f))

        val profile = Mockito.mockingDetails(llama).invocations.single().arguments[0] as ModelProfile
        assertEquals(GenerationParams(temperature = 0.5f), profile.generation)
        assertEquals(LlmModelCatalog.DEFAULT.ggufFileName, File(profile.modelPath).name)
    }

    @Test
    fun `resetGeneration restores and applies the shipped profile`() = runTest {
        val llama = Mockito.mock(LlamaTranslationBridge::class.java)
        val selection = selection(llama = llama)
        selection.saveGeneration(GenerationBound.TOP_P, 0.5f)

        selection.resetGeneration()

        val last = Mockito.mockingDetails(llama).invocations.last().arguments[0] as ModelProfile
        assertEquals(GenerationParams(), last.generation)
        assertEquals(GenerationParams(), selection.generationProfile().params)
    }

    @Test
    fun `saveResourceLimit applies accepted threads and context to the slot and refuses a bad value`() = runTest {
        val llama = Mockito.mock(LlamaTranslationBridge::class.java)
        val selection = selection(llama = llama)

        assertTrue(selection.saveResourceLimit(ResourceLimit.THREADS, 1))
        assertFalse(selection.saveResourceLimit(ResourceLimit.CONTEXT_TOKENS, 3000))
        assertTrue(selection.saveResourceLimit(ResourceLimit.CONTEXT_TOKENS, 1536))

        val last = Mockito.mockingDetails(llama).invocations.last().arguments[0] as ModelProfile
        assertEquals(RuntimeLimits(threads = 1, contextTokens = 1536), last.runtime)
        assertEquals(2, Mockito.mockingDetails(llama).invocations.size)
        assertEquals(1, selection.resourceLimit(ResourceLimit.THREADS))
    }

    @Test
    fun `resetResourceLimits restores and applies the shipped limits`() = runTest {
        val llama = Mockito.mock(LlamaTranslationBridge::class.java)
        val selection = selection(llama = llama)
        selection.saveResourceLimit(ResourceLimit.CONTEXT_TOKENS, 2816)

        selection.resetResourceLimits()

        val last = Mockito.mockingDetails(llama).invocations.last().arguments[0] as ModelProfile
        assertEquals(RuntimeLimits(), last.runtime)
    }

    @Test
    fun `an unknown stored model id recovers to the default and is reported`() {
        val prefs = MapSharedPreferences().apply { values[Constants.PREF_LLM_MODEL] = "retired-model" }
        val selection = selection(prefs = prefs)

        assertSame(LlmModelCatalog.DEFAULT, selection.currentLlmModel())
        assertTrue(selection.storedLlmModelRecovered())
        assertFalse(selection().storedLlmModelRecovered())
    }

    @Test
    fun `a wrongly typed stored model id recovers to the default and is reported`() {
        val prefs = MapSharedPreferences().apply { values[Constants.PREF_LLM_MODEL] = 42 }
        val selection = selection(prefs = prefs)

        assertSame(LlmModelCatalog.DEFAULT, selection.currentLlmModel())
        assertTrue(selection.storedLlmModelRecovered())
    }

    @Test
    fun `a cleared model is not reported as recovered`() {
        val prefs = MapSharedPreferences()
        val selection = selection(prefs = prefs)

        selection.clearLlmModel()
        selection.clearRecovered()

        assertTrue(selection.llmModelCleared())
        assertFalse(selection.storedLlmModelRecovered())
    }

    @Test
    fun `clearRecovered drops the bad stored values so the warning is raised once`() {
        val prefs = MapSharedPreferences().apply {
            values[Constants.PREF_LLM_MODEL] = "retired-model"
            values[GenerationProfileStore.key(GenerationBound.TOP_K)] = 500f
            values[GenerationProfileStore.key(GenerationBound.TOP_P)] = 0.5f
        }
        val selection = selection(prefs = prefs)

        selection.clearRecovered()

        assertFalse(selection.storedLlmModelRecovered())
        assertEquals(GenerationProfileStore.Loaded(GenerationParams(topP = 0.5f), emptyList()), selection.generationProfile())
    }

    @Test
    fun `close closes the slot`() {
        val llama = Mockito.mock(LlamaTranslationBridge::class.java)

        selection(llama = llama).close()

        Mockito.verify(llama).close()
    }

    private fun selection(
        llama: LlamaTranslationBridge = Mockito.mock(LlamaTranslationBridge::class.java),
        prefs: SharedPreferences = MapSharedPreferences()
    ): TranslationModelSelection = TranslationModelSelection(llama, prefs, File("models"))

    private fun editor(): SharedPreferences.Editor =
        Mockito.mock(SharedPreferences.Editor::class.java).also { editor ->
            Mockito.`when`(editor.putString(anyString(), anyString())).thenReturn(editor)
        }
}
