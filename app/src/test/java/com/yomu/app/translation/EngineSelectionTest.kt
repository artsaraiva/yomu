package com.yomu.app.translation

import android.content.SharedPreferences
import com.yomu.core.Constants
import com.yomu.core.ModelProfile
import com.yomu.core.TranslationPromptMode
import com.yomu.core.TranslationSlot
import com.yomu.ml.LlamaTranslationBridge
import com.yomu.ml.opusmt.OpusMtTranslationBridge
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito

class EngineSelectionTest {

    @Test
    fun `selection is not a translation slot`() {
        assertFalse(TranslationSlot::class.java.isAssignableFrom(EngineSelection::class.java))
    }

    @Test
    fun `fromId defaults to ML Kit for unknown values`() {
        assertEquals(TranslationEngineType.ML_KIT, TranslationEngineType.fromId("nonexistent"))
        assertEquals(TranslationEngineType.ML_KIT, TranslationEngineType.fromId(""))
    }

    @Test
    fun `current returns the selected slot`() {
        val mlKit = Mockito.mock(MlKitTranslationBridge::class.java)
        val opus = Mockito.mock(OpusMtTranslationBridge::class.java)
        val llama = Mockito.mock(LlamaTranslationBridge::class.java)

        assertSame(mlKit, selection("ml_kit", mlKit, opus, llama).current())
        assertSame(opus, selection("opus_mt", mlKit, opus, llama).current())
        assertSame(llama, selection("llm", mlKit, opus, llama).current())
    }

    @Test
    fun `selectEngine ends the old session and persists the new engine`() {
        val editor = editor()
        val prefs = prefsWithEngine("ml_kit", editor)
        val mlKit = Mockito.mock(MlKitTranslationBridge::class.java)
        val selection = EngineSelection(
            mlKit,
            Mockito.mock(OpusMtTranslationBridge::class.java),
            Mockito.mock(LlamaTranslationBridge::class.java),
            prefs
        )

        selection.selectEngine(TranslationEngineType.OPUS_MT)

        assertEquals(TranslationEngineType.OPUS_MT, selection.currentEngine())
        Mockito.verify(mlKit).endSession()
        Mockito.verify(editor).putString(Constants.PREF_TRANSLATION_ENGINE, "opus_mt")
        Mockito.verify(editor).apply()
    }

    @Test
    fun `currentLlmModel defaults when nothing is picked`() {
        val prefs = prefsWithEngine("llm")
        Mockito.`when`(prefs.getString(Constants.PREF_LLM_MODEL, null))
            .thenReturn(null)

        assertSame(LlmModelCatalog.DEFAULT, selection("llm", prefs = prefs).currentLlmModel())
    }

    @Test
    fun `selectLlmModel persists and applies the catalog profile`() = runTest {
        val editor = editor()
        val prefs = prefsWithEngine("llm", editor)
        val llama = Mockito.mock(LlamaTranslationBridge::class.java)
        val selection = selection("llm", llama = llama, prefs = prefs)
        val option = LlmModelCatalog.fromId(Constants.CAT_TRANSLATION_MODEL_ID)!!

        selection.selectLlmModel(option)

        Mockito.verify(editor).putString(Constants.PREF_LLM_MODEL, option.id)
        val profile = Mockito.mockingDetails(llama).invocations.single().arguments[0] as ModelProfile
        assertEquals(option.ggufFileName, File(profile.modelPath).name)
        assertEquals(option.idKeyedBatch, profile.idKeyedBatch)
        assertEquals(TranslationPromptMode.MODEL_CARD, profile.promptMode)
    }

    @Test
    fun `close closes all slots`() {
        val mlKit = Mockito.mock(MlKitTranslationBridge::class.java)
        val opus = Mockito.mock(OpusMtTranslationBridge::class.java)
        val llama = Mockito.mock(LlamaTranslationBridge::class.java)
        val selection = selection("ml_kit", mlKit, opus, llama)

        selection.close()

        Mockito.verify(mlKit).close()
        Mockito.verify(opus).close()
        Mockito.verify(llama).close()
    }

    private fun selection(
        engine: String,
        mlKit: MlKitTranslationBridge = Mockito.mock(MlKitTranslationBridge::class.java),
        opus: OpusMtTranslationBridge = Mockito.mock(OpusMtTranslationBridge::class.java),
        llama: LlamaTranslationBridge = Mockito.mock(LlamaTranslationBridge::class.java),
        prefs: SharedPreferences = prefsWithEngine(engine)
    ): EngineSelection = EngineSelection(mlKit, opus, llama, prefs, File("models"))

    private fun prefsWithEngine(
        engineId: String,
        editor: SharedPreferences.Editor? = null
    ): SharedPreferences = Mockito.mock(SharedPreferences::class.java).also { prefs ->
        Mockito.`when`(prefs.getString(Constants.PREF_TRANSLATION_ENGINE, null))
            .thenReturn(engineId)
        editor?.let { Mockito.`when`(prefs.edit()).thenReturn(it) }
    }

    private fun editor(): SharedPreferences.Editor =
        Mockito.mock(SharedPreferences.Editor::class.java).also { editor ->
            Mockito.`when`(editor.putString(anyString(), anyString())).thenReturn(editor)
        }
}
