package com.yomu.app.db.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "translation_cache",
    indices = [Index(value = ["cacheKey"], unique = true)]
)
data class TranslationCacheEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val cacheKey: String,
    val engineId: String,
    val modelId: String?,
    val normalizedSourceText: String,
    val translatedText: String,
    val confidence: Float,
    val createdAt: Long = System.currentTimeMillis()
)
