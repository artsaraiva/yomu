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
}
