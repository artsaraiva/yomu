package com.yomu.app.translation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

class ServerSettingsStoreTest {

    private val prefs = MapSharedPreferences()
    private val keyPrefs = MapSharedPreferences()
    private val softwareKey: SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    private val store = ServerSettingsStore(prefs, keyPrefs) { softwareKey }

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

        store.confirm("http://192.168.1.20:8080")
        assertFalse(store.isSetUp())

        store.saveModel("qwen2.5:7b")
        assertTrue(store.isSetUp())
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

        val afterRestart = ServerSettingsStore(prefs, keyPrefs) { softwareKey }

        assertEquals(KEY, afterRestart.apiKey())
        assertEquals(ApiKeyState.SAVED, afterRestart.apiKeyState())
        assertTrue(keyPrefs.values.isNotEmpty())
        assertFalse(storedText().contains(KEY))
        assertFalse(storedText().contains(Base64.getEncoder().encodeToString(KEY.toByteArray())))
    }

    @Test
    fun `the key is kept out of the ordinary preferences`() {
        store.saveAddress("http://192.168.1.20:8080")
        store.saveApiKey(KEY)

        assertEquals(setOf("server_address"), prefs.values.keys)
    }

    @Test
    fun `saving a blank key removes the saved one`() {
        store.saveApiKey(KEY)

        assertEquals(ApiKeyState.NONE, store.saveApiKey("  "))

        assertTrue(keyPrefs.values.isEmpty())
        assertEquals("", ServerSettingsStore(prefs, keyPrefs) { softwareKey }.apiKey())
    }

    private fun storedText(): String = (prefs.values.values + keyPrefs.values.values).joinToString(" ")

    private companion object {
        const val KEY = "sk-test-0123456789abcdef"
    }
}
