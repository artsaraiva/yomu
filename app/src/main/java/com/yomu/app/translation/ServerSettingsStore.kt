package com.yomu.app.translation

import android.content.SharedPreferences
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * The network translator's settings (ADR-0023): the server's address, the model picked on it, whether
 * Connect has confirmed that address, and the reader's API key.
 *
 * The address, model and confirmation live in [prefs]. Changing the address clears the other two, so
 * a confirmation always belongs to the address it was made for.
 */
class ServerSettingsStore(
    private val prefs: SharedPreferences,
    private val keyPrefs: SharedPreferences,
    private val secretKey: () -> SecretKey
) {

    /** The server's address as the reader typed it, trimmed; empty when none is set. */
    fun address(): String = readString(prefs, PREF_ADDRESS)

    /** Saves [address]; a different address than the saved one clears the confirmation and the model. */
    fun saveAddress(address: String) {
        val trimmed = address.trim()
        if (trimmed == address()) return
        prefs.edit().putString(PREF_ADDRESS, trimmed).remove(PREF_CONFIRMED).remove(PREF_MODEL).apply()
    }

    /**
     * Records that Connect reached [address]. Refused, returning false, when [address] is not the saved
     * one: the reader changed it while Connect ran, and the new one has not been reached.
     */
    fun confirm(address: String): Boolean {
        if (address.trim() != address() || address().isEmpty()) return false
        prefs.edit().putBoolean(PREF_CONFIRMED, true).apply()
        return true
    }

    fun isConfirmed(): Boolean = runCatching { prefs.getBoolean(PREF_CONFIRMED, false) }.getOrDefault(false)

    /** The model name picked from the server's list or typed; empty when none is set. */
    fun model(): String = readString(prefs, PREF_MODEL)

    fun saveModel(model: String) = prefs.edit().putString(PREF_MODEL, model.trim()).apply()

    /**
     * Whether the network translator has what it needs to take a page: an address, Connect's
     * confirmation of it, and a model. The key is optional. Read before every page, so it stays cheap.
     */
    fun isSetUp(): Boolean = address().isNotEmpty() && isConfirmed() && model().isNotEmpty()

    /** The reader's API key; empty when none was entered. */
    fun apiKey(): String {
        val iv = readString(keyPrefs, KEY_IV)
        val ciphertext = readString(keyPrefs, KEY_CIPHERTEXT)
        if (iv.isEmpty() || ciphertext.isEmpty()) return ""
        return open(iv, ciphertext)
    }

    fun apiKeyState(): ApiKeyState = if (apiKey().isEmpty()) ApiKeyState.NONE else ApiKeyState.SAVED

    /** Seals [key] and saves it; a blank key removes the saved one. */
    fun saveApiKey(key: String): ApiKeyState {
        val trimmed = key.trim()
        val editor = keyPrefs.edit().clear()
        if (trimmed.isEmpty()) {
            editor.apply()
            return ApiKeyState.NONE
        }
        val (iv, ciphertext) = seal(trimmed)
        editor.putString(KEY_IV, iv).putString(KEY_CIPHERTEXT, ciphertext).apply()
        return ApiKeyState.SAVED
    }

    private fun seal(text: String): Pair<String, String> {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, secretKey()) }
        val ciphertext = cipher.doFinal(text.toByteArray(Charsets.UTF_8))
        return base64.encodeToString(cipher.iv) to base64.encodeToString(ciphertext)
    }

    private fun open(iv: String, ciphertext: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(GCM_TAG_BITS, Base64.getDecoder().decode(iv)))
        }
        return String(cipher.doFinal(Base64.getDecoder().decode(ciphertext)), Charsets.UTF_8)
    }

    private fun readString(from: SharedPreferences, key: String): String =
        runCatching { from.getString(key, null) }.getOrNull().orEmpty()

    companion object {
        private const val PREF_ADDRESS = "server_address"
        private const val PREF_MODEL = "server_model"
        private const val PREF_CONFIRMED = "server_confirmed"
        private const val KEY_IV = "iv"
        private const val KEY_CIPHERTEXT = "ciphertext"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_BITS = 128
        private val base64 = Base64.getEncoder()
    }
}

/** What became of the reader's API key. */
enum class ApiKeyState {
    /** No key was entered. */
    NONE,

    /** Encrypted on the phone; it is still there after a restart. */
    SAVED
}
