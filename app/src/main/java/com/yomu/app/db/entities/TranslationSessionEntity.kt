package com.yomu.app.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "translation_sessions")
data class TranslationSessionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sourceApp: String,
    val startedAt: Long = System.currentTimeMillis(),
    val lastTranslatedAt: Long = System.currentTimeMillis(),
    val bubbleCount: Int = 0,
    val isActive: Boolean = true
)
