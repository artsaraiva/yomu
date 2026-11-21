package com.yomu.app.service

import com.yomu.app.db.HistoryDao
import com.yomu.app.db.TranslationSessionDao
import com.yomu.app.db.entities.TranslationEntity
import com.yomu.app.db.entities.TranslationSessionEntity
import com.yomu.app.translation.MapSharedPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class SessionManagerTest {

    private val sessions = FakeSessionDao()
    private val recent = FakeHistoryDao()
    private val saveToRecent = SaveToRecentStore(MapSharedPreferences())
    private val manager = SessionManager(sessions, recent, saveToRecent)

    @Test
    fun `with saving on, a finished page is saved to Recent`() = runTest {
        finishPage("[0] こんにちは → Hello", bubbles = 1)

        val session = sessions.rows.single()
        assertEquals(
            TranslationEntity(
                id = 1,
                sessionId = session.id,
                sourceImagePath = "",
                translatedText = "[0] こんにちは → Hello",
                sourceLanguage = "ja",
                targetLanguage = "en",
                bubbleCount = 1,
                translationTimeMs = 900,
                createdAt = 0
            ),
            recent.rows.single().copy(createdAt = 0)
        )
        assertEquals("manual", session.sourceApp)
        assertEquals(1, session.bubbleCount)
    }

    @Test
    fun `pages finished one after another are saved in the same session`() = runTest {
        finishPage("[0] こんにちは → Hello", bubbles = 1)
        finishPage("[0] はい → Yes\n[1] いいえ → No", bubbles = 2)

        val session = sessions.rows.single()
        assertEquals(listOf(session.id, session.id), recent.rows.map { it.sessionId })
        assertEquals(3, session.bubbleCount)
    }

    private suspend fun finishPage(text: String, bubbles: Int) = manager.savePage(
        sourceApp = "manual",
        sourceImagePath = "",
        translatedText = text,
        sourceLanguage = "ja",
        targetLanguage = "en",
        bubbleCount = bubbles,
        translationTimeMs = 900
    )
}

/** Keeps sessions in a list, with the queries' meaning: the newest active session is the active one. */
private class FakeSessionDao : TranslationSessionDao {
    val rows = mutableListOf<TranslationSessionEntity>()

    override suspend fun getActiveSession() = rows.filter { it.isActive }.maxByOrNull { it.lastTranslatedAt }

    override suspend fun insertSession(session: TranslationSessionEntity): Long {
        val id = rows.size + 1L
        rows += session.copy(id = id)
        return id
    }

    override suspend fun updateSessionTimestamp(id: Long, timestamp: Long) = change(id) { it.copy(lastTranslatedAt = timestamp) }

    override suspend fun incrementBubbleCount(id: Long, count: Int) = change(id) { it.copy(bubbleCount = it.bubbleCount + count) }

    override suspend fun closeSession(id: Long) = change(id) { it.copy(isActive = false) }

    override suspend fun closeStaleSessions(timeoutMs: Long) {
        val now = System.currentTimeMillis()
        rows.replaceAll { if (it.isActive && now - it.lastTranslatedAt > timeoutMs) it.copy(isActive = false) else it }
    }

    private fun change(id: Long, update: (TranslationSessionEntity) -> TranslationSessionEntity) =
        rows.replaceAll { if (it.id == id) update(it) else it }
}

/** Keeps Recent's pages in a list. */
private class FakeHistoryDao : HistoryDao {
    val rows = mutableListOf<TranslationEntity>()

    override fun getAllTranslations(): Flow<List<TranslationEntity>> = flowOf(rows.sortedByDescending { it.createdAt })

    override suspend fun getTranslationById(id: Long) = rows.firstOrNull { it.id == id }

    override suspend fun insertTranslation(translation: TranslationEntity): Long {
        val id = rows.size + 1L
        rows += translation.copy(id = id)
        return id
    }

    override suspend fun deleteTranslation(translation: TranslationEntity) {
        rows.removeAll { it.id == translation.id }
    }

    override suspend fun clearHistory() = rows.clear()

    override suspend fun getTranslationCount() = rows.size

    override fun getTranslationCountSince(startTimeMs: Long): Flow<Int> = flowOf(rows.count { it.createdAt >= startTimeMs })
}
