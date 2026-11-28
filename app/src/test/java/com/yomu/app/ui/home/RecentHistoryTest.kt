package com.yomu.app.ui.home

import com.yomu.app.db.HistoryDao
import com.yomu.app.db.entities.TranslationEntity
import com.yomu.app.translation.MapSharedPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito

class RecentHistoryTest {
    private val prefs = MapSharedPreferences()
    private val state = MutableStateFlow(HomeUiState())
    private val page = TranslationEntity(
        sourceImagePath = "page.jpg", translatedText = "Hello", sourceLanguage = "ja", targetLanguage = "en",
        bubbleCount = 1, translationTimeMs = 10L
    )

    private fun history(translations: Flow<List<TranslationEntity>>, clearFails: Boolean = false): RecentHistory {
        val dao = Mockito.mock(HistoryDao::class.java) { invocation ->
            when (invocation.method.name) {
                "getAllTranslations" -> translations
                "clearHistory" -> if (clearFails) throw IllegalStateException("disk") else Unit
                else -> Mockito.RETURNS_DEFAULTS.answer(invocation)
            }
        }
        return RecentHistory(dao, prefs, state)
    }

    @Test
    fun `saved pages load, and the first one is remembered`() = runTest {
        history(flowOf(listOf(page))).load()

        assertEquals(listOf(page), state.value.translations)
        assertFalse(state.value.historyLoading)
        assertTrue(state.value.hasRead)
        assertTrue(prefs.getBoolean(RecentHistory.HAS_READ, false))
    }

    @Test
    fun `an empty history loads without marking anything read`() = runTest {
        history(flowOf(emptyList())).load()

        assertFalse(state.value.historyLoading)
        assertFalse(state.value.hasRead)
        assertFalse(prefs.getBoolean(RecentHistory.HAS_READ, false))
    }

    @Test
    fun `a history that can't be read is an error, not an endless load`() = runTest {
        history(flow { throw IllegalStateException("disk") }).load()

        assertTrue(state.value.historyError)
        assertFalse(state.value.historyLoading)
    }

    @Test
    fun `clearing confirms it, and a failed clear says so`() = runTest {
        history(flowOf(emptyList())).clear()
        assertEquals("Recent translations cleared", state.value.confirmation)
        assertFalse(state.value.clearError)

        history(flowOf(emptyList()), clearFails = true).clear()
        assertTrue(state.value.clearError)
    }
}
