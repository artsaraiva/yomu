package com.yomu.app.translation

import android.content.SharedPreferences
import javax.crypto.SecretKey

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

    private fun readString(from: SharedPreferences, key: String): String =
        runCatching { from.getString(key, null) }.getOrNull().orEmpty()

    companion object {
        private const val PREF_ADDRESS = "server_address"
        private const val PREF_MODEL = "server_model"
        private const val PREF_CONFIRMED = "server_confirmed"
    }
}
