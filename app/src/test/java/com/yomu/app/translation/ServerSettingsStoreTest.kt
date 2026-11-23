package com.yomu.app.translation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
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
}
