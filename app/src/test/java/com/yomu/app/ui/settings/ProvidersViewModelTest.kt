package com.yomu.app.ui.settings

import com.yomu.app.translation.MapSharedPreferences
import com.yomu.app.translation.ServerSettingsStore
import com.yomu.ml.ServerModelList
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
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

    private fun viewModel() = ProvidersViewModel(store, ServerModelList(OkHttpClient()))

    private fun address(): String = server.url("/").toString()
}
