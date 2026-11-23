package com.yomu.ml

import com.google.gson.JsonParser
import com.yomu.core.GenerationParams
import com.yomu.core.TranslationOutcome
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The network translator's request and streamed reply, against a local server (#486, ADR-0023). */
class NetworkTranslationSlotTest {
    private val server = MockWebServer()

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun translatePage_streamedReplyReportsEachLineOnceInOrder() = runTest {
        server.enqueue(chatStream("[1] Hel", "lo\n[2] Good", "bye\n"))
        val reported = mutableListOf<Pair<Int, String>>()

        val result = networkSlot(server.base()).translatePage(page(1 to "こんにちは", 2 to "さようなら")) { id, text ->
            reported += id to text
        }

        assertEquals(listOf(1 to "Hello", 2 to "Goodbye"), reported)
        assertEquals(mapOf(1 to "Hello", 2 to "Goodbye"), result.byId)
        assertEquals(TranslationOutcome.SUCCESS, result.outcome)
        assertEquals("/v1/chat/completions", server.takeRequest().path)
    }

    @Test
    fun translatePage_baseAddressAlreadyEndingInV1IsNotDoubled() = runTest {
        val expected = mapOf(
            "/v1" to "/v1/chat/completions",
            "/v1/" to "/v1/chat/completions",
            "/api/v1" to "/api/v1/chat/completions",
            "/api" to "/api/v1/chat/completions"
        )
        for ((basePath, requestPath) in expected) {
            server.enqueue(chatStream("[1] Hello\n"))

            networkSlot(server.url(basePath).toString()).translatePage(page(1 to "こんにちは"))

            assertEquals(basePath, requestPath, server.takeRequest().path)
        }
    }

    @Test
    fun translatePage_requestSendsThePagePromptModelAndOnlyTheServerSamplerSettings() = runTest {
        server.enqueue(chatStream("[1] Hello\n"))
        val generation = GenerationParams(temperature = 0.7f, topK = 20, topP = 0.8f, penaltyRepeat = 1.1f, penaltyPresent = 1.5f)

        networkSlot(server.base(), model = "gemma3:12b", generation = generation)
            .translatePage(page(1 to "こんにちは", 2 to "さようなら"))

        val body = JsonParser().parse(server.takeRequest().body.readUtf8()).asJsonObject
        assertEquals(setOf("model", "messages", "stream", "temperature", "top_p", "max_tokens"), body.keySet())
        assertEquals("gemma3:12b", body["model"].asString)
        assertEquals(0.7, body["temperature"].asDouble, 1e-6)
        assertEquals(0.8, body["top_p"].asDouble, 1e-6)
        assertEquals(4096, body["max_tokens"].asInt)
        assertTrue(body["stream"].asBoolean)
        val message = body.getAsJsonArray("messages").single().asJsonObject
        assertEquals("user", message["role"].asString)
        assertEquals(
            "Translate each Japanese line to English. Keep the [id] tag before each line. Panels are separated by ---.\n" +
                "[1] こんにちは\n[2] さようなら\n",
            message["content"].asString
        )
    }

    @Test
    fun translatePage_samplerValueOutsideItsBoundIsSentAsTheDefault() = runTest {
        server.enqueue(chatStream("[1] Hello\n"))

        networkSlot(server.base(), generation = GenerationParams(temperature = 5f, topP = Float.NaN))
            .translatePage(page(1 to "こんにちは"))

        val body = JsonParser().parse(server.takeRequest().body.readUtf8()).asJsonObject
        assertEquals(0.2, body["temperature"].asDouble, 1e-6)
        assertEquals(0.9, body["top_p"].asDouble, 1e-6)
    }
}
