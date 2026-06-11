package com.yomu.app.db

import androidx.room.*
import com.yomu.app.db.entities.TranslationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {
    @Query("SELECT * FROM translations ORDER BY createdAt DESC")
    fun getAllTranslations(): Flow<List<TranslationEntity>>
    
    @Query("SELECT * FROM translations WHERE id = :id")
    suspend fun getTranslationById(id: Long): TranslationEntity?
    
    @Insert
    suspend fun insertTranslation(translation: TranslationEntity): Long
    
    @Delete
    suspend fun deleteTranslation(translation: TranslationEntity)
    
    @Query("DELETE FROM translations")
    suspend fun clearHistory()
    
    @Query("SELECT COUNT(*) FROM translations")
    suspend fun getTranslationCount(): Int
}
