package com.yomu.pipeline.ocr

import android.graphics.Bitmap
import com.yomu.ml.OnnxRuntime
import java.io.File

data class OcrResult(
    val text: String,
    val confidence: Float,
    val boundingBox: FloatArray
)

class OcrEngine(private val onnxRuntime: OnnxRuntime) {

    companion object {
        private const val INPUT_SIZE = 224
        private const val CHANNELS = 3
        // Safety cap on decoder steps, not the expected length: generation stops at the real [SEP]
        // (resolved from the vocab, see sepTokenId), so short bubbles end early and only genuinely
        // long ones use the budget. 32 truncated lines longer than the 36-char annotated max (#82).
        private const val MAX_LENGTH = 128
        private const val BOS_TOKEN_ID = 0
        // Fallback only: this model's [SEP] is not reliably at index 102 (the English-BERT id).
        // The real stop id is resolved from the loaded vocab by string; see sepTokenId.
        private const val EOS_TOKEN_ID = 102
        private const val SEP_TOKEN = "[SEP]"
        private const val NORMALIZATION_MEAN = 0.5f
        private const val NORMALIZATION_STD = 0.5f

        internal fun decodeTokens(tokenIds: List<Int>, vocab: Map<Int, String>): String {
            val builder = StringBuilder()
            for (id in tokenIds) {
                val token = vocab[id] ?: continue
                when {
                    // Special tokens ([CLS], [SEP], [PAD], [UNK], ...) are structure, not text —
                    // drop them. Leaking them corrupted every source line (#74) and made the LLM
                    // echo the prompt instead of translating.
                    isSpecialToken(token) -> Unit
                    token.startsWith("##") -> builder.append(token.removePrefix("##"))
                    // manga-ocr is character/wordpiece level over Japanese: concatenate, no spaces.
                    else -> builder.append(token)
                }
            }
            return builder.toString().trim()
        }

        private fun isSpecialToken(token: String): Boolean =
            token.length >= 2 && token.startsWith("[") && token.endsWith("]")
    }

    private var encoderLoaded = false
    private var decoderLoaded = false
    private var vocabLoaded = false
    private var encoderPath: String? = null
    private var decoderPath: String? = null
    private var vocabPath: String? = null
    private var vocab: Map<Int, String>? = null
    private var sepTokenId: Int = EOS_TOKEN_ID

    fun loadModel(encoderPath: String, decoderPath: String, vocabPath: String): Boolean {
        this.encoderPath = encoderPath
        this.decoderPath = decoderPath
        this.vocabPath = vocabPath

        encoderLoaded = onnxRuntime.loadModel(encoderPath)
        decoderLoaded = onnxRuntime.loadModel(decoderPath)
        vocabLoaded = loadVocab(vocabPath)

        return encoderLoaded && decoderLoaded && vocabLoaded
    }

    fun isModelLoaded(): Boolean = encoderLoaded && decoderLoaded && vocabLoaded

    fun extractText(region: Bitmap): OcrResult? {
        if (!isModelLoaded()) return null
        val encoderPath = encoderPath ?: return null
        val decoderPath = decoderPath ?: return null
        val vocab = vocab ?: return null

        val pixelValues = preprocessImage(region)
        val encoderOutput = runEncoder(encoderPath, pixelValues) ?: return null
        val encoderOutputName = onnxRuntime.getOutputNames(encoderPath).firstOrNull() ?: return null
        val encoderOutputShape = resolveDynamicShape(
            shape = onnxRuntime.getOutputShape(encoderPath, encoderOutputName),
            values = encoderOutput,
            fallback = longArrayOf(1L, 197L, 192L)
        )

        val text = runDecoder(decoderPath, encoderOutput, encoderOutputShape, vocab)

        return OcrResult(
            text = text,
            confidence = 1.0f,
            boundingBox = floatArrayOf(
                region.width.toFloat(),
                region.height.toFloat(),
                0f,
                0f
            )
        )
    }

