package com.yomu.app.service
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.StatFs
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import com.yomu.app.db.ModelDao
import com.yomu.app.db.entities.ModelEntity
import com.yomu.app.db.entities.ModelStatus
import com.yomu.app.db.entities.ModelType
import com.yomu.core.Constants
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
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
    companion object {
        private const val ML_KIT_DOWNLOAD_TIMEOUT_MS = 120_000L
    }

    data class DownloadProgress(
        val modelId: String,
        val bytesDownloaded: Long,
        val totalBytes: Long,
        val percentage: Int
    )

    private data class AdditionalFile(
        val fileName: String,
        val url: String,
        val checksum: String,
        val size: Long
    )

    private data class DownloadTask(
        val file: File,
        val url: String,
        val checksum: String,
        val size: Long
    )

    fun getAllModels(): Flow<List<ModelEntity>> = modelDao.getAllModels()

    suspend fun getModel(id: String): ModelEntity? = modelDao.getModelById(id)

    suspend fun getModelsByType(type: ModelType): List<ModelEntity> = modelDao.getModelsByType(type.name)

    suspend fun refreshModelList() {
        val models = listOf(
            ModelEntity(
                id = Constants.BUBBLE_DETECTION_MODEL_ID,
                name = "Bubble Detection (YOLO26 Nano Manga)",
                type = ModelType.VISION,
                fileName = Constants.BUBBLE_DETECTION_MODEL,
                fileSize = 6_070_000L,
                downloadUrl = "https://huggingface.co/Kiuyha/Manga-Bubble-YOLO/resolve/main/onnx/yolo26n.onnx",
                checksum = "b45c2e12cf0c3c1d2abfbbb9123c9f96f040f2ac36a0842382ecd9d859c851c7",
                status = ModelStatus.AVAILABLE,
                version = "1.0",
                isRequired = true
            ),
            ModelEntity(
                id = Constants.MANGA_OCR_MODEL_ID,
                name = "MangaOCR (Encoder + Decoder)",
                type = ModelType.VISION,
                fileName = Constants.OCR_ENCODER_MODEL,
                fileSize = 140_410_339L,
                downloadUrl = "https://huggingface.co/l0wgear/manga-ocr-2025-onnx/resolve/main/encoder_model.onnx",
                checksum = "f87668ae0f62d6f032dac6b213e8c0fea84cd15895ac8cab624cc9a2f49d4a27",
                status = ModelStatus.AVAILABLE,
                version = "1.0",
                isRequired = true
            ),
            ModelEntity(
                id = Constants.ML_KIT_JA_EN_MODEL_ID,
                name = "ML Kit Japanese → English",
                type = ModelType.TRANSLATION,
                fileName = "mlkit-ja-en",
                fileSize = 30_000_000L,
                downloadUrl = "google-mlkit-translate-ja-en",
                checksum = "",
                status = ModelStatus.AVAILABLE,
                version = "1.0",
                isRequired = false
            ),
            ModelEntity(
                id = Constants.QWEN_TRANSLATION_MODEL_ID,
                name = "Qwen 3 1.7B (4-bit Q4_K_M)",
                type = ModelType.LLM,
                fileName = Constants.TRANSLATION_MODEL_4BIT,
                fileSize = 1_282_439_584L,
                downloadUrl = "https://huggingface.co/bartowski/Qwen_Qwen3-1.7B-GGUF/resolve/main/Qwen_Qwen3-1.7B-Q4_K_M.gguf",
                checksum = "72c5c3cb38fa32d5256e2fe30d03e7a64c6c79e668ad84057e3bd66e250b24fb",
                status = ModelStatus.AVAILABLE,
                version = "1.0",
                isRequired = false
            )
        )

        for (model in models) {
            val existing = modelDao.getModelById(model.id)
            if (existing == null) {
                modelDao.insertModel(model)
            } else {
                modelDao.insertModel(
                    existing.copy(
                        name = model.name,
                        type = model.type,
                        fileName = model.fileName,
                        fileSize = model.fileSize,
                        downloadUrl = model.downloadUrl,
                        checksum = model.checksum,
                        version = model.version,
                        isRequired = model.isRequired
                    )
                )
            }
        }
    }
    private fun getAdditionalFiles(modelId: String): List<AdditionalFile> = when (modelId) {
        Constants.MANGA_OCR_MODEL_ID -> listOf(
            AdditionalFile(
                fileName = Constants.OCR_DECODER_MODEL,
                url = "https://huggingface.co/l0wgear/manga-ocr-2025-onnx/resolve/main/decoder_model.onnx",
                checksum = "6b1fb216d542c4b2a4fa5b9d7ae3522081eb85fb959d2cecd28055af956a8a5e",
                size = 118_053_454L
            ),
            AdditionalFile(
                fileName = Constants.OCR_VOCAB_FILE,
                url = "https://huggingface.co/l0wgear/manga-ocr-2025-onnx/resolve/main/vocab.txt",
                checksum = "",
                size = 24_072L
            )
        )
        else -> emptyList()
    }
    private fun getModelDir(type: ModelType): File = when (type) {
        ModelType.VISION -> File(context.filesDir, "${Constants.MODELS_DIR}/${Constants.VISION_MODELS_DIR}")
        ModelType.LLM -> File(context.filesDir, "${Constants.MODELS_DIR}/${Constants.LLM_MODELS_DIR}")
        ModelType.TRANSLATION -> File(context.filesDir, "${Constants.MODELS_DIR}/${Constants.TRANSLATION_MODELS_DIR}")
    }
    private fun isOnWifi(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    private fun hasEnoughSpace(requiredBytes: Long): Boolean {
        val dir = File(context.filesDir, Constants.MODELS_DIR)
        dir.mkdirs()
        val stat = StatFs(dir.absolutePath)
        return stat.availableBytes > requiredBytes * 1.1
    }
    suspend fun downloadModel(
        modelId: String,
        onProgress: (DownloadProgress) -> Unit = {}
    ): Boolean = withContext(Dispatchers.IO) {
        if (modelId == Constants.ML_KIT_JA_EN_MODEL_ID) {
            return@withContext downloadMlKitTranslationModel(modelId, onProgress)
        }

        if (!isOnWifi()) {
            return@withContext false
        }

        val model = modelDao.getModelById(modelId) ?: return@withContext false
        val additionalFiles = getAdditionalFiles(model.id)
        val totalRequiredBytes = model.fileSize + additionalFiles.sumOf { it.size }

        if (!hasEnoughSpace(totalRequiredBytes)) {
            return@withContext false
        }

        modelDao.updateModelStatus(modelId, ModelStatus.DOWNLOADING)

        val outputDir = getModelDir(model.type)
        outputDir.mkdirs()

        val tasks = listOf(
            DownloadTask(
                file = File(outputDir, model.fileName),
                url = model.downloadUrl,
                checksum = model.checksum,
                size = model.fileSize
            )
        ) + additionalFiles.map {
            DownloadTask(
                file = File(outputDir, it.fileName),
                url = it.url,
                checksum = it.checksum,
                size = it.size
            )
        }

        try {
            val totalBytes = tasks.sumOf { it.size }
            var bytesDownloaded = 0L

            for (task in tasks) {
                val success = downloadFile(task.url, task.file) { bytesRead ->
                    bytesDownloaded += bytesRead
                    if (totalBytes > 0) {
                        val progress = ((bytesDownloaded * 100) / totalBytes).toInt()
                        modelDao.updateDownloadProgress(modelId, progress)
                        onProgress(
                            DownloadProgress(
                                modelId = modelId,
                                bytesDownloaded = bytesDownloaded,
                                totalBytes = totalBytes,
                                percentage = progress
                            )
                        )
                    }
                }

                if (!success) {
                    deleteFiles(tasks.map { it.file })
                    modelDao.updateModelStatus(modelId, ModelStatus.ERROR)
                    return@withContext false
                }

                if (task.checksum.isNotEmpty()) {
                    val fileChecksum = computeChecksum(task.file)
                    if (fileChecksum != task.checksum) {
                        deleteFiles(tasks.map { it.file })
                        modelDao.updateModelStatus(modelId, ModelStatus.ERROR)
                        return@withContext false
                    }
                }
            }

            modelDao.updateModelStatus(modelId, ModelStatus.READY)
            true
        } catch (e: Exception) {
            modelDao.updateModelStatus(modelId, ModelStatus.ERROR)
            false
        }
    }

    private suspend fun downloadMlKitTranslationModel(
        modelId: String,
        onProgress: (DownloadProgress) -> Unit
    ): Boolean {
        modelDao.updateModelStatus(modelId, ModelStatus.DOWNLOADING)
        onProgress(DownloadProgress(modelId, 0L, 30_000_000L, 0))
        val options = TranslatorOptions.Builder()
            .setSourceLanguage(TranslateLanguage.JAPANESE)
            .setTargetLanguage(TranslateLanguage.ENGLISH)
            .build()
        val translator = Translation.getClient(options)
        return try {
            val ready = withTimeoutOrNull(ML_KIT_DOWNLOAD_TIMEOUT_MS) {
                translator.downloadModelIfNeeded(DownloadConditions.Builder().build()).await()
                true
            } ?: false
            if (ready) {
                modelDao.updateDownloadProgress(modelId, 100)
                onProgress(DownloadProgress(modelId, 30_000_000L, 30_000_000L, 100))
                modelDao.updateModelStatus(modelId, ModelStatus.READY)
                true
            } else {
                modelDao.updateModelStatus(modelId, ModelStatus.ERROR)
                false
            }
        } catch (_: Exception) {
            modelDao.updateModelStatus(modelId, ModelStatus.ERROR)
            false
        } finally {
            translator.close()
        }
    }

    private suspend fun downloadFile(
        url: String,
        outputFile: File,
        onBytesRead: suspend (Long) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .build()

        val response = okHttpClient.newCall(request).execute()
        if (!response.isSuccessful) {
            return@withContext false
        }

        val body = response.body ?: return@withContext false

        body.byteStream().use { input ->
            FileOutputStream(outputFile).use { output ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    onBytesRead(bytesRead.toLong())
                }
            }
        }

        true
    }

    private fun computeChecksum(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun deleteFiles(files: List<File>) {
        for (file in files) {
            if (file.exists()) {
                file.delete()
            }
        }
    }

    suspend fun deleteModel(modelId: String): Boolean = withContext(Dispatchers.IO) {
        if (modelId == Constants.ML_KIT_JA_EN_MODEL_ID) {
            modelDao.updateModelStatus(modelId, ModelStatus.AVAILABLE)
            modelDao.updateDownloadProgress(modelId, 0)
            return@withContext true
        }

        val model = modelDao.getModelById(modelId) ?: return@withContext false

        val modelDir = getModelDir(model.type)
        val filesToDelete = listOf(File(modelDir, model.fileName)) + getAdditionalFiles(model.id).map {
            File(modelDir, it.fileName)
        }
        for (file in filesToDelete) {
            if (file.exists()) {
                file.delete()
            }
        }

        modelDao.updateModelStatus(modelId, ModelStatus.AVAILABLE)
        modelDao.updateDownloadProgress(modelId, 0)
        true
    }

    fun getModelFile(model: ModelEntity): File? {
        val modelFile = File(getModelDir(model.type), model.fileName)
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
