package com.yomu.pipeline.ocr

import android.graphics.Bitmap
import com.yomu.ml.OnnxRuntime
import java.io.File

data class OcrResult(
    val text: String,
    /** The decoder's mean probability for the tokens it chose, the stop token included. */
    val confidence: Float,
    val boundingBox: FloatArray
) {
    /** A read this unsure is likely garbled, art or a text-free balloon, and is not worth translating (#366). */
    val unsure: Boolean get() = confidence < OcrEngine.MIN_CONFIDENCE
}

/**
 * Reads one crop with Hayai v2.7 (ADR-0024): the crop keeps its shape on a [PatchGrid], the prefill graph reads it and
 * the decode graph writes the text one byte-level token at a time.
 */
class OcrEngine(private val onnxRuntime: OnnxRuntime) {

    companion object {
        // Hayai's mean confidence sits near 0.99 for right and wrong reads alike. On JMangaBench_Read at the 192 patch
        // cap, this gate drops 52 of 7,789 reads, 42 of them wrong; 0.90 drops 46 more wrong and 31 more right, and the
        // lowest token probability separates no better (#510). Art reads as "...", which readsAsJapanese drops.
        const val MIN_CONFIDENCE = 0.85f
        private const val BOS_TOKEN_ID = 16001L
        private const val EOS_TOKEN_ID = 16002
        private const val PAD_TOKEN_ID = 16000
    }

    private var prefillPath: String? = null
    private var decodePath: String? = null
    private var vocab: ByteLevelVocab? = null
    private var positions: NpyFloats? = null

    /** Loads both graphs, the tokenizer and the position table; true only when all four loaded. */
    fun loadModel(prefillPath: String, decodePath: String, tokenizerPath: String, positionsPath: String): Boolean {
        val graphsLoaded = onnxRuntime.loadModel(prefillPath) && onnxRuntime.loadModel(decodePath)
        this.prefillPath = prefillPath
        this.decodePath = decodePath
        vocab = runCatching { ByteLevelVocab.load(File(tokenizerPath)) }.getOrNull()
        positions = runCatching { NpyFloats.read(File(positionsPath)) }.getOrNull()
        return graphsLoaded && isModelLoaded()
    }

    fun isModelLoaded(): Boolean =
        prefillPath?.let(onnxRuntime::isModelLoaded) == true && decodePath?.let(onnxRuntime::isModelLoaded) == true &&
            vocab != null && positions != null

    /** The text of [region], or null when the model is not loaded or its prefill fails. */
    fun extractText(region: Bitmap): OcrResult? {
        if (!isModelLoaded()) return null
        val vocab = vocab ?: return null
        val pixels = IntArray(region.width * region.height)
        region.getPixels(pixels, 0, region.width, 0, 0, region.width, region.height)
        val input = HayaiInput.of(
            pixels, region.width, region.height, positions ?: return null, PatchGrid.of(region.height, region.width)
        )
        val decode = onnxRuntime.startHayaiDecode(
            prefillPath ?: return null, decodePath ?: return null,
            input.pixelValues, input.positions, input.slots, input.grid.rows, input.grid.columns, BOS_TOKEN_ID
        ) ?: return null
        val decoded = decode.use {
            decodeGreedy({ it == EOS_TOKEN_ID || it == PAD_TOKEN_ID }) { previous ->
                val logits = if (previous == null) decode.firstLogits else decode.step(previous.toLong())
                logits?.let { chooseToken(it, 0, it.size) }
            }
        }
        return OcrResult(
            text = vocab.decode(decoded.tokenIds).trim(),
            confidence = decoded.confidence,
            boundingBox = floatArrayOf(region.width.toFloat(), region.height.toFloat(), 0f, 0f)
        )
    }

    fun release() {
        prefillPath?.let(onnxRuntime::releaseModel)
        decodePath?.let(onnxRuntime::releaseModel)
        prefillPath = null
        decodePath = null
        vocab = null
        positions = null
    }
}
