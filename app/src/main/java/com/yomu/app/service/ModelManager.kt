package com.yomu.app.service

import android.content.Context
import com.yomu.app.db.ModelDao
import com.yomu.app.db.entities.ModelEntity
import com.yomu.app.db.entities.ModelStatus
import com.yomu.app.db.entities.ModelType
import com.yomu.core.Constants
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ModelManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val modelDao: ModelDao,
    private val okHttpClient: OkHttpClient
) {
    
    data class DownloadProgress(
        val modelId: String,
        val bytesDownloaded: Long,
        val totalBytes: Long,
        val percentage: Int
    )
    
    fun getAllModels(): Flow<List<ModelEntity>> = modelDao.getAllModels()
    
    suspend fun getModel(id: String): ModelEntity? = modelDao.getModelById(id)
    
    suspend fun getModelsByType(type: ModelType): List<ModelEntity> = modelDao.getModelsByType(type.name)
    
    suspend fun refreshModelList() {
        val models = listOf(
            ModelEntity(
                id = "bubble_detection_v1",
                name = "Bubble Detection (YOLOv11 Nano)",
                type = ModelType.VISION,
                fileName = Constants.BUBBLE_DETECTION_MODEL,
                fileSize = 6_000_000L,
                downloadUrl = "https://models.yomu.app/v1/${Constants.BUBBLE_DETECTION_MODEL}",
                checksum = "",
                status = ModelStatus.AVAILABLE,
                version = "1.0",
                isRequired = true
            ),
            ModelEntity(
                id = "manga_ocr_v1",
                name = "MangaOCR",
                type = ModelType.VISION,
                fileName = Constants.OCR_MODEL,
                fileSize = 150_000_000L,
                downloadUrl = "https://models.yomu.app/v1/${Constants.OCR_MODEL}",
                checksum = "",
                status = ModelStatus.AVAILABLE,
                version = "1.0",
                isRequired = true
            ),
            ModelEntity(
                id = "qwen3_1.7b_4bit_v1",
                name = "Qwen 3 1.7B (4-bit)",
                type = ModelType.LLM,
                fileName = Constants.TRANSLATION_MODEL_4BIT,
                fileSize = 1_000_000_000L,
                downloadUrl = "https://models.yomu.app/v1/${Constants.TRANSLATION_MODEL_4BIT}",
                checksum = "",
                status = ModelStatus.AVAILABLE,
                version = "1.0",
                isRequired = true
            )
        )
        
        for (model in models) {
            val existing = modelDao.getModelById(model.id)
            if (existing == null) {
                modelDao.insertModel(model)
            }
        }
    }
    
    suspend fun downloadModel(
        modelId: String,
        onProgress: (DownloadProgress) -> Unit = {}
    ): Boolean = withContext(Dispatchers.IO) {
        val model = modelDao.getModelById(modelId) ?: return@withContext false
        
        modelDao.updateModelStatus(modelId, ModelStatus.DOWNLOADING)
        
        try {
            val request = Request.Builder()
                .url(model.downloadUrl)
                .build()
            
            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                modelDao.updateModelStatus(modelId, ModelStatus.ERROR)
                return@withContext false
            }
            
            val body = response.body ?: run {
                modelDao.updateModelStatus(modelId, ModelStatus.ERROR)
                return@withContext false
            }
            
            val outputDir = when (model.type) {
                ModelType.VISION -> File(context.filesDir, "${Constants.MODELS_DIR}/${Constants.VISION_MODELS_DIR}")
                ModelType.LLM -> File(context.filesDir, "${Constants.MODELS_DIR}/${Constants.LLM_MODELS_DIR}")
            }
            outputDir.mkdirs()
            
            val outputFile = File(outputDir, model.fileName)
            val totalBytes = body.contentLength()
            var bytesDownloaded = 0L
            
            body.byteStream().use { input ->
                FileOutputStream(outputFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        bytesDownloaded += bytesRead
                        
                        if (totalBytes > 0) {
                            val progress = ((bytesDownloaded * 100) / totalBytes).toInt()
                            modelDao.updateDownloadProgress(modelId, progress)
                            onProgress(DownloadProgress(
                                modelId = modelId,
                                bytesDownloaded = bytesDownloaded,
                                totalBytes = totalBytes,
                                percentage = progress
                            ))
                        }
                    }
                }
            }
            
            // Verify checksum if available
            if (model.checksum.isNotEmpty()) {
                val digest = MessageDigest.getInstance("SHA-256")
                val fileDigest = outputFile.inputStream().use { input ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        digest.update(buffer, 0, bytesRead)
                    }
                    digest.digest().joinToString("") { "%02x".format(it) }
                }
                
                if (fileDigest != model.checksum) {
                    outputFile.delete()
                    modelDao.updateModelStatus(modelId, ModelStatus.ERROR)
                    return@withContext false
                }
            }
            
            modelDao.updateModelStatus(modelId, ModelStatus.READY)
            return@withContext true
        } catch (e: Exception) {
            modelDao.updateModelStatus(modelId, ModelStatus.ERROR)
            return@withContext false
        }
    }
    
    suspend fun deleteModel(modelId: String): Boolean = withContext(Dispatchers.IO) {
        val model = modelDao.getModelById(modelId) ?: return@withContext false
        
        val modelDir = when (model.type) {
            ModelType.VISION -> File(context.filesDir, "${Constants.MODELS_DIR}/${Constants.VISION_MODELS_DIR}")
            ModelType.LLM -> File(context.filesDir, "${Constants.MODELS_DIR}/${Constants.LLM_MODELS_DIR}")
        }
        
        val modelFile = File(modelDir, model.fileName)
        if (modelFile.exists()) {
            modelFile.delete()
        }
        
        modelDao.updateModelStatus(modelId, ModelStatus.AVAILABLE)
        modelDao.updateDownloadProgress(modelId, 0)
        true
    }
    
    fun getModelFile(model: ModelEntity): File? {
        val modelDir = when (model.type) {
            ModelType.VISION -> File(context.filesDir, "${Constants.MODELS_DIR}/${Constants.VISION_MODELS_DIR}")
            ModelType.LLM -> File(context.filesDir, "${Constants.MODELS_DIR}/${Constants.LLM_MODELS_DIR}")
        }
        val modelFile = File(modelDir, model.fileName)
        return if (modelFile.exists()) modelFile else null
    }
    
    fun getTotalModelSize(): Long {
        val modelsDir = File(context.filesDir, Constants.MODELS_DIR)
        if (!modelsDir.exists()) return 0
        return modelsDir.walkTopDown()
            .filter { it.isFile }
            .sumOf { it.length() }
    }
}
