package com.yomu.pipeline

import android.graphics.Bitmap
import android.util.Log
import com.yomu.pipeline.bubble.BubbleDetector
import com.yomu.pipeline.context.ContextAssembler
import com.yomu.pipeline.ocr.OcrEngine
import com.yomu.pipeline.translation.TranslationEngine
import com.yomu.pipeline.translation.TranslationResult
import com.yomu.pipeline.typesetting.TypesetBubble
import com.yomu.pipeline.typesetting.Typesetter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

data class ModelPaths(
    val bubbleDetectionPath: String,
    val ocrEncoderPath: String,
    val ocrDecoderPath: String,
    val ocrVocabPath: String
)

data class PipelineResult(
    val typesetBubbles: List<TypesetBubble>,
    val translationResult: TranslationResult,
    val pageWidth: Int,
    val pageHeight: Int,
    val totalTimeMs: Long
)

class TranslationPipeline(
    private val bubbleDetector: BubbleDetector,
    private val ocrEngine: OcrEngine,
    private val contextAssembler: ContextAssembler,
    private val translationEngine: TranslationEngine,
    private val typesetter: Typesetter
) {

    companion object {
        private const val TAG = "TranslationPipeline"
        private const val TRANSLATION_READY_TIMEOUT_MS = 120_000L
    }

    enum class Stage {
        BUBBLE_DETECTION,
        OCR,
        CONTEXT_ASSEMBLY,
        TRANSLATION,
        TYPESETTING,
        DONE,
        ERROR
    }

    interface PipelineCallback {
        fun onStageProgress(stage: Stage, progress: Float)
        fun onError(stage: Stage, message: String)
        fun onComplete(result: PipelineResult)
    }

    private var currentStage = Stage.ERROR

    var modelPaths: ModelPaths? = null

    fun getCurrentStage(): Stage = currentStage

    fun isModelLoaded(): Boolean {
        return bubbleDetector.isModelLoaded() &&
               ocrEngine.isModelLoaded()
    }

    suspend fun loadModels(paths: ModelPaths): Boolean = withContext(Dispatchers.IO) {
        val bubbleLoaded = bubbleDetector.loadModel(paths.bubbleDetectionPath)
        val ocrLoaded = ocrEngine.loadModel(
            paths.ocrEncoderPath,
            paths.ocrDecoderPath,
            paths.ocrVocabPath
        )
        bubbleLoaded && ocrLoaded
    }

    suspend fun processPage(
        bitmap: Bitmap,
        sessionContext: List<Pair<String, String>> = emptyList(),
        callback: PipelineCallback? = null
    ): PipelineResult? {
        val startTime = System.currentTimeMillis()
        val pageWidth = bitmap.width
        val pageHeight = bitmap.height

        try {
            if (!isModelLoaded()) {
                val paths = modelPaths
                if (paths == null) {
                    callback?.onError(Stage.BUBBLE_DETECTION, "Model paths not configured")
                    currentStage = Stage.ERROR
                    return null
                }
                currentStage = Stage.BUBBLE_DETECTION
                callback?.onStageProgress(Stage.BUBBLE_DETECTION, 0.0f)
                Log.i(TAG, "loadModels start")
                loadModels(paths)
                Log.i(TAG, "loadModels complete modelsLoaded=${isModelLoaded()}")
            }

            if (!isModelLoaded()) {
                callback?.onError(Stage.BUBBLE_DETECTION, "Failed to load models")
                currentStage = Stage.ERROR
                return null
            }

            currentStage = Stage.BUBBLE_DETECTION
            callback?.onStageProgress(Stage.BUBBLE_DETECTION, 0.0f)
            val bubbles = bubbleDetector.detect(bitmap)
            callback?.onStageProgress(Stage.BUBBLE_DETECTION, 0.2f)

            if (bubbles.isEmpty()) {
                callback?.onError(Stage.BUBBLE_DETECTION, "No bubbles detected on the page")
                currentStage = Stage.ERROR
                return null
            }

            currentStage = Stage.OCR
            callback?.onStageProgress(Stage.OCR, 0.2f)
            val ocrResults = mutableMapOf<Int, com.yomu.pipeline.ocr.OcrResult>()

            for ((index, bubble) in bubbles.withIndex()) {
                val cropBounds = CropBoundsCalculator.clamp(
                    bitmapWidth = bitmap.width,
                    bitmapHeight = bitmap.height,
                    left = bubble.boundingBox.left.toInt(),
                    top = bubble.boundingBox.top.toInt(),
                    width = bubble.boundingBox.width().toInt(),
                    height = bubble.boundingBox.height().toInt()
                )
                val bubbleBitmap = Bitmap.createBitmap(
                    bitmap,
                    cropBounds.x,
                    cropBounds.y,
                    cropBounds.width,
                    cropBounds.height
                )

                val result = ocrEngine.extractText(bubbleBitmap)
                if (result != null && result.text.isNotEmpty()) {
                    ocrResults[bubble.id] = result
                }

                val progress = 0.2f + ((index + 1).toFloat() / bubbles.size) * 0.3f
                callback?.onStageProgress(Stage.OCR, progress)
            }

            currentStage = Stage.CONTEXT_ASSEMBLY
            callback?.onStageProgress(Stage.CONTEXT_ASSEMBLY, 0.5f)
            val pageContext = contextAssembler.assemble(
                bubbles = bubbles,
                ocrResults = ocrResults,
                pageWidth = pageWidth,
                pageHeight = pageHeight
            )
            callback?.onStageProgress(Stage.CONTEXT_ASSEMBLY, 0.6f)

            currentStage = Stage.TRANSLATION
            callback?.onStageProgress(Stage.TRANSLATION, 0.6f)
            if (!translationEngine.isReady()) {
                Log.i(TAG, "translationEnsureReady start")
                val ready = withTimeoutOrNull(TRANSLATION_READY_TIMEOUT_MS) {
                    translationEngine.ensureReady()
                } ?: false
                Log.i(TAG, "translationEnsureReady complete ready=$ready statusReady=${translationEngine.isReady()}")
            }
            val translationResult = if (translationEngine.isReady()) {
                translationEngine.translate(pageContext.blocks, sessionContext)
            } else {
                translationEngine.fallback(pageContext.blocks)
            }
            callback?.onStageProgress(Stage.TRANSLATION, 0.8f)

            currentStage = Stage.TYPESETTING
            callback?.onStageProgress(Stage.TYPESETTING, 0.8f)
            val bubbleBounds = bubbles.associate { bubble ->
                bubble.id to floatArrayOf(
                    bubble.boundingBox.left,
                    bubble.boundingBox.top,
                    bubble.boundingBox.right,
                    bubble.boundingBox.bottom
                )
            }
            val typesetBubbles = typesetter.typeset(
                translationResult.translations,
                bubbleBounds
            )
            callback?.onStageProgress(Stage.TYPESETTING, 1.0f)

            currentStage = Stage.DONE

            val result = PipelineResult(
                typesetBubbles = typesetBubbles,
                translationResult = translationResult,
                pageWidth = pageWidth,
                pageHeight = pageHeight,
                totalTimeMs = System.currentTimeMillis() - startTime
            )

            callback?.onComplete(result)
            return result

        } catch (e: Exception) {
            callback?.onError(currentStage, e.message ?: "Unknown error")
            currentStage = Stage.ERROR
            return null
        }
    }

    fun release() {
        contextAssembler.reset()
        translationEngine.release()
    }

    fun close() {
        bubbleDetector.release()
        ocrEngine.release()
        contextAssembler.reset()
        translationEngine.close()
    }
}
