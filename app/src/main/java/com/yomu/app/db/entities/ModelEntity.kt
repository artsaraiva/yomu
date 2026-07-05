package com.yomu.app.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "models")
data class ModelEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val type: ModelType,
    val fileName: String,
    val fileSize: Long,
    val downloadUrl: String,
    val checksum: String,
    val status: ModelStatus,
    val downloadProgress: Int = 0,
    val version: String,
    val isRequired: Boolean,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

enum class ModelType {
    VISION,
    LLM,
    TRANSLATION
}

enum class ModelStatus {
    AVAILABLE,
    DOWNLOADING,
    READY,
    ERROR,
    UPDATE_AVAILABLE
}
