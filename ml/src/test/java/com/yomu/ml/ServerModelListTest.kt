package com.yomu.ml

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
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
}
