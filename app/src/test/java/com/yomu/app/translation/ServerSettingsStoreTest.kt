package com.yomu.app.translation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.KeyStoreException
import java.util.Base64
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

class ServerSettingsStoreTest {

    private val prefs = MapSharedPreferences()
    private val keyPrefs = MapSharedPreferences()
    private val softwareKey: SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    private val failingKeystore: () -> SecretKey = { throw KeyStoreException("no Keystore") }
    private val store = storeOverSamePrefs()

    @Test
    fun `changing the address clears the confirmation and the model`() {
        store.saveAddress("http://192.168.1.20:8080")
        store.confirm("http://192.168.1.20:8080")
        store.saveModel("qwen2.5:7b")

        store.saveAddress("http://192.168.1.21:8080")

        assertEquals("http://192.168.1.21:8080", store.address())
        assertFalse(store.isConfirmed())
        assertEquals("", store.model())
    }

    @Test
    fun `saving the same address again keeps the confirmation and the model`() {
        store.saveAddress("http://192.168.1.20:8080")
        store.confirm("http://192.168.1.20:8080")
        store.saveModel("qwen2.5:7b")

        store.saveAddress(" http://192.168.1.20:8080 ")

        assertTrue(store.isConfirmed())
        assertEquals("qwen2.5:7b", store.model())
    }

    @Test
    fun `the server is set up only once address, confirmation and model are all present`() {
        assertFalse(store.isSetUp())

        store.saveAddress("http://192.168.1.20:8080")
        assertFalse(store.isSetUp())

        assertTrue(store.confirm("http://192.168.1.20:8080"))
        assertFalse(store.isSetUp())

        store.saveModel("qwen2.5:7b")
        assertTrue(store.isSetUp())
    }

    @Test
    fun `the translator gets the address, key and model only while the server is set up`() {
        store.saveAddress("http://192.168.1.20:8080")
        store.saveModel("qwen2.5:7b")
        store.saveApiKey("sk-local-1")
        assertNull(store.translatorConfig())

        store.confirm("http://192.168.1.20:8080")
        store.saveModel("qwen2.5:7b")
        val config = store.translatorConfig()

        assertEquals("http://192.168.1.20:8080", config?.baseUrl)
        assertEquals("sk-local-1", config?.apiKey)
        assertEquals("qwen2.5:7b", config?.model)
        store.saveAddress("http://192.168.1.21:8080")
        assertNull(store.translatorConfig())
    }

    @Test
    fun `Connect's model list is kept after a restart until the address changes`() {
        store.saveAddress("http://192.168.1.20:8080")
        assertNull("no Connect yet", store.serverModels())

        store.saveServerModels(listOf("qwen2.5:7b", "llama3.1:8b-instruct \"q4\""))
        store.saveAddress("http://192.168.1.20:8080")

        assertEquals(listOf("qwen2.5:7b", "llama3.1:8b-instruct \"q4\""), storeOverSamePrefs().serverModels())
        store.saveAddress("http://192.168.1.21:8080")
        assertNull(store.serverModels())
    }

    @Test
    fun `a server with no list keeps an empty list, not none`() {
        store.saveAddress("http://192.168.1.20:8080")

        store.saveServerModels(emptyList())

        assertEquals(emptyList<String>(), store.serverModels())
    }

    @Test
    fun `the notice is accepted once and stays accepted after a restart`() {
        assertFalse(store.noticeAccepted())

        store.acceptNotice()

        assertTrue(storeOverSamePrefs().noticeAccepted())
    }

    @Test
    fun `a model without a confirmation is not set up`() {
        store.saveAddress("http://192.168.1.20:8080")
        store.saveModel("qwen2.5:7b")

        assertFalse(store.isSetUp())
    }

    @Test
    fun `a confirmation for an address that is no longer the saved one is refused`() {
        store.saveAddress("http://192.168.1.21:8080")

        assertFalse(store.confirm("http://192.168.1.20:8080"))
        assertFalse(store.isConfirmed())
    }

