package com.yomu.app.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "translations")
data class TranslationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sourceImagePath: String,
    val translatedText: String,
    val sourceLanguage: String,
    val targetLanguage: String,
    val bubbleCount: Int,
    val translationTimeMs: Long,
    val createdAt: Long = System.currentTimeMillis()
)
