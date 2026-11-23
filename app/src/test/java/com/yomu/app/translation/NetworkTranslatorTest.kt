package com.yomu.app.translation

import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

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
}