    @Test
    fun `no key entered reads back empty`() {
        assertEquals("", store.apiKey())
        assertEquals(ApiKeyState.NONE, store.apiKeyState())
    }

    @Test
    fun `a saved key reads back the same after a restart, and the stored bytes do not contain it`() {
        assertEquals(ApiKeyState.SAVED, store.saveApiKey(KEY))

        val afterRestart = storeOverSamePrefs()

        assertEquals(KEY, afterRestart.apiKey())
        assertEquals(ApiKeyState.SAVED, afterRestart.apiKeyState())
        assertTrue(keyPrefs.values.isNotEmpty())
        assertFalse(storedText().contains(KEY))
        assertFalse(storedText().contains(Base64.getEncoder().encodeToString(KEY.toByteArray())))
    }

    @Test
    fun `the key goes to its own file and leaves the ordinary preferences untouched`() {
        store.saveAddress("http://192.168.1.20:8080")
        val ordinaryBefore = prefs.values.toMap()

        store.saveApiKey(KEY)

        assertEquals(ordinaryBefore, prefs.values)
        assertTrue(keyPrefs.values.isNotEmpty())
    }

    @Test
    fun `saving a blank key removes the saved one`() {
        store.saveApiKey(KEY)

        assertEquals(ApiKeyState.NONE, store.saveApiKey("  "))

        assertTrue(keyPrefs.values.isEmpty())
        assertEquals("", storeOverSamePrefs().apiKey())
    }

    @Test
    fun `with a failing Keystore the key is readable this session, nothing is written, and it is reported not saved`() {
        val store = storeOverSamePrefs(failingKeystore)

        assertEquals(ApiKeyState.NOT_SAVED, store.saveApiKey(KEY))

        assertEquals(KEY, store.apiKey())
        assertEquals(ApiKeyState.NOT_SAVED, store.apiKeyState())
        assertTrue(keyPrefs.values.isEmpty())
        assertTrue(prefs.values.isEmpty())
    }

    @Test
    fun `a key the Keystore could not save does not bring the older saved key back after a restart`() {
        store.saveApiKey("sk-older")

        storeOverSamePrefs(failingKeystore).saveApiKey(KEY)

        assertEquals("", storeOverSamePrefs().apiKey())
    }

    @Test
    fun `a key saved this session is still read after the Keystore starts failing`() {
        var keystoreWorks = true
        val store = storeOverSamePrefs { if (keystoreWorks) softwareKey else failingKeystore() }
        store.saveApiKey(KEY)

        keystoreWorks = false

        assertEquals(KEY, store.apiKey())
        assertEquals(ApiKeyState.SAVED, store.apiKeyState())
    }

    @Test
    fun `a saved key the Keystore cannot open reads back empty, is reported not saved, and stays stored`() {
        store.saveApiKey(KEY)
        val stored = keyPrefs.values.toMap()

        val afterRestart = storeOverSamePrefs(failingKeystore)

        assertEquals("", afterRestart.apiKey())
        assertEquals(ApiKeyState.NOT_SAVED, afterRestart.apiKeyState())
        assertEquals(stored, keyPrefs.values)
    }

    @Test
    fun `a saved key the Keystore could not open is read once the Keystore recovers`() {
        store.saveApiKey(KEY)
        var keystoreWorks = false
        val afterRestart = storeOverSamePrefs { if (keystoreWorks) softwareKey else failingKeystore() }
        assertEquals("", afterRestart.apiKey())

        keystoreWorks = true

        assertEquals(KEY, afterRestart.apiKey())
        assertEquals(ApiKeyState.SAVED, afterRestart.apiKeyState())
    }

    /** A store as the next process would build it: same files, nothing held in memory. */
    private fun storeOverSamePrefs(keystore: () -> SecretKey = { softwareKey }) =
        ServerSettingsStore(prefs = prefs, keyPrefs = keyPrefs, secretKey = keystore)

    private fun storedText(): String = (prefs.values.values + keyPrefs.values.values).joinToString(" ")

    private companion object {
        const val KEY = "sk-test-0123456789abcdef"
    }
}
