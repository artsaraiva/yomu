package com.yomu.app.translation

import com.yomu.core.Constants
import com.yomu.core.TargetLanguage
import org.junit.Assert.assertEquals
import org.junit.Test

class TargetLanguageStoreTest {

    private val prefs = MapSharedPreferences()
    private val store = TargetLanguageStore(prefs)

    @Test
    fun `nothing stored loads English`() {
        assertEquals(TargetLanguage.ENGLISH, store.load())
    }

    @Test
    fun `every saved language loads back`() {
        TargetLanguage.entries.forEach { language ->
            store.save(language)

            assertEquals(language, TargetLanguageStore(prefs).load())
        }
    }

    @Test
    fun `the choice is kept under the existing target-language key`() {
        store.save(TargetLanguage.BRAZILIAN_PORTUGUESE)

        assertEquals("pt-BR", prefs.values[Constants.PREF_TARGET_LANGUAGE])
    }

    @Test
    fun `the English stored before languages existed loads as English`() {
        prefs.values[Constants.PREF_TARGET_LANGUAGE] = "en"

        assertEquals(TargetLanguage.ENGLISH, store.load())
    }

    @Test
    fun `an unknown or wrongly typed stored value loads English`() {
        prefs.values[Constants.PREF_TARGET_LANGUAGE] = "ja"
        assertEquals(TargetLanguage.ENGLISH, store.load())

        prefs.values[Constants.PREF_TARGET_LANGUAGE] = 3
        assertEquals(TargetLanguage.ENGLISH, store.load())
    }
}
