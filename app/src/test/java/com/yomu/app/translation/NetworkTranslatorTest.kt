package com.yomu.app.translation

import com.google.gson.JsonParser
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
}
