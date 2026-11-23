package com.yomu.ml

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** What Connect asks the reader's server, and what it makes of the answer (#489, ADR-0023). */
class ServerModelListTest {
    private val server = MockWebServer()

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun fetch_serverListReturnsItsModelNamesInTheServerOrder() = runTest {
        server.enqueue(MockResponse().setBody("""{"object":"list","data":[{"id":"qwen2.5:7b"},{"id":"gemma3:4b"}]}"""))

        val result = ServerModelList(OkHttpClient()).fetch(server.url("/").toString(), apiKey = "")

        assertEquals(ServerModels.Listed(listOf("qwen2.5:7b", "gemma3:4b")), result)
        assertEquals("/v1/models", server.takeRequest().path)
    }

    @Test
    fun fetch_sendsTheKeyAsABearerTokenOnlyWhenOneIsSet() = runTest {
        repeat(2) { server.enqueue(MockResponse().setBody(LIST)) }
        val models = ServerModelList(OkHttpClient())

        models.fetch(server.url("/").toString(), apiKey = "sk-local-1")
        models.fetch(server.url("/").toString(), apiKey = " ")

        assertEquals("Bearer sk-local-1", server.takeRequest().getHeader("Authorization"))
        assertNull(server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun fetch_serverWithNoListOrAnEmptyOneIsUnlisted() = runTest {
        server.enqueue(MockResponse().setResponseCode(404))
        server.enqueue(MockResponse().setBody("""{"object":"list","data":[]}"""))
        val models = ServerModelList(OkHttpClient())

        assertEquals(ServerModels.Unlisted, models.fetch(server.url("/").toString(), apiKey = ""))
        assertEquals(ServerModels.Unlisted, models.fetch(server.url("/").toString(), apiKey = ""))
    }

    @Test
    fun fetch_401Or403IsAKeyRejected() = runTest {
        server.enqueue(MockResponse().setResponseCode(401))
        server.enqueue(MockResponse().setResponseCode(403))
        val models = ServerModelList(OkHttpClient())

        repeat(2) {
            assertEquals(ServerModels.Failed(ConnectFailure.KEY_REJECTED), models.fetch(server.url("/").toString(), "sk-wrong"))
        }
    }

    private companion object {
        const val LIST = """{"data":[{"id":"qwen2.5:7b"}]}"""
    }
}
