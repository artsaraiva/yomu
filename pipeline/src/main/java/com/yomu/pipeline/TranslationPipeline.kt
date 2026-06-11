package com.yomu.pipeline

import android.graphics.Bitmap
import com.yomu.pipeline.bubble.BubbleDetector
import com.yomu.pipeline.context.ContextAssembler
import com.yomu.pipeline.ocr.OcrEngine
import com.yomu.pipeline.translation.TranslationEngine
import com.yomu.pipeline.translation.TranslationResult
import com.yomu.pipeline.typesetting.TypesetBubble
import com.yomu.pipeline.typesetting.Typesetter

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

    fun getCurrentStage(): Stage = currentStage

    fun isModelLoaded(): Boolean {
        return bubbleDetector.isModelLoaded() &&
               ocrEngine.isModelLoaded() &&
               translationEngine.isModelLoaded()
    }

    fun processPage(
        bitmap: Bitmap,
        callback: PipelineCallback? = null
    ): PipelineResult? {
        val startTime = System.currentTimeMillis()
        val pageWidth = bitmap.width
        val pageHeight = bitmap.height

        try {
            // Stage 1: Bubble Detection
            currentStage = Stage.BUBBLE_DETECTION
            callback?.onStageProgress(Stage.BUBBLE_DETECTION, 0.0f)
            val bubbles = bubbleDetector.detect(bitmap)
            callback?.onStageProgress(Stage.BUBBLE_DETECTION, 0.2f)

            if (bubbles.isEmpty()) {
                callback?.onError(Stage.BUBBLE_DETECTION, "No bubbles detected on the page")
                currentStage = Stage.ERROR
                return null
            }

            // Stage 2: OCR
            currentStage = Stage.OCR
            callback?.onStageProgress(Stage.OCR, 0.2f)
            val ocrResults = mutableMapOf<Int, com.yomu.pipeline.ocr.OcrResult>()

            for ((index, bubble) in bubbles.withIndex()) {
                val bubbleBitmap = Bitmap.createBitmap(
                    bitmap,
                    bubble.boundingBox.left.toInt().coerceAtLeast(0),
                    bubble.boundingBox.top.toInt().coerceAtLeast(0),
                    bubble.boundingBox.width().toInt().coerceAtMost(bitmap.width),
                    bubble.boundingBox.height().toInt().coerceAtMost(bitmap.height)
                )

                val result = ocrEngine.extractText(bubbleBitmap)
                if (result != null && result.text.isNotEmpty()) {
                    ocrResults[bubble.id] = result
                }

                val progress = 0.2f + ((index + 1).toFloat() / bubbles.size) * 0.3f
                callback?.onStageProgress(Stage.OCR, progress)
            }

            // Stage 3: Context Assembly
            currentStage = Stage.CONTEXT_ASSEMBLY
            callback?.onStageProgress(Stage.CONTEXT_ASSEMBLY, 0.5f)
            val pageContext = contextAssembler.assemble(
                bubbles = bubbles,
                ocrResults = ocrResults,
                pageWidth = pageWidth,
                pageHeight = pageHeight
            )
            callback?.onStageProgress(Stage.CONTEXT_ASSEMBLY, 0.6f)

            // Stage 4: Translation
            currentStage = Stage.TRANSLATION
            callback?.onStageProgress(Stage.TRANSLATION, 0.6f)
            val translationResult = translationEngine.translate(pageContext.blocks)
            callback?.onStageProgress(Stage.TRANSLATION, 0.8f)

            // Stage 5: Typesetting
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
        bubbleDetector.release()
        ocrEngine.release()
        contextAssembler.reset()
        translationEngine.release()
    }
}
