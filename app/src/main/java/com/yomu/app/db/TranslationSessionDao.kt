package com.yomu.app.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.yomu.app.db.entities.TranslationEntity
import com.yomu.app.db.entities.TranslationSessionEntity

@Dao
interface TranslationSessionDao {
    @Query("SELECT * FROM translation_sessions WHERE isActive = 1 ORDER BY lastTranslatedAt DESC LIMIT 1")
    suspend fun getActiveSession(): TranslationSessionEntity?

    @Insert
    suspend fun insertSession(session: TranslationSessionEntity): Long

    @Query("UPDATE translation_sessions SET lastTranslatedAt = :timestamp WHERE id = :id")
    suspend fun updateSessionTimestamp(id: Long, timestamp: Long)

    @Query("UPDATE translation_sessions SET bubbleCount = bubbleCount + :count WHERE id = :id")
    suspend fun incrementBubbleCount(id: Long, count: Int)

    @Query("UPDATE translation_sessions SET isActive = 0 WHERE id = :id")
    suspend fun closeSession(id: Long)

    @Query("SELECT * FROM translations WHERE sessionId = :sessionId ORDER BY createdAt DESC LIMIT :limit")
    suspend fun getSessionHistory(sessionId: Long, limit: Int): List<TranslationEntity>

    @Query("UPDATE translation_sessions SET isActive = 0 WHERE isActive = 1 AND (strftime('%s', 'now') * 1000 - lastTranslatedAt) > :timeoutMs")
    suspend fun closeStaleSessions(timeoutMs: Long)
}
