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

/** A model's slot in the pipeline. Stored by name, so renaming an entry needs a database migration. */
enum class ModelType {
    DETECTION,
    OCR,
    LLM
}

enum class ModelStatus {
    AVAILABLE,
    DOWNLOADING,
    READY,
    ERROR,
    UPDATE_AVAILABLE
}
