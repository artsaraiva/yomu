package com.yomu.app.translation

import com.google.gson.JsonParser
import com.yomu.core.ERROR_MODEL_MISSING
import com.yomu.core.ERROR_SERVER_FAILED
import com.yomu.core.ERROR_SERVER_HTTPS_REQUIRED
import com.yomu.core.ERROR_SERVER_KEY_REJECTED
import com.yomu.core.ERROR_SERVER_MODEL_NOT_FOUND
import com.yomu.core.ERROR_SERVER_TIMEOUT
import com.yomu.core.ERROR_SERVER_UNREACHABLE
import com.yomu.core.GenerationBound
import com.yomu.core.TranslatableBubble
import com.yomu.core.TranslatablePage
import com.yomu.core.TranslationOutcome
import com.yomu.ml.LlamaTranslationBridge
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito

/** The network translator as registered: "Your server", built from the stored settings (#489, ADR-0023). */
class NetworkTranslatorTest {
    private val prefs = MapSharedPreferences()
    private val softwareKey: SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    private val store = ServerSettingsStore(prefs, MapSharedPreferences()) { softwareKey }
    private val translator = Translator.network(store, prefs, OkHttpClient())

    @Test
    fun `it is shown as Your server and can run only while the server is set up`() {
        assertEquals("Your server", translator.displayName)
        assertFalse(translator.canRun())

        store.saveAddress("http://192.168.1.20:11434")
        store.confirm("http://192.168.1.20:11434")
        assertFalse(translator.canRun())

        store.saveModel("qwen2.5:7b")
        assertTrue(translator.canRun())

        store.saveAddress("http://192.168.1.21:11434")
        assertFalse(translator.canRun())
    }

    @Test
    fun `its slot sends the page to the stored server with the key, the model and the reader's sampler`() = runTest {
        val server = MockWebServer()
        server.enqueue(MockResponse().setBody("data: {\"choices\":[{\"delta\":{\"content\":\"[1] Hello\\n\"}}]}\n\ndata: [DONE]\n\n"))
        val address = server.url("/").toString()
        store.saveAddress(address)
        store.confirm(address)
        store.saveModel("qwen2.5:7b")
        store.saveApiKey("sk-local-1")
        GenerationProfileStore(prefs).save(GenerationBound.TEMPERATURE, 0.3f)
        GenerationProfileStore(prefs).save(GenerationBound.TOP_P, 0.85f)

        val result = translator.slot().translatePage(TranslatablePage(listOf(listOf(TranslatableBubble(1, "こんにちは"))))) { _, _ -> }

        val request = server.takeRequest()
        val body = JsonParser.parseString(request.body.readUtf8()).asJsonObject
        assertEquals("/v1/chat/completions", request.path)
        assertEquals("Bearer sk-local-1", request.getHeader("Authorization"))
        assertEquals("qwen2.5:7b", body.get("model").asString)
        assertEquals(0.3f, body.get("temperature").asFloat, 0.001f)
        assertEquals(0.85f, body.get("top_p").asFloat, 0.001f)
        assertEquals(mapOf(1 to "Hello"), result.byId)
        server.shutdown()
    }

    @Test
    fun `its slot sends nothing while the server isn't set up, even when asked straight`() = runTest {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(200))
        val address = server.url("/").toString()
        store.saveAddress(address)
        store.saveModel("qwen2.5:7b")
        store.saveApiKey("sk-local-1")

        val result = translator.slot().translatePage(TranslatablePage(listOf(listOf(TranslatableBubble(1, "こんにちは"))))) { _, _ -> }

