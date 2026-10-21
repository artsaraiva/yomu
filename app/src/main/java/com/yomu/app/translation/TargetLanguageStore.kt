package com.yomu.app.translation

import android.content.SharedPreferences
import com.yomu.core.Constants
import com.yomu.core.TargetLanguage

/** The reader's target language (#337): English unless chosen, and English again for an unknown or wrongly typed stored value. */
class TargetLanguageStore(private val prefs: SharedPreferences) {

    fun load(): TargetLanguage {
        val stored = runCatching { prefs.getString(Constants.PREF_TARGET_LANGUAGE, null) }.getOrNull()
        return TargetLanguage.fromCode(stored) ?: TargetLanguage.ENGLISH
    }

    fun save(language: TargetLanguage) = prefs.edit().putString(Constants.PREF_TARGET_LANGUAGE, language.code).apply()
}
