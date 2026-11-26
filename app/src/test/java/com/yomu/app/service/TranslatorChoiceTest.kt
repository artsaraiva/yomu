package com.yomu.app.service

import com.yomu.app.db.entities.ModelStatus
import com.yomu.app.db.entities.ModelType
import com.yomu.app.translation.AppDestination
import com.yomu.app.translation.LlmModelCatalog
import com.yomu.app.translation.MapSharedPreferences
import com.yomu.app.translation.NetworkTranslatorEntries
import com.yomu.app.translation.ServerSettingsStore
import com.yomu.app.translation.TargetLanguageStore
import com.yomu.app.translation.TranslationModelSelection
import com.yomu.app.translation.Translator
import com.yomu.app.translation.TranslatorHomeAction
import com.yomu.app.translation.TranslatorPick
import com.yomu.app.translation.TranslatorPickerEntry
import com.yomu.app.translation.TranslatorSettingsRow
import com.yomu.core.Constants
import com.yomu.core.TargetLanguage
import com.yomu.core.TranslationSlot
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

/** Choosing between the phone's models and the registered translators' entries in the model picker (#490, #534). */
class TranslatorChoiceTest {
    private val prefs = MapSharedPreferences()
    private val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    private val store = ServerSettingsStore(prefs, MapSharedPreferences()) { key }
    private val llama = Mockito.mock(LlamaTranslationBridge::class.java)
    private val third = Translator("third", "Third", { true }, lazyOf(Mockito.mock(TranslationSlot::class.java)))
    private val translation = TranslationModelSelection(
        llama, prefs, File("models"), translators = setOf(Translator.onDevice(llama), Translator.network(store, prefs, OkHttpClient()), third)
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
    fun `the picker lists each translator that offers entries, under its name`() {
        val groups = choice.pickerGroups()

        assertEquals(listOf(Translator.NETWORK_ID), groups.map { it.translatorId })
        assertEquals("Your server", groups.single().name)
        assertEquals(listOf("Set up your server"), groups.single().entries.map { it.name })
        assertEquals("the group of a translator added beside them", listOf("Fourth"), withFourth().pickerGroups().drop(1).map { it.name })
    }

    @Test
    fun `an entry that asks first changes nothing, and accepting it selects its translator`() {
        setUpServer()

        val confirm = choice.pick(Translator.NETWORK_ID, entry("gemma3:4b").pick)

        assertTrue(confirm is TranslatorPick.Confirm)
        assertEquals(Translator.ON_DEVICE_ID, translation.activeTranslator().id)
        assertEquals(TranslatorPick.Select, choice.pick(Translator.NETWORK_ID, (confirm as TranslatorPick.Confirm).accept))
        assertEquals("gemma3:4b", choice.activeServerModel())
        assertEquals(listOf(false, true), choice.pickerGroups().single().entries.map { it.checked })
    }

    @Test
    fun `an entry that opens a screen or is refused leaves the phone in use`() {
        assertEquals(TranslatorPick.Open(NetworkTranslatorEntries.PROVIDERS), choice.pick(Translator.NETWORK_ID, entry("Set up your server").pick))
        assertEquals(TranslatorPick.Refused, choice.pick(Translator.NETWORK_ID) { TranslatorPick.Refused })

        assertEquals(Translator.ON_DEVICE_ID, translation.activeTranslator().id)
    }

    @Test
    fun `a pick for a translator that isn't registered is refused`() {
        assertEquals(TranslatorPick.Refused, choice.pick("gone") { TranslatorPick.Select })

        assertEquals(Translator.ON_DEVICE_ID, translation.activeTranslator().id)
    }

    @Test
    fun `Settings lists the row of each translator that supplies one`() {
        assertEquals(listOf("Providers"), choice.settingsRows().map { it.title })
        assertEquals(listOf("Providers", "Fourth"), withFourth().settingsRows().map { it.title })
    }

    @Test
    fun `Home offers Set up server only while the server is chosen and not set up`() {
        assertNull("the phone is chosen", choice.homeAction())

        setUpServer()
        pickOnServer("gemma3:4b")
        assertNull("the server is chosen and set up", choice.homeAction())

        // Connect again, as Providers does, and the server no longer lists the chosen model.
        store.saveServerModels(listOf("llama3.1:8b"))
        store.saveModel("")
        assertEquals(TranslatorHomeAction("Set up server", AppDestination("settings/providers")), choice.homeAction())

        translation.selectOnDevice()
        assertNull("the phone is chosen again", choice.homeAction())
    }

    @Test
    fun `changing the server's address switches back to the phone until a server model is picked again`() {
        setUpServer()
        pickOnServer("gemma3:4b")

        store.saveAddress("http://192.168.1.21:11434")
        store.confirm("http://192.168.1.21:11434")
        store.saveServerModels(listOf("llama3.1:8b"))
        store.saveModel("llama3.1:8b")

        assertNull("set up again in Providers, but not picked", choice.activeServerModel())
        assertEquals(Translator.ON_DEVICE_ID, translation.activeTranslator().id)
        assertEquals(TranslatorPick.Select, choice.pick(Translator.NETWORK_ID, entry("llama3.1:8b").pick))
        assertEquals("llama3.1:8b", choice.activeServerModel())
    }

    @Test
    fun `saving the same address again keeps the server in use`() {
        setUpServer()
        pickOnServer("gemma3:4b")

        store.saveAddress(" $ADDRESS ")

        assertEquals("gemma3:4b", choice.activeServerModel())
    }

    @Test
    fun `a phone pick switches back to the phone and keeps the server's settings`() = runTest {
        setUpServer()
        pickOnServer("gemma3:4b")

        assertTrue(choice.pickPhoneModel(LlmModelCatalog.DEFAULT.id, ModelStatus.READY, EIGHT_GB))

        assertEquals(Translator.ON_DEVICE_ID, translation.activeTranslator().id)
        assertNull(choice.activeServerModel())
        assertEquals(listOf("qwen2.5:7b", "gemma3:4b"), choice.pickerGroups().single().entries.map { it.name })
    }

    @Test
    fun `a phone pick the slot refuses leaves the server in use`() = runTest {
        setUpServer()
        pickOnServer("gemma3:4b")
        TargetLanguageStore(prefs).save(TargetLanguage.FRENCH)

        assertFalse(choice.pickPhoneModel(Constants.CAT_TRANSLATION_MODEL_ID, ModelStatus.READY, EIGHT_GB))

        assertEquals("gemma3:4b", choice.activeServerModel())
    }

    @Test
    fun `while a server model is picked, any language is set even with an English-only phone model`() = runTest {
        slots.pick(ModelType.LLM, Constants.CAT_TRANSLATION_MODEL_ID, ModelStatus.READY, EIGHT_GB)
        setUpServer()
        pickOnServer("gemma3:4b")

        assertEquals(LanguageSwitch.Done, slots.switchLanguage(TargetLanguage.FRENCH))

        assertEquals(TargetLanguage.FRENCH, slots.targetLanguage())
    }

    @Test
    fun `a page names the translator that takes it, and the server model only for the network translator`() {
        assertEquals("nothing chosen", PageTranslator(Translator.ON_DEVICE_ID, null), choice.forPage())

        setUpServer()
        pickOnServer("gemma3:4b")
        assertEquals(PageTranslator(Translator.NETWORK_ID, "gemma3:4b"), choice.forPage())

        translation.selectTranslator(third)
        assertEquals(PageTranslator("third", null), choice.forPage())
    }

    @Test
    fun `deleting the phone model while a server model is picked keeps reading ready`() = runTest {
        setUpServer()
        pickOnServer("gemma3:4b")

        slots.delete(LlmModelCatalog.DEFAULT.id)

        assertNull(slots.selectedId(ModelType.LLM))
        assertTrue(slots.ready(READING_MODELS))
    }

    @Test
    fun `with no phone model, clearing the server's address makes reading not ready`() = runTest {
        setUpServer()
        pickOnServer("gemma3:4b")
        slots.delete(LlmModelCatalog.DEFAULT.id)

        store.saveAddress("http://192.168.1.21:11434")

        assertEquals(Translator.ON_DEVICE_ID, translation.activeTranslator().id)
        assertFalse(slots.ready(READING_MODELS))
    }

    @Test
    fun `any translator that can run lifts the phone model, not only the server`() = runTest {
        slots.delete(LlmModelCatalog.DEFAULT.id)
        assertFalse(slots.ready(READING_MODELS))

        translation.selectTranslator(third)

        assertTrue(slots.ready(READING_MODELS))
    }

    @Test
    fun `a phone pick with no phone model leaves the server in use until its download completes`() = runTest {
        setUpServer()
        pickOnServer("gemma3:4b")
        slots.delete(LlmModelCatalog.DEFAULT.id)

        assertTrue(choice.pickPhoneModel(LlmModelCatalog.DEFAULT.id, ModelStatus.AVAILABLE, EIGHT_GB))

        assertEquals(LlmModelCatalog.DEFAULT.id, slots.pendingId(ModelType.LLM))
        assertEquals("gemma3:4b", choice.activeServerModel())
        assertTrue(slots.ready(READING_MODELS))

        slots.commitPending(mapOf(LlmModelCatalog.DEFAULT.id to ModelStatus.READY))

        assertEquals(Translator.ON_DEVICE_ID, translation.activeTranslator().id)
        assertEquals(LlmModelCatalog.DEFAULT.id, slots.selectedId(ModelType.LLM))
    }

    @Test
    fun `a phone model downloaded from elsewhere leaves the server in use`() = runTest {
        setUpServer()
        pickOnServer("gemma3:4b")
        slots.delete(LlmModelCatalog.DEFAULT.id)

        slots.pick(ModelType.LLM, LlmModelCatalog.DEFAULT.id, ModelStatus.AVAILABLE, EIGHT_GB)
        slots.commitPending(mapOf(LlmModelCatalog.DEFAULT.id to ModelStatus.READY))

        assertEquals("gemma3:4b", choice.activeServerModel())
    }

    private fun entry(name: String): TranslatorPickerEntry = choice.pickerGroups().single().entries.first { it.name == name }

    /** Picks [model] in the server's group and accepts the notice when it is asked. */
    private fun pickOnServer(model: String) {
        val pick = choice.pick(Translator.NETWORK_ID, entry(model).pick)
        if (pick is TranslatorPick.Confirm) choice.pick(Translator.NETWORK_ID, pick.accept)
    }

    private fun withFourth(): TranslatorChoice {
        val fourth = Translator(
            "fourth", "Fourth", { true }, lazyOf(Mockito.mock(TranslationSlot::class.java)),
            pickerEntries = { active -> listOf(TranslatorPickerEntry("Fourth model", "Hosted", active) { TranslatorPick.Select }) },
            settingsRow = TranslatorSettingsRow("Fourth", { "Signed in" }, AppDestination("settings/fourth"))
        )
        val selection = TranslationModelSelection(
            llama, prefs, File("models"), translators = setOf(Translator.onDevice(llama), Translator.network(store, prefs, OkHttpClient()), fourth)
        )
        return TranslatorChoice(selection, store, slots)
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
        val READING_MODELS = mapOf(
            ModelManager.SLOT_DEFAULTS.getValue(ModelType.DETECTION) to ModelStatus.READY,
            ModelManager.SLOT_DEFAULTS.getValue(ModelType.OCR) to ModelStatus.READY
        )
    }
}