        assertEquals(TranslationOutcome.ERROR, result.outcome)
        assertEquals(0, server.requestCount)
        server.shutdown()
    }

    @Test
    fun `registered beside the phone, it takes pages only once chosen and while the server is set up`() {
        val llama = Mockito.mock(LlamaTranslationBridge::class.java)
        val selection = TranslationModelSelection(llama, prefs, translators = setOf(Translator.onDevice(llama), translator))
        store.saveAddress("http://192.168.1.20:11434")
        store.confirm("http://192.168.1.20:11434")
        store.saveModel("qwen2.5:7b")
        assertSame("nothing chosen", llama, selection.current())

        selection.selectTranslator(translator)
        assertSame(translator.slot(), selection.current())

        store.saveAddress("http://192.168.1.21:11434")
        assertSame("chosen but not set up", llama, selection.current())
    }

    @Test
    fun `it names each server failure, points to a phone model on Home, and opens Providers`() {
        val expected = mapOf(
            ERROR_SERVER_UNREACHABLE to "Can't reach your server",
            ERROR_SERVER_KEY_REJECTED to "Your server rejected the key",
            ERROR_SERVER_MODEL_NOT_FOUND to "Your server doesn't have this model",
            ERROR_SERVER_TIMEOUT to "Your server took too long",
            ERROR_SERVER_HTTPS_REQUIRED to "Your server's address needs https://",
            ERROR_SERVER_FAILED to "Your server couldn't translate the page"
        )

        for ((code, cause) in expected) {
            val message = translator.readerMessage(code)
            assertEquals(code, "$cause \u2014 or pick a phone model in the model picker on Home", message?.text)
            assertEquals(code, ReaderAction("Tap to open Providers", NetworkTranslatorEntries.PROVIDERS), message?.action)
        }
        assertNull("not one of its codes", translator.readerMessage(ERROR_MODEL_MISSING))
    }

    @Test
    fun `until the server is set up, its one picker entry opens Providers`() {
        val entry = translator.pickerEntries(active = false).single()

        assertEquals("Set up your server", entry.name)
        assertEquals("A server you run, or a hosted API you have a key for", entry.subtitle)
        assertFalse(entry.checked)
        assertEquals(TranslatorPick.Open(NetworkTranslatorEntries.PROVIDERS), entry.pick())
    }

    @Test
    fun `once set up, it offers each listed server model, checked while it takes the page`() {
        setUpServer()

        val entries = translator.pickerEntries(active = true)

        assertEquals(listOf("qwen2.5:7b", "gemma3:4b"), entries.map { it.name })
        assertEquals(listOf(null, null), entries.map { it.subtitle })
        assertEquals(listOf(true, false), entries.map { it.checked })
        assertEquals("not taking pages", listOf(false, false), translator.pickerEntries(active = false).map { it.checked })
    }

    @Test
    fun `a server that keeps no list offers its typed model name`() {
        setUpServer(listed = emptyList(), model = "qwen2.5:7b-instruct")

        assertEquals(listOf("qwen2.5:7b-instruct"), translator.pickerEntries(active = false).map { it.name })
    }

    @Test
    fun `the first pick asks first, and accepting it stores the model and selects the translator`() {
        setUpServer()
        val confirm = entry("gemma3:4b").pick() as TranslatorPick.Confirm

        assertEquals("Translate on your server?", confirm.title)
        assertTrue(confirm.message, confirm.message.contains("sent to $ADDRESS"))
        assertEquals("Send to my server", confirm.confirmLabel)
        assertEquals("nothing changes until accepted", "qwen2.5:7b", store.model())
        assertFalse(store.noticeAccepted())

        assertEquals(TranslatorPick.Select, confirm.accept())
        assertEquals("gemma3:4b", store.model())
        assertTrue(store.noticeAccepted())
    }

    @Test
    fun `once the notice is accepted, a pick stores its model and selects the translator at once`() {
        setUpServer()
        store.acceptNotice()

        assertEquals(TranslatorPick.Select, entry("gemma3:4b").pick())

        assertEquals("gemma3:4b", store.model())
    }

    @Test
    fun `a notice for an address the reader has since changed is refused`() {
        setUpServer()
        val confirm = entry("gemma3:4b").pick() as TranslatorPick.Confirm
        store.saveAddress("http://192.168.1.21:11434")

        assertEquals(TranslatorPick.Refused, confirm.accept())

        assertFalse(store.noticeAccepted())
    }

    @Test
    fun `a model the server no longer lists is refused`() {
        setUpServer()
        store.acceptNotice()
        val gemma = entry("gemma3:4b")
        store.saveServerModels(listOf("qwen2.5:7b"))

        assertEquals(TranslatorPick.Refused, gemma.pick())
        assertEquals("qwen2.5:7b", store.model())
    }

    @Test
    fun `its Settings row opens Providers and says whether the server is set up`() {
        val row = translator.settingsRow

        assertEquals("Providers", row?.title)
        assertEquals(NetworkTranslatorEntries.PROVIDERS, row?.destination)
        assertEquals("A server you run, or a hosted API you have a key for.", row?.summary?.invoke())

        setUpServer()
        assertEquals("Connected to $ADDRESS", row?.summary?.invoke())
    }

    private fun entry(model: String) = translator.pickerEntries(active = false).first { it.name == model }

    private fun setUpServer(listed: List<String> = listOf("qwen2.5:7b", "gemma3:4b"), model: String = "qwen2.5:7b") {
        store.saveAddress(ADDRESS)
        store.confirm(ADDRESS)
        store.saveServerModels(listed)
        store.saveModel(model)
    }

    private companion object {
        const val ADDRESS = "http://192.168.1.20:11434"
    }
}
