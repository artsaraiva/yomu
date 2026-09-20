package com.yomu.app.service
import android.app.ActivityManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.StatFs
import com.yomu.app.db.ModelDao
import com.yomu.app.db.entities.ModelEntity
import com.yomu.app.db.entities.ModelStatus
import com.yomu.app.db.entities.ModelType
import com.yomu.app.translation.LlmModelCatalog
import com.yomu.core.Constants
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
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

    internal data class AdditionalFile(
        val fileName: String,
        val url: String,
        val checksum: String,
        val size: Long
    )

    internal data class DownloadTask(
        val file: File,
        val url: String,
        val checksum: String,
        val size: Long
    )

    fun getAllModels(): Flow<List<ModelEntity>> = modelDao.getAllModels()

    suspend fun getModel(id: String): ModelEntity? = modelDao.getModelById(id)

    suspend fun getModelsByType(type: ModelType): List<ModelEntity> = modelDao.getModelsByType(type.name)

    companion object {
        /** Every model Yomu ships. [refreshModelList] upserts these rows; plain data so JVM tests can read it. */
        internal val REGISTRY = listOf(
            ModelEntity(
                id = Constants.BUBBLE_DETECTION_MODEL_ID,
                name = "Bubble Detection (YOLO26 Nano Manga)",
                type = ModelType.DETECTION,
                fileName = Constants.BUBBLE_DETECTION_MODEL,
                fileSize = 6_070_000L,
                downloadUrl = "https://huggingface.co/Kiuyha/Manga-Bubble-YOLO/resolve/fb646500455e8a8a3a807fd27b855c8e4fc63766/onnx/yolo26n.onnx",
                checksum = "b45c2e12cf0c3c1d2abfbbb9123c9f96f040f2ac36a0842382ecd9d859c851c7",
                status = ModelStatus.AVAILABLE,
                version = "1.0",
                isRequired = true
            ),
            ModelEntity(
                id = Constants.MANGA_OCR_MODEL_ID,
                name = "MangaOCR (Encoder + Decoder)",
                type = ModelType.OCR,
                fileName = Constants.OCR_ENCODER_MODEL,
                fileSize = 140_410_339L,
                downloadUrl = "https://huggingface.co/l0wgear/manga-ocr-2025-onnx/resolve/e8b27bbd3f424fe3877e0bda704d6a920e4f0a33/encoder_model.onnx",
                checksum = "f87668ae0f62d6f032dac6b213e8c0fea84cd15895ac8cab624cc9a2f49d4a27",
                status = ModelStatus.AVAILABLE,
                version = "1.0",
                isRequired = true
            ),
            ModelEntity(
                id = Constants.CAT_TRANSLATION_MODEL_ID,
                name = "CAT-Translate 0.8B (Q4_K_M)",
                type = ModelType.LLM,
                fileName = Constants.TRANSLATION_MODEL_4BIT,
                fileSize = Constants.TRANSLATION_MODEL_4BIT_SIZE,
                downloadUrl = "https://huggingface.co/mradermacher/CAT-Translate-0.8b-GGUF/resolve/834d0624185e964856a1b3c43eb5e114c9c41df5/CAT-Translate-0.8b.Q4_K_M.gguf",
                checksum = "6de8e40b687eb2248727c8ad208c54af8c82ad52b415901859d5fcd7fd65bb4c",
                status = ModelStatus.AVAILABLE,
                version = "1.0",
                isRequired = false
            ),
            // URL, exact size, and SHA-256 are the pinned Q4_K_M revisions (checksum is the HuggingFace
            // LFS oid, which is the sha256 of the file content — the same digest computeChecksum verifies).
            ModelEntity(
                id = Constants.CAT_TRANSLATION_14B_MODEL_ID,
                name = "CAT-Translate 1.4B (Q4_K_M)",
                type = ModelType.LLM,
                fileName = Constants.CAT_TRANSLATION_14B_MODEL,
                fileSize = Constants.CAT_TRANSLATION_14B_SIZE,
                downloadUrl = "https://huggingface.co/mradermacher/CAT-Translate-1.4b-GGUF/resolve/2eb35647e57b5981c14611e67b9ad205329b498d/CAT-Translate-1.4b.Q4_K_M.gguf",
                checksum = "332371e7aa764c6dde6df70956062e839aed69ad3db28e1af118aa99b6f63467",
                status = ModelStatus.AVAILABLE,
                version = "1.0",
                isRequired = false
            ),
            ModelEntity(
                id = Constants.QWEN25_15B_MODEL_ID,
                name = "Qwen2.5 1.5B Instruct (Q4_K_M)",
                type = ModelType.LLM,
                fileName = Constants.QWEN25_15B_MODEL,
                fileSize = Constants.QWEN25_15B_SIZE,
                downloadUrl = "https://huggingface.co/bartowski/Qwen2.5-1.5B-Instruct-GGUF/resolve/9eadc66189c7641e1ddd226b8267a9119b2ce2d4/Qwen2.5-1.5B-Instruct-Q4_K_M.gguf",
                checksum = "1adf0b11065d8ad2e8123ea110d1ec956dab4ab038eab665614adba04b6c3370",
                status = ModelStatus.AVAILABLE,
                version = "1.0",
                isRequired = false
            ),
            ModelEntity(
                id = Constants.QWEN35_2B_MODEL_ID,
                name = "Qwen3.5 2B (Q4_K_M)",
                type = ModelType.LLM,
                fileName = Constants.QWEN35_2B_MODEL,
                fileSize = Constants.QWEN35_2B_SIZE,
                downloadUrl = "https://huggingface.co/unsloth/Qwen3.5-2B-GGUF/resolve/f6d5376be1edb4d416d56da11e5397a961aca8ae/Qwen3.5-2B-Q4_K_M.gguf",
                checksum = "aaf42c8b7c3cab2bf3d69c355048d4a0ee9973d48f16c731c0520ee914699223",
                status = ModelStatus.AVAILABLE,
                version = "1.0",
                isRequired = false
            )
        )

        /** The curated model each slot holds when nothing (or an unknown id) is selected. */
        val SLOT_DEFAULTS: Map<ModelType, String> = mapOf(
            ModelType.DETECTION to Constants.BUBBLE_DETECTION_MODEL_ID,
            ModelType.OCR to Constants.MANGA_OCR_MODEL_ID,
            ModelType.LLM to LlmModelCatalog.DEFAULT.id
        )

        /**
         * Extra files downloaded with a model, in the order its consumer reads them from
         * [modelFiles]: MangaOCR's decoder, then its vocabulary.
         */
        internal fun additionalFiles(modelId: String): List<AdditionalFile> = when (modelId) {
            Constants.MANGA_OCR_MODEL_ID -> listOf(
                AdditionalFile(
                    fileName = Constants.OCR_DECODER_MODEL,
                    url = "https://huggingface.co/l0wgear/manga-ocr-2025-onnx/resolve/e8b27bbd3f424fe3877e0bda704d6a920e4f0a33/decoder_model.onnx",
                    checksum = "6b1fb216d542c4b2a4fa5b9d7ae3522081eb85fb959d2cecd28055af956a8a5e",
                    size = 118_053_454L
                ),
                AdditionalFile(
                    fileName = Constants.OCR_VOCAB_FILE,
                    url = "https://huggingface.co/l0wgear/manga-ocr-2025-onnx/resolve/e8b27bbd3f424fe3877e0bda704d6a920e4f0a33/vocab.txt",
                    checksum = "",
                    size = 24_072L
                )
            )
            else -> emptyList()
        }
    }

    suspend fun refreshModelList() = withContext(Dispatchers.IO) {
        val registryIds = REGISTRY.map { it.id }.toSet()
        for (stale in modelDao.getAllModels().first().filterNot { it.id in registryIds }) {
            deleteFiles(modelFiles(stale))
            modelDao.deleteModel(stale)
        }
        for (model in REGISTRY) {
            val existing = modelDao.getModelById(model.id)
            if (existing == null) {
                modelDao.insertModel(model)
            } else {
                // A READY row whose files left without going through deleteModel — benchmark
                // staging, storage pressure, a restore that missed filesDir — otherwise keeps
                // being selected and translates nothing in silence (#308).
                val lostItsFiles = existing.status == ModelStatus.READY &&
                    modelFiles(model).any { !it.exists() }
                modelDao.insertModel(
                    existing.copy(
                        name = model.name,
                        type = model.type,
                        fileName = model.fileName,
                        fileSize = model.fileSize,
                        downloadUrl = model.downloadUrl,
                        checksum = model.checksum,
                        version = model.version,
                        isRequired = model.isRequired,
                        status = if (lostItsFiles) ModelStatus.AVAILABLE else existing.status,
                        downloadProgress = if (lostItsFiles) 0 else existing.downloadProgress
                    )
                )
            }
        }
    }
    private fun getModelDir(type: ModelType): File = when (type) {
        ModelType.DETECTION, ModelType.OCR -> File(context.filesDir, "${Constants.MODELS_DIR}/${Constants.VISION_MODELS_DIR}")
        ModelType.LLM -> File(context.filesDir, "${Constants.MODELS_DIR}/${Constants.LLM_MODELS_DIR}")
    }
    fun deviceTotalMemBytes(): Long {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return 0L
        return ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }.totalMem
    }

    fun isOnWifi(): Boolean {
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
        if (!isOnWifi()) {
            return@withContext false
        }

        val model = modelDao.getModelById(modelId) ?: return@withContext false
        val additionalFiles = additionalFiles(model.id)
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

        downloadTasks(modelId, tasks, onProgress)
    }

    internal suspend fun downloadTasks(
        modelId: String,
        tasks: List<DownloadTask>,
        onProgress: (DownloadProgress) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
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
        } catch (e: CancellationException) {
            withContext(NonCancellable) {
                deleteFiles(tasks.map { it.file })
                modelDao.updateModelStatus(modelId, ModelStatus.AVAILABLE)
                modelDao.updateDownloadProgress(modelId, 0)
            }
            throw e
        } catch (e: Exception) {
            modelDao.updateModelStatus(modelId, ModelStatus.ERROR)
            false
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
                    ensureActive()
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
        val model = modelDao.getModelById(modelId) ?: return@withContext false

        deleteFiles(modelFiles(model))

        modelDao.updateModelStatus(modelId, ModelStatus.AVAILABLE)
        modelDao.updateDownloadProgress(modelId, 0)
        true
    }

    /** The model's main file followed by its [additionalFiles], whether or not they are downloaded. */
    fun modelFiles(model: ModelEntity): List<File> {
        val modelDir = getModelDir(model.type)
        return listOf(File(modelDir, model.fileName)) + additionalFiles(model.id).map { File(modelDir, it.fileName) }
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
