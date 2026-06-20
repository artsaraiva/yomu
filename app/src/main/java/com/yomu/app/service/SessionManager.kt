package com.yomu.app.service

import com.yomu.app.db.HistoryDao
import com.yomu.app.db.TranslationSessionDao
import com.yomu.app.db.entities.TranslationEntity
import com.yomu.app.db.entities.TranslationSessionEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionManager @Inject constructor(
    private val sessionDao: TranslationSessionDao,
    private val historyDao: HistoryDao
) {
    companion object {
        private const val SESSION_TIMEOUT_MS = 5 * 60 * 1000L
    }

    suspend fun getOrCreateSession(sourceApp: String): TranslationSessionEntity {
        sessionDao.closeStaleSessions(SESSION_TIMEOUT_MS)
        val active = sessionDao.getActiveSession()
        if (active != null && active.sourceApp == sourceApp) {
            return active
        }
        if (active != null) {
            sessionDao.closeSession(active.id)
        }
        val newSession = TranslationSessionEntity(sourceApp = sourceApp)
        val id = sessionDao.insertSession(newSession)
        return sessionDao.getActiveSession() ?: TranslationSessionEntity(
            id = id,
            sourceApp = sourceApp
        )
    }

    suspend fun saveTranslation(
        sessionId: Long,
        sourceImagePath: String,
        translatedText: String,
        sourceLanguage: String,
        targetLanguage: String,
        bubbleCount: Int,
        translationTimeMs: Long
    ) {
        val entity = TranslationEntity(
            sessionId = sessionId,
            sourceImagePath = sourceImagePath,
            translatedText = translatedText,
            sourceLanguage = sourceLanguage,
            targetLanguage = targetLanguage,
            bubbleCount = bubbleCount,
            translationTimeMs = translationTimeMs
        )
        historyDao.insertTranslation(entity)
        sessionDao.updateSessionTimestamp(sessionId, System.currentTimeMillis())
        sessionDao.incrementBubbleCount(sessionId, bubbleCount)
    }

    suspend fun getSessionContext(sessionId: Long, maxBubbles: Int = 20): List<Pair<String, String>> {
        val history = sessionDao.getSessionHistory(sessionId, 5)
        return history.flatMap { entity ->
            parseTranslationPairs(entity.translatedText)
        }.takeLast(maxBubbles)
    }

    private fun parseTranslationPairs(translatedText: String): List<Pair<String, String>> {
        val regex = Regex("""\[(\d+)\]\s*(.+)""")
        return translatedText.lines().mapNotNull { line ->
            val match = regex.find(line.trim()) ?: return@mapNotNull null
            val parts = match.groupValues[2].split("→")
            if (parts.size == 2) parts[0].trim() to parts[1].trim() else null
        }
    }
}
