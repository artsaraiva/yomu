package com.yomu.app.translation

import android.content.SharedPreferences
import com.yomu.app.translation.MlKitTranslationBridge
import com.yomu.ml.LlamaTranslationBridge
import com.yomu.ml.TranslationOutput
import com.yomu.ml.TranslationStatus
import com.yomu.ml.opusmt.OpusMtTranslationBridge
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito

class TranslationEngineSelectorTest {

    @Test
    fun `fromId defaults to ML_KIT for unknown values`() {
        assertEquals(TranslationEngineType.ML_KIT, TranslationEngineType.fromId("nonexistent"))
        assertEquals(TranslationEngineType.ML_KIT, TranslationEngineType.fromId(""))
    }

    @Test
    fun `fromId returns correct type for known ids`() {
        assertEquals(TranslationEngineType.ML_KIT, TranslationEngineType.fromId("ml_kit"))
        assertEquals(TranslationEngineType.OPUS_MT, TranslationEngineType.fromId("opus_mt"))
        assertEquals(TranslationEngineType.LLM, TranslationEngineType.fromId("llm"))
    }

    @Test
    fun `engineId returns current engine id`() {
        val prefs = prefsWithEngine("ml_kit")
        val selector = TranslationEngineSelector(
            Mockito.mock(MlKitTranslationBridge::class.java),
            Mockito.mock(OpusMtTranslationBridge::class.java),
            Mockito.mock(LlamaTranslationBridge::class.java),
            prefs
        )

        assertEquals("ml_kit", selector.engineId)
        assertEquals(TranslationEngineType.ML_KIT, selector.currentEngine())
    }

    @Test
    fun `selectEngine switches current engine and saves to prefs`() {
        val editor = Mockito.mock(SharedPreferences.Editor::class.java)
        Mockito.`when`(editor.putString(anyString(), anyString())).thenReturn(editor)
        val prefs = Mockito.mock(SharedPreferences::class.java)
        Mockito.`when`(prefs.getString(Mockito.eq("translation_engine"), Mockito.any())).thenReturn("ml_kit")
        Mockito.`when`(prefs.edit()).thenReturn(editor)

        val mlKitBridge = Mockito.mock(MlKitTranslationBridge::class.java)
        val selector = TranslationEngineSelector(
            mlKitBridge,
            Mockito.mock(OpusMtTranslationBridge::class.java),
            Mockito.mock(LlamaTranslationBridge::class.java),
            prefs
        )

        selector.selectEngine(TranslationEngineType.OPUS_MT)

        assertEquals(TranslationEngineType.OPUS_MT, selector.currentEngine())
        assertEquals("opus_mt", selector.engineId)
        Mockito.verify(editor).putString("translation_engine", "opus_mt")
        Mockito.verify(editor).apply()
    }

    @Test
    fun `selectEngine clears memory on old bridge`() {
        val editor = Mockito.mock(SharedPreferences.Editor::class.java)
        Mockito.`when`(editor.putString(anyString(), anyString())).thenReturn(editor)
        val prefs = Mockito.mock(SharedPreferences::class.java)
        Mockito.`when`(prefs.getString(Mockito.eq("translation_engine"), Mockito.any())).thenReturn("ml_kit")
        Mockito.`when`(prefs.edit()).thenReturn(editor)

        val mlKitBridge = Mockito.mock(MlKitTranslationBridge::class.java)
        val selector = TranslationEngineSelector(
            mlKitBridge,
            Mockito.mock(OpusMtTranslationBridge::class.java),
            Mockito.mock(LlamaTranslationBridge::class.java),
            prefs
        )

        selector.selectEngine(TranslationEngineType.LLM)

        Mockito.verify(mlKitBridge).clearMemory()
    }

    @Test
    fun `translate delegates to active bridge`() = runTest {
        val prefs = prefsWithEngine("opus_mt")
        val expected = TranslationOutput("Hello", 0.85f, 50L)
        val opusMtBridge = Mockito.mock(OpusMtTranslationBridge::class.java)
        Mockito.`when`(opusMtBridge.status).thenReturn(TranslationStatus.Ready)
        Mockito.`when`(opusMtBridge.translate("こんにちは")).thenReturn(expected)

        val selector = TranslationEngineSelector(
            Mockito.mock(MlKitTranslationBridge::class.java),
            opusMtBridge,
            Mockito.mock(LlamaTranslationBridge::class.java),
            prefs
        )

        val result = selector.translate("こんにちは")

        assertEquals(expected, result)
        Mockito.verify(opusMtBridge).translate("こんにちは")
    }

    @Test
    fun `translate does not call inactive bridges`() = runTest {
        val prefs = prefsWithEngine("ml_kit")
        val mlKitBridge = Mockito.mock(MlKitTranslationBridge::class.java)
        Mockito.`when`(mlKitBridge.status).thenReturn(TranslationStatus.Ready)
        Mockito.`when`(mlKitBridge.translate("テスト")).thenReturn(
            TranslationOutput("Test", 0.8f, 10L)
        )
        val opusMtBridge = Mockito.mock(OpusMtTranslationBridge::class.java)
        val llamaBridge = Mockito.mock(LlamaTranslationBridge::class.java)

        val selector = TranslationEngineSelector(mlKitBridge, opusMtBridge, llamaBridge, prefs)

        selector.translate("テスト")

        Mockito.verifyNoInteractions(opusMtBridge)
        Mockito.verifyNoInteractions(llamaBridge)
    }

    @Test
    fun `close closes all three bridges`() {
        val prefs = prefsWithEngine("ml_kit")
        val mlKitBridge = Mockito.mock(MlKitTranslationBridge::class.java)
        val opusMtBridge = Mockito.mock(OpusMtTranslationBridge::class.java)
        val llamaBridge = Mockito.mock(LlamaTranslationBridge::class.java)

        val selector = TranslationEngineSelector(mlKitBridge, opusMtBridge, llamaBridge, prefs)

        selector.close()

        Mockito.verify(mlKitBridge).close()
        Mockito.verify(opusMtBridge).close()
        Mockito.verify(llamaBridge).close()
    }

    @Test
    fun `supportsBatch delegates to active bridge`() {
        val prefs = prefsWithEngine("llm")
        val llamaBridge = Mockito.mock(LlamaTranslationBridge::class.java)
        Mockito.`when`(llamaBridge.supportsBatch()).thenReturn(true)

        val selector = TranslationEngineSelector(
            Mockito.mock(MlKitTranslationBridge::class.java),
            Mockito.mock(OpusMtTranslationBridge::class.java),
            llamaBridge,
            prefs
        )

        assertEquals(true, selector.supportsBatch())
    }

    private fun prefsWithEngine(engineId: String): SharedPreferences {
        val prefs = Mockito.mock(SharedPreferences::class.java)
        Mockito.`when`(prefs.getString(Mockito.eq("translation_engine"), Mockito.any())).thenReturn(engineId)
        return prefs
    }
}
