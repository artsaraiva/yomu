package com.yomu.app.db

import androidx.room.*
import com.yomu.app.db.entities.ModelEntity
import com.yomu.app.db.entities.ModelStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface ModelDao {
    @Query("SELECT * FROM models ORDER BY createdAt DESC")
    fun getAllModels(): Flow<List<ModelEntity>>
    
    @Query("SELECT * FROM models WHERE id = :id")
    suspend fun getModelById(id: String): ModelEntity?
    
    @Query("SELECT * FROM models WHERE type = :type")
    suspend fun getModelsByType(type: String): List<ModelEntity>
    
    @Query("SELECT * FROM models WHERE status = :status")
    suspend fun getModelsByStatus(status: ModelStatus): List<ModelEntity>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertModel(model: ModelEntity)
    
    @Update
    suspend fun updateModel(model: ModelEntity)
    
    @Delete
    suspend fun deleteModel(model: ModelEntity)
    
    @Query("UPDATE models SET status = :status, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateModelStatus(id: String, status: ModelStatus, updatedAt: Long = System.currentTimeMillis())
    
    @Query("UPDATE models SET downloadProgress = :progress WHERE id = :id")
    suspend fun updateDownloadProgress(id: String, progress: Int)
}
