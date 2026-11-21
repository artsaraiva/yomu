package com.yomu.app.service

import android.content.SharedPreferences
import javax.inject.Inject

/** The reader's Save pages to Recent switch (#454): on unless turned off, and on again for a wrongly typed stored value. */
class SaveToRecentStore @Inject constructor(private val prefs: SharedPreferences) {

    fun load(): Boolean = runCatching { prefs.getBoolean(KEY, DEFAULT) }.getOrDefault(DEFAULT)

    fun save(enabled: Boolean) = prefs.edit().putBoolean(KEY, enabled).apply()

    companion object {
        const val KEY = "save_to_recent_enabled"
        const val DEFAULT = true
    }
}
