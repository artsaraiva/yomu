package com.yomu.app.service

import com.yomu.app.db.entities.ModelStatus
import com.yomu.app.db.entities.ModelType
import com.yomu.app.translation.LlmModelCatalog
import com.yomu.app.translation.MapSharedPreferences
import com.yomu.app.translation.ServerSettingsStore
import com.yomu.app.translation.TargetLanguageStore
import com.yomu.app.translation.TranslationModelSelection
import com.yomu.app.translation.Translator
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
import javax.crypto.KeyGenerator
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito

/** Choosing between the phone's models and the server's in the model picker (#490, ADR-0023). */
class TranslatorChoiceTest {
    private val prefs = MapSharedPreferences()
    private val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    private val store = ServerSettingsStore(prefs, MapSharedPreferences()) { key }
    private val llama = Mockito.mock(LlamaTranslationBridge::class.java)
    private val translation = TranslationModelSelection(
        llama, prefs, File("models"), translators = setOf(Translator.onDevice(llama), Translator.network(store, prefs, OkHttpClient()))
    )
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
    private val choice = TranslatorChoice(translation, store, slots)

    @Test
    fun `the server group lists nothing until the server is set up`() {
        assertNull(choice.serverModels())

        store.saveAddress(ADDRESS)
        store.confirm(ADDRESS)
        store.saveServerModels(listOf("qwen2.5:7b", "gemma3:4b"))
        assertNull("connected, but no model picked yet", choice.serverModels())

        store.saveModel("qwen2.5:7b")
        assertEquals(listOf("qwen2.5:7b", "gemma3:4b"), choice.serverModels())
    }

    @Test
    fun `a server that keeps no list shows its typed model name`() {
        setUpServer(listed = emptyList(), model = "qwen2.5:7b-instruct")

        assertEquals(listOf("qwen2.5:7b-instruct"), choice.serverModels())
    }

    @Test
    fun `the first server pick asks for the notice and changes nothing until it is accepted`() {
        setUpServer()

        val notice = choice.pickServerModel("gemma3:4b")

        assertEquals(ServerPick.Notice("gemma3:4b", ADDRESS), notice)
        assertNull(choice.activeServerModel())
        assertEquals("qwen2.5:7b", store.model())

        assertTrue(choice.acceptNotice(notice as ServerPick.Notice))

        assertEquals("gemma3:4b", choice.activeServerModel())
        assertEquals("gemma3:4b", store.model())
        assertEquals(Translator.NETWORK_ID, translation.activeTranslator().id)
    }

    @Test
    fun `declining the notice leaves the phone in use, and the next server pick asks again`() {
        setUpServer()

        choice.pickServerModel("gemma3:4b")

        assertEquals(Translator.ON_DEVICE_ID, translation.activeTranslator().id)
        assertEquals(ServerPick.Notice("gemma3:4b", ADDRESS), choice.pickServerModel("gemma3:4b"))
    }

    @Test
    fun `once the notice is accepted, a server pick takes the next page at once`() {
        setUpServer()
        choice.acceptNotice(choice.pickServerModel("gemma3:4b") as ServerPick.Notice)

        assertEquals(ServerPick.Done, choice.pickServerModel("qwen2.5:7b"))

        assertEquals("qwen2.5:7b", choice.activeServerModel())
    }

    @Test
    fun `a server model can't be picked while the server isn't set up, nor one the server doesn't list`() {
        store.acceptNotice()
        assertEquals(ServerPick.Refused, choice.pickServerModel("qwen2.5:7b"))

        setUpServer()
        assertEquals(ServerPick.Refused, choice.pickServerModel("llama3.1:70b"))
        assertNull(choice.activeServerModel())
    }

    @Test
    fun `a notice for an address the reader has since changed is refused`() {
        setUpServer()
        val notice = choice.pickServerModel("gemma3:4b") as ServerPick.Notice
        store.saveAddress("http://192.168.1.21:11434")

        assertFalse(choice.acceptNotice(notice))

        assertFalse(store.noticeAccepted())
        assertEquals(Translator.ON_DEVICE_ID, translation.activeTranslator().id)
    }

    @Test
    fun `changing the server's address switches back to the phone until a server model is picked again`() {
        setUpServer()
        choice.acceptNotice(choice.pickServerModel("gemma3:4b") as ServerPick.Notice)

        store.saveAddress("http://192.168.1.21:11434")
        store.confirm("http://192.168.1.21:11434")
        store.saveServerModels(listOf("llama3.1:8b"))
        store.saveModel("llama3.1:8b")

        assertNull("set up again in Providers, but not picked", choice.activeServerModel())
        assertEquals(Translator.ON_DEVICE_ID, translation.activeTranslator().id)
        assertEquals(ServerPick.Done, choice.pickServerModel("llama3.1:8b"))
        assertEquals("llama3.1:8b", choice.activeServerModel())
    }

    @Test
    fun `saving the same address again keeps the server in use`() {
        setUpServer()
        choice.acceptNotice(choice.pickServerModel("gemma3:4b") as ServerPick.Notice)

        store.saveAddress(" $ADDRESS ")

        assertEquals("gemma3:4b", choice.activeServerModel())
    }

    @Test
    fun `a phone pick switches back to the phone and keeps the server's settings`() = runTest {
        setUpServer()
        choice.acceptNotice(choice.pickServerModel("gemma3:4b") as ServerPick.Notice)

        assertTrue(choice.pickPhoneModel(LlmModelCatalog.DEFAULT.id, ModelStatus.READY, EIGHT_GB))

        assertEquals(Translator.ON_DEVICE_ID, translation.activeTranslator().id)
        assertNull(choice.activeServerModel())
        assertEquals(listOf("qwen2.5:7b", "gemma3:4b"), choice.serverModels())
    }

    @Test
    fun `a phone pick the slot refuses leaves the server in use`() = runTest {
        setUpServer()
        choice.acceptNotice(choice.pickServerModel("gemma3:4b") as ServerPick.Notice)
        TargetLanguageStore(prefs).save(TargetLanguage.FRENCH)

        assertFalse(choice.pickPhoneModel(Constants.CAT_TRANSLATION_MODEL_ID, ModelStatus.READY, EIGHT_GB))

        assertEquals("gemma3:4b", choice.activeServerModel())
    }

    @Test
    fun `while a server model is picked, any language is set even with an English-only phone model`() = runTest {
        slots.pick(ModelType.LLM, Constants.CAT_TRANSLATION_MODEL_ID, ModelStatus.READY, EIGHT_GB)
        setUpServer()
        choice.acceptNotice(choice.pickServerModel("gemma3:4b") as ServerPick.Notice)

        assertEquals(LanguageSwitch.Done, slots.switchLanguage(TargetLanguage.FRENCH))

        assertEquals(TargetLanguage.FRENCH, slots.targetLanguage())
    }

    private fun setUpServer(listed: List<String> = listOf("qwen2.5:7b", "gemma3:4b"), model: String = "qwen2.5:7b") {
        store.saveAddress(ADDRESS)
        store.confirm(ADDRESS)
        store.saveServerModels(listed)
        store.saveModel(model)
    }

    private companion object {
        const val ADDRESS = "http://192.168.1.20:11434"
        const val EIGHT_GB = 8L * 1024 * 1024 * 1024
    }
}