    private fun preprocessImage(bitmap: Bitmap): FloatArray {
        val resized = Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, true)
        val floatArray = FloatArray(CHANNELS * INPUT_SIZE * INPUT_SIZE)
        val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
        resized.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)

        val planeSize = INPUT_SIZE * INPUT_SIZE
        for (i in pixels.indices) {
            val pixel = pixels[i]
            val r = ((pixel shr 16) and 0xFF) / 255.0f
            val g = ((pixel shr 8) and 0xFF) / 255.0f
            val b = (pixel and 0xFF) / 255.0f

            floatArray[i] = normalize(r)
            floatArray[i + planeSize] = normalize(g)
            floatArray[i + 2 * planeSize] = normalize(b)
        }

        return floatArray
    }

    private fun normalize(value: Float): Float {
        return (value - NORMALIZATION_MEAN) / NORMALIZATION_STD
    }

    private fun runEncoder(encoderPath: String, pixelValues: FloatArray): FloatArray? {
        val inputNames = onnxRuntime.getInputNames(encoderPath)
        if (inputNames.isEmpty()) return null

        val inputs = mutableMapOf<String, FloatArray>()
        val inputShapes = mutableMapOf<String, LongArray>()
        val inputShape = longArrayOf(1, CHANNELS.toLong(), INPUT_SIZE.toLong(), INPUT_SIZE.toLong())

        for (name in inputNames) {
            if (isPixelValuesInput(name)) {
                inputs[name] = pixelValues
                inputShapes[name] = inputShape
            } else {
                val shape = onnxRuntime.getInputShape(encoderPath, name) ?: inputShape
                inputs[name] = FloatArray(shape.totalElements()) { 0f }
                inputShapes[name] = shape
            }
        }

        val outputs = onnxRuntime.runInferenceNamed(encoderPath, inputs, inputShapes)
        return outputs?.values?.firstOrNull()
    }

    private fun runDecoder(
        decoderPath: String,
        encoderOutput: FloatArray,
        encoderOutputShape: LongArray,
        vocab: Map<Int, String>
    ): String {
        val inputNames = onnxRuntime.getInputNames(decoderPath)
        if (inputNames.isEmpty()) return ""

        val inputIds = mutableListOf<Long>(BOS_TOKEN_ID.toLong())
        val generatedIds = mutableListOf<Int>()

        for (step in 0 until MAX_LENGTH) {
            val decoderInputIds = inputIds.toLongArray()
            val seqLen = decoderInputIds.size.toLong()

            val floatInputs = mutableMapOf<String, FloatArray>()
            val floatInputShapes = mutableMapOf<String, LongArray>()
            val longInputs = mutableMapOf<String, LongArray>()
            val longInputShapes = mutableMapOf<String, LongArray>()

            val encoderSeqLen = encoderOutputShape.getOrNull(1) ?: 1L

            for (name in inputNames) {
                val isLong = onnxRuntime.isInputLong(decoderPath, name)
                if (isLong) {
                    when {
                        isDecoderInputIds(name) -> {
                            longInputs[name] = decoderInputIds
                            longInputShapes[name] = longArrayOf(1L, seqLen)
                        }
                        isEncoderAttentionMask(name) -> {
                            longInputs[name] = LongArray(encoderSeqLen.toInt()) { 1L }
                            longInputShapes[name] = longArrayOf(1L, encoderSeqLen)
                        }
                        isDecoderAttentionMask(name) -> {
                            longInputs[name] = LongArray(seqLen.toInt()) { 1L }
                            longInputShapes[name] = longArrayOf(1L, seqLen)
                        }
                        else -> {
                            longInputs[name] = LongArray(seqLen.toInt()) { 1L }
                            longInputShapes[name] = longArrayOf(1L, seqLen)
                        }
                    }
                } else {
                    if (isEncoderHiddenStates(name)) {
                        floatInputs[name] = encoderOutput
                        floatInputShapes[name] = encoderOutputShape
                    } else {
                        val shape = onnxRuntime.getInputShape(decoderPath, name) ?: encoderOutputShape
                        floatInputs[name] = FloatArray(shape.totalElements()) { 0f }
                        floatInputShapes[name] = shape
                    }
                }
            }

            val outputs = onnxRuntime.runInferenceMixed(
                decoderPath,
                floatInputs,
                floatInputShapes,
                longInputs,
                longInputShapes
            ) ?: break

            val outputName = outputs.keys.firstOrNull() ?: break
            val logits = outputs[outputName] ?: break
            val currentSeqLen = seqLen.toInt()
            val currentVocabSize = if (currentSeqLen > 0 && logits.size % currentSeqLen == 0) {
                logits.size / currentSeqLen
            } else {
                vocab.size
            }

            val lastTimestepStart = (currentSeqLen - 1) * currentVocabSize
            if (lastTimestepStart + currentVocabSize > logits.size) break

            val nextTokenId = argmax(logits, lastTimestepStart, currentVocabSize)

            if (nextTokenId == sepTokenId) break
            generatedIds.add(nextTokenId)
            inputIds.add(nextTokenId.toLong())
        }

        return decodeTokens(generatedIds, vocab)
    }

    private fun argmax(values: FloatArray, start: Int, count: Int): Int {
        var bestIndex = 0
        var bestValue = Float.NEGATIVE_INFINITY
        for (i in 0 until count) {
            val value = values[start + i]
            if (value > bestValue) {
                bestValue = value
                bestIndex = i
            }
        }
        return bestIndex
    }

    private fun loadVocab(vocabPath: String): Boolean {
        return try {
            val file = File(vocabPath)
            if (!file.exists()) return false
            val loadedVocab = file.readLines().mapIndexed { index, line ->
                index to line.trim()
            }.toMap()
            vocab = loadedVocab
            sepTokenId = loadedVocab.entries.firstOrNull { it.value == SEP_TOKEN }?.key ?: EOS_TOKEN_ID
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun resolveDynamicShape(
        shape: LongArray?,
        values: FloatArray,
        fallback: LongArray
    ): LongArray {
        val resolved = (shape ?: fallback).copyOf()
        for (index in resolved.indices) {
            if (resolved[index] <= 0L) {
                resolved[index] = fallback.getOrNull(index) ?: 1L
            }
        }
        val resolvedElements = resolved.totalElements()
        if (resolvedElements == values.size) {
            return resolved
        }
        if (resolved.size == 3 && resolved[1] > 0L && resolved[2] > 0L) {
            val trailing = resolved[1] * resolved[2]
            if (trailing > 0L && values.size % trailing == 0L) {
                resolved[0] = values.size / trailing
            }
        }
        return resolved
    }

    private fun isPixelValuesInput(name: String): Boolean {
        return name == "pixel_values" || name.contains("pixel")
    }

    private fun isDecoderInputIds(name: String): Boolean {
        return name == "decoder_input_ids" || name.contains("input_ids")
    }

    private fun isEncoderHiddenStates(name: String): Boolean {
        return name == "encoder_hidden_states" || name.contains("encoder") || name.contains("hidden_states")
    }

    private fun isEncoderAttentionMask(name: String): Boolean {
        return name == "encoder_attention_mask" || (name.contains("attention_mask") && name.contains("encoder"))
    }

    private fun isDecoderAttentionMask(name: String): Boolean {
        return name == "decoder_attention_mask" || (name.contains("attention_mask") && name.contains("decoder"))
    }

    private fun LongArray.totalElements(): Int {
        var total = 1L
        for (dim in this) {
            total *= dim.coerceAtLeast(1L)
        }
        return total.toInt()
    }

    fun release() {
        encoderPath?.let { onnxRuntime.releaseModel(it) }
        decoderPath?.let { onnxRuntime.releaseModel(it) }
        encoderPath = null
        decoderPath = null
        vocabPath = null
        vocab = null
        encoderLoaded = false
        decoderLoaded = false
        vocabLoaded = false
    }
}
