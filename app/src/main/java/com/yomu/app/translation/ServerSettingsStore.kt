package com.yomu.app.translation

import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.yomu.ml.NetworkTranslatorConfig
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * The network translator's settings (ADR-0023): the server's address, the model picked on it, whether
 * Connect has confirmed that address, the models Connect listed, whether the reader accepted the
 * notice, and the reader's API key.
 *
 * The address, model, confirmation and list live in [prefs]. Changing the address clears the other
 * three, so a confirmation and a list always belong to the address they were made for.
 *
 * The key is sealed with AES-GCM under [secretKey], a key kept in the Android Keystore, and only the
 * IV and ciphertext reach [keyPrefs], a file of its own that backups leave out. When the Keystore
 * fails, the key is held in memory until the process ends and is never written unencrypted. Make one
 * store per process, so that held key is the one every caller sees.
 */
class ServerSettingsStore(
    private val prefs: SharedPreferences,
    private val keyPrefs: SharedPreferences,
    private val secretKey: () -> SecretKey
) {

    private class HeldKey(val value: String, val state: ApiKeyState)

    private class Sealed(val iv: String, val ciphertext: String)

    // The key as entered or opened this process: a saved key is opened once, and a key the Keystore
    // would not seal still works until the app stops. A failed open is not held, so the next read retries.
    private var heldKey: HeldKey? = null

    /** The server's address as the reader typed it, trimmed; empty when none is set. */
    fun address(): String = readString(prefs, PREF_ADDRESS)

    /** Saves [address]; a different address than the saved one clears the confirmation and the model. */
    @Synchronized
    fun saveAddress(address: String) {
        val trimmed = address.trim()
        if (trimmed == address()) return
        prefs.edit().putString(PREF_ADDRESS, trimmed).remove(PREF_CONFIRMED).remove(PREF_MODEL).remove(PREF_MODELS).apply()
    }

    /**
     * Records that Connect reached [address]. Refused, returning false, when [address] is not the saved
     * one: the reader changed it while Connect ran, and the new one has not been reached.
     */
    @Synchronized
    fun confirm(address: String): Boolean {
        // Locked with saveAddress: a change between this check and the write would confirm the new address.
        if (address.trim() != address() || address().isEmpty()) return false
        prefs.edit().putBoolean(PREF_CONFIRMED, true).apply()
        return true
    }

    /** Whether Connect has reached the saved address; cleared whenever the address changes. */
    fun isConfirmed(): Boolean = runCatching { prefs.getBoolean(PREF_CONFIRMED, false) }.getOrDefault(false)

    /** The model name picked from the server's list or typed; empty when none is set. */
    fun model(): String = readString(prefs, PREF_MODEL)

    /** Saves the model name, trimmed; it stays until the address changes. */
    fun saveModel(model: String) = prefs.edit().putString(PREF_MODEL, model.trim()).apply()

    /**
     * The model names the last Connect listed, in the server's order: empty for a server that keeps no
     * list, null when Connect hasn't listed this address. Read by the model picker, so opening it sends nothing.
     */
    fun serverModels(): List<String>? = readString(prefs, PREF_MODELS).takeIf { it.isNotEmpty() }
        ?.let { json -> runCatching { gson.fromJson<List<String>>(json, MODEL_LIST) }.getOrNull() }

    /** Keeps Connect's list of model names (names only) until the address changes. */
    fun saveServerModels(names: List<String>) = prefs.edit().putString(PREF_MODELS, gson.toJson(names)).apply()

    /** Whether the reader accepted the notice that a page's text is sent to their server; asked once. */
    fun noticeAccepted(): Boolean = runCatching { prefs.getBoolean(PREF_NOTICE_ACCEPTED, false) }.getOrDefault(false)

    fun acceptNotice() = prefs.edit().putBoolean(PREF_NOTICE_ACCEPTED, true).apply()

    /**
     * Whether the network translator has what it needs to take a page: an address, Connect's
     * confirmation of it, and a model. The key is optional. Read before every page, so it stays cheap.
     */
    fun isSetUp(): Boolean = address().isNotEmpty() && isConfirmed() && model().isNotEmpty()

    /**
     * Where a page may go: the address, key and model read together under the lock [saveAddress] takes,
     * so the key is never paired with an address Connect didn't confirm. Null unless the server is set up.
     */
    @Synchronized
    fun translatorConfig(): NetworkTranslatorConfig? =
        if (isSetUp()) NetworkTranslatorConfig(baseUrl = address(), apiKey = apiKey(), model = model()) else null

    /** The reader's API key; empty when none was entered or a saved one can't be opened. */
    @Synchronized
    fun apiKey(): String = currentKey().value

    /** Whether the key is saved, held for this session only, or absent; see [ApiKeyState]. */
    @Synchronized
    fun apiKeyState(): ApiKeyState = currentKey().state

    /**
     * Seals [key] and saves it, replacing any saved key; a blank key removes it. When the Keystore
     * fails, [key] is held for this process only and [ApiKeyState.NOT_SAVED] is returned, so a screen
     * can tell the reader.
     */
    @Synchronized
    fun saveApiKey(key: String): ApiKeyState {
        val trimmed = key.trim()
        val sealed = if (trimmed.isEmpty()) null else runCatching { seal(trimmed) }.getOrNull()
        // The older key goes even when the new one can't be sealed, or it would return after a restart.
        val editor = keyPrefs.edit().clear()
        sealed?.let { editor.putString(KEY_IV, it.iv).putString(KEY_CIPHERTEXT, it.ciphertext) }
        editor.apply()
        val state = when {
            trimmed.isEmpty() -> ApiKeyState.NONE
            sealed == null -> ApiKeyState.NOT_SAVED
            else -> ApiKeyState.SAVED
        }
        heldKey = HeldKey(trimmed, state)
        return state
    }

    private fun currentKey(): HeldKey = heldKey ?: openSavedKey()

    // An unopenable key stays stored and is not held: the Keystore may only be failing for now.
    private fun openSavedKey(): HeldKey {
        val iv = readString(keyPrefs, KEY_IV)
        val ciphertext = readString(keyPrefs, KEY_CIPHERTEXT)
        if (iv.isEmpty() || ciphertext.isEmpty()) return HeldKey("", ApiKeyState.NONE).also { heldKey = it }
        return runCatching { open(Sealed(iv, ciphertext)) }.fold(
            onSuccess = { key -> HeldKey(key, ApiKeyState.SAVED).also { heldKey = it } },
            onFailure = { HeldKey("", ApiKeyState.NOT_SAVED) }
        )
    }

    // The cipher picks the IV: a Keystore key refuses one handed in, and a fresh IV per seal is what GCM needs.
    private fun seal(text: String): Sealed {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, secretKey()) }
        val ciphertext = cipher.doFinal(text.toByteArray(Charsets.UTF_8))
        return Sealed(Base64.getEncoder().encodeToString(cipher.iv), Base64.getEncoder().encodeToString(ciphertext))
    }

    private fun open(sealed: Sealed): String {
        val iv = Base64.getDecoder().decode(sealed.iv)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        }
        return String(cipher.doFinal(Base64.getDecoder().decode(sealed.ciphertext)), Charsets.UTF_8)
    }

    private fun readString(from: SharedPreferences, key: String): String =
        runCatching { from.getString(key, null) }.getOrNull().orEmpty()

    companion object {
        /** The SharedPreferences file that holds the sealed key; the backup rules leave it out by this name. */
        const val KEY_FILE = "yomu_server_key"

        private const val PREF_ADDRESS = "server_address"
        private const val PREF_MODEL = "server_model"
        private const val PREF_CONFIRMED = "server_confirmed"
        private const val PREF_MODELS = "server_models"
        private const val PREF_NOTICE_ACCEPTED = "server_notice_accepted"
        private const val KEY_IV = "iv"
        private const val KEY_CIPHERTEXT = "ciphertext"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_BITS = 128
        private val gson = Gson()
        private val MODEL_LIST = object : TypeToken<List<String>>() {}.type
    }
}

/** What became of the reader's API key. */
enum class ApiKeyState {
    /** No key was entered. */
    NONE,

    /** Encrypted on the phone; it is still there after a restart. */
    SAVED,

    /**
     * The Keystore failed. A key entered this session works until the app stops and is then
     * forgotten; a key saved earlier can't be opened and reads back empty until the Keystore recovers.
     */
    NOT_SAVED
}
