package com.yomu.app.ui.settings

import com.yomu.app.translation.MapSharedPreferences
import com.yomu.app.translation.ServerSettingsStore
import com.yomu.ml.ConnectFailure
import com.yomu.ml.ServerModelList
import java.security.KeyStoreException
import java.util.concurrent.TimeUnit
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** The Providers screen: Connect is the only thing that contacts the server (#489, ADR-0023). */
@OptIn(ExperimentalCoroutinesApi::class)
class ProvidersViewModelTest {
    private val server = MockWebServer()
    private val softwareKey: SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    private val store = ServerSettingsStore(MapSharedPreferences(), MapSharedPreferences()) { softwareKey }

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
        Dispatchers.resetMain()
    }

    @Test
    fun `typing an address and a key sends nothing`() {
        val viewModel = viewModel()

        viewModel.editAddress(address())
        viewModel.editApiKey("sk-local-1")

        assertEquals(address(), viewModel.state.value.address)
        assertEquals("sk-local-1", viewModel.state.value.apiKey)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `Connect sends the key only to the address on screen and lists the server's models`() = runTest {
        server.enqueue(MockResponse().setBody(TWO_MODELS))
        server.enqueue(MockResponse().setBody(TWO_MODELS))
        val viewModel = viewModel()
        viewModel.editAddress(address())
        viewModel.editApiKey("sk-local-1")

        viewModel.connect().join()

        val request = server.takeRequest()
        assertEquals("/v1/models", request.path)
        assertEquals("Bearer sk-local-1", request.getHeader("Authorization"))
        assertEquals(ServerConnection.Connected(listOf("qwen2.5:7b", "gemma3:4b")), viewModel.state.value.connection)
        assertTrue(store.isConfirmed())

        viewModel.editApiKey("")
        viewModel.connect().join()

        assertNull(server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun `picking a listed model stores it and the server is set up`() = runTest {
        server.enqueue(MockResponse().setBody(TWO_MODELS))
        val viewModel = connected()

        viewModel.pickModel("gemma3:4b")

        assertEquals("gemma3:4b", store.model())
        assertEquals("gemma3:4b", viewModel.state.value.model)
        assertTrue(store.isSetUp())
    }

    @Test
    fun `a server without a model list takes a typed model name`() = runTest {
        server.enqueue(MockResponse().setResponseCode(404))
        val viewModel = connected()
        assertEquals(ServerConnection.Connected(emptyList()), viewModel.state.value.connection)

        viewModel.pickModel("qwen2.5:7b-instruct")

        assertEquals("qwen2.5:7b-instruct", store.model())
        assertTrue(store.isSetUp())
    }

    @Test
    fun `editing the address after Connect clears the confirmation and the model`() = runTest {
        server.enqueue(MockResponse().setBody(TWO_MODELS))
        val viewModel = connected()
        viewModel.pickModel("qwen2.5:7b")

        viewModel.editAddress(address() + "x")

        assertFalse(store.isConfirmed())
        assertEquals("", store.model())
        assertEquals(ServerConnection.NotConnected, viewModel.state.value.connection)
        assertEquals("", viewModel.state.value.model)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `a failed Connect shows why and leaves the address unconfirmed`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401))
        val viewModel = viewModel()
        viewModel.editAddress(address())
        viewModel.editApiKey("sk-wrong")

        viewModel.connect().join()

        assertEquals(ServerConnection.Failed(ConnectFailure.KEY_REJECTED), viewModel.state.value.connection)
        assertFalse(store.isConfirmed())
    }

    @Test
    fun `a Connect still running when the address changes confirms nothing`() = runTest {
        server.enqueue(MockResponse().setBody(TWO_MODELS).setHeadersDelay(2, TimeUnit.SECONDS))
        val viewModel = viewModel()
        viewModel.editAddress(address())
        val connect = viewModel.connect()

        viewModel.editAddress("http://192.168.1.21:11434")
        connect.join()

        assertFalse(store.isConfirmed())
        assertEquals(ServerConnection.NotConnected, viewModel.state.value.connection)
    }

    @Test
    fun `a key the Keystore won't save still reaches the server and the screen says it wasn't saved`() = runTest {
        server.enqueue(MockResponse().setBody(TWO_MODELS))
        val noKeystore = ServerSettingsStore(MapSharedPreferences(), MapSharedPreferences()) { throw KeyStoreException("no Keystore") }
        val viewModel = ProvidersViewModel(noKeystore, ServerModelList(OkHttpClient()))
        viewModel.editAddress(address())
        viewModel.editApiKey("sk-local-1")
        assertFalse(viewModel.state.value.keyNotSaved)

        viewModel.connect().join()

        assertTrue(viewModel.state.value.keyNotSaved)
        assertEquals("Bearer sk-local-1", server.takeRequest().getHeader("Authorization"))
        assertEquals("sk-local-1", noKeystore.apiKey())
    }

    @Test
    fun `reopening the screen shows the confirmed server and its model and sends nothing`() {
        val noKeystore = ServerSettingsStore(MapSharedPreferences(), MapSharedPreferences()) { throw KeyStoreException("no Keystore") }
        noKeystore.saveAddress("http://192.168.1.20:11434")
        noKeystore.confirm("http://192.168.1.20:11434")
        noKeystore.saveModel("qwen2.5:7b")
        noKeystore.saveApiKey("sk-local-1")

        val state = ProvidersViewModel(noKeystore, ServerModelList(OkHttpClient())).state.value

        assertEquals(
            ProvidersUiState(
                address = "http://192.168.1.20:11434",
                apiKey = "sk-local-1",
                model = "qwen2.5:7b",
                connection = ServerConnection.Connected(models = null),
                keyNotSaved = true
            ),
            state
        )
        assertEquals(0, server.requestCount)
    }

    private suspend fun connected(): ProvidersViewModel = viewModel().apply {
        editAddress(address())
        connect().join()
    }

    private fun viewModel() = ProvidersViewModel(store, ServerModelList(OkHttpClient()))

    private fun address(): String = server.url("/").toString()

    private companion object {
        const val TWO_MODELS = """{"object":"list","data":[{"id":"qwen2.5:7b"},{"id":"gemma3:4b"}]}"""
    }
}
