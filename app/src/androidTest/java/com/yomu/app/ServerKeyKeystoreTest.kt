package com.yomu.app

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yomu.app.translation.ApiKeyState
import com.yomu.app.translation.ServerSettingsStore
import com.yomu.app.translation.androidKeystoreKey
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import java.security.KeyStore

/**
 * The server key sealed under a real Android Keystore key, which the JVM unit tests can only stand in
 * for. Uses its own alias and files, so the app's saved key is left alone.
 */
@RunWith(AndroidJUnit4::class)
class ServerKeyKeystoreTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext

    @After
    fun cleanUp() {
        context.deleteSharedPreferences(PREFS)
        context.deleteSharedPreferences(KEY_PREFS)
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(ALIAS)
    }

    @Test
    fun keySavedUnderTheKeystoreReadsBackAfterARestartAndIsNotStoredInTheClear() {
        assertEquals(ApiKeyState.SAVED, store().saveApiKey(API_KEY))

        val afterRestart = store()

        assertEquals(API_KEY, afterRestart.apiKey())
        assertEquals(ApiKeyState.SAVED, afterRestart.apiKeyState())
        val stored = context.getSharedPreferences(KEY_PREFS, Context.MODE_PRIVATE).all.values.joinToString(" ")
        assertFalse(stored.contains(API_KEY))
    }

    private fun store() = ServerSettingsStore(
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE),
        context.getSharedPreferences(KEY_PREFS, Context.MODE_PRIVATE)
    ) { androidKeystoreKey(ALIAS) }

    private companion object {
        const val ALIAS = "yomu_server_key_test"
        const val PREFS = "server_key_test_prefs"
        const val KEY_PREFS = "server_key_test_key"
        const val API_KEY = "sk-test-0123456789abcdef"
    }
}
