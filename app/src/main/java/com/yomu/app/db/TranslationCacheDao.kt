package com.yomu.app.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.yomu.app.db.entities.TranslationCacheEntity

@Dao
interface TranslationCacheDao {
    @Query("SELECT * FROM translation_cache WHERE cacheKey = :key LIMIT 1")
    suspend fun get(key: String): TranslationCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(entity: TranslationCacheEntity)
}
