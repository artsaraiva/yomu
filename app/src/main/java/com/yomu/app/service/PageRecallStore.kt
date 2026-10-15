package com.yomu.app.service

import android.content.SharedPreferences

/** The reader's page recall switch (#351): on unless turned off, and on again for a wrongly typed stored value. */
class PageRecallStore(private val prefs: SharedPreferences) {

    fun load(): Boolean = runCatching { prefs.getBoolean(KEY, DEFAULT) }.getOrDefault(DEFAULT)

    fun save(enabled: Boolean) = prefs.edit().putBoolean(KEY, enabled).apply()

    companion object {
        const val KEY = "page_recall_enabled"
        const val DEFAULT = true
    }
}
