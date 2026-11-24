package com.yomu.app.translation

import com.yomu.core.ERROR_SERVER_UNREACHABLE
import com.yomu.core.TranslatableBubble
import com.yomu.core.TranslatablePage
import com.yomu.ml.LlamaTranslationBridge
import javax.crypto.KeyGenerator
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode

/**
 * A failed server page fails visibly: the phone never translates it instead (ADR-0023). Robolectric only
 * because the network slot logs its failure; Conscrypt off, as in ServerKeyBackupRulesTest.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@ConscryptMode(ConscryptMode.Mode.OFF)
class NetworkPageFailureTest {

    @Test
    fun `a page the server fails keeps its cause and is not translated on the phone instead`() = runTest {
        val prefs = MapSharedPreferences()
        val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        val store = ServerSettingsStore(prefs, MapSharedPreferences()) { key }
        val network = Translator.network(store, prefs, OkHttpClient())
        val stopped = MockWebServer().apply { start() }
        val address = stopped.url("/").toString()
        stopped.shutdown()
        store.saveAddress(address)
        store.confirm(address)
        store.saveModel("qwen2.5:7b")
        val llama = Mockito.mock(LlamaTranslationBridge::class.java)
        val selection = TranslationModelSelection(llama, prefs, translators = setOf(Translator.onDevice(llama), network))
        selection.selectTranslator(network)

        val result = selection.current().translatePage(TranslatablePage(listOf(listOf(TranslatableBubble(1, "こんにちは"))))) { _, _ -> }

        assertEquals(ERROR_SERVER_UNREACHABLE, result.errorCode)
        Mockito.verifyNoInteractions(llama)
    }
}
