package com.yomu.ml.opusmt

import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer
import com.yomu.ml.OnnxRuntime
import java.io.Closeable
import java.io.File
import java.nio.file.Paths

class OpusMtTranslator(
    private val onnxRuntime: OnnxRuntime,
    private val encoderModelPath: String,
    private val decoderModelPath: String,
    private val tokenizerPath: String
) : Closeable {

    companion object {
        private const val MAX_LENGTH = 512
        private const val PAD_TOKEN_ID: Long = 60715L
        private const val EOS_TOKEN_ID: Long = 0L
    }

    private var tokenizer: HuggingFaceTokenizer? = null
    private var isLoaded = false

    private var encoderInputIdsName: String = "input_ids"
    private var encoderAttentionMaskName: String = "attention_mask"
    private var encoderOutputName: String = ""

    private var decoderInputIdsName: String = "input_ids"
    private var decoderAttentionMaskName: String = "attention_mask"
    private var encoderHiddenStatesName: String = "encoder_hidden_states"
    private var decoderEncoderAttentionMaskName: String = "encoder_attention_mask"
    private var logitsOutputName: String = "logits"

    private var pastInputNames: List<String> = emptyList()
    private var presentOutputNames: List<String> = emptyList()
    private var kvStaticDims: List<Pair<Int, Int>> = emptyList()

    fun load(): Boolean {
        if (isLoaded) return true

        val encoderFile = File(encoderModelPath)
        val decoderFile = File(decoderModelPath)
        val tokenizerFile = File(tokenizerPath)
        if (!encoderFile.exists() || !decoderFile.exists() || !tokenizerFile.exists()) return false

        val onnxLoaded = onnxRuntime.loadModel(encoderModelPath) && onnxRuntime.loadModel(decoderModelPath)
        if (!onnxLoaded) return false

        val tokenizer = try {
            HuggingFaceTokenizer.newInstance(Paths.get(tokenizerPath))
        } catch (_: Exception) {
            return false
        }

        discoverEncoderMetadata()
        discoverDecoderMetadata()

        this.tokenizer = tokenizer
        isLoaded = true
        return true
    }

    private fun discoverEncoderMetadata() {
        val inputs = onnxRuntime.getInputNames(encoderModelPath)
        encoderInputIdsName = inputs.firstOrNull { it == "input_ids" } ?: inputs.firstOrNull().orEmpty()
        encoderAttentionMaskName = inputs.firstOrNull { it == "attention_mask" }
            ?: inputs.firstOrNull { it != encoderInputIdsName }.orEmpty()

        val outputs = onnxRuntime.getOutputNames(encoderModelPath)
        encoderOutputName = outputs.firstOrNull { it.contains("hidden", ignoreCase = true) }
            ?: outputs.firstOrNull().orEmpty()
    }

    private fun discoverDecoderMetadata() {
        val inputs = onnxRuntime.getInputNames(decoderModelPath)
        decoderInputIdsName = inputs.firstOrNull { it == "input_ids" } ?: inputs.firstOrNull().orEmpty()
        decoderAttentionMaskName = inputs.firstOrNull { it == "attention_mask" }
            ?: inputs.firstOrNull { it != decoderInputIdsName }.orEmpty()
        encoderHiddenStatesName = inputs.firstOrNull { it.contains("encoder_hidden_states", ignoreCase = true) }
            ?: inputs.firstOrNull { it != decoderInputIdsName && it != decoderAttentionMaskName }.orEmpty()
        decoderEncoderAttentionMaskName = inputs.firstOrNull { it.contains("encoder_attention_mask", ignoreCase = true) }
            ?: inputs.firstOrNull {
                it != decoderInputIdsName && it != decoderAttentionMaskName && it != encoderHiddenStatesName
            }.orEmpty()

        pastInputNames = inputs.filter { it.contains("past", ignoreCase = true) }.sorted()
        val outputs = onnxRuntime.getOutputNames(decoderModelPath)
        presentOutputNames = outputs.filter { it.contains("present", ignoreCase = true) }.sorted()

        logitsOutputName = outputs.firstOrNull { it.contains("logits", ignoreCase = true) }
            ?: outputs.firstOrNull { it !in presentOutputNames }.orEmpty()

        kvStaticDims = pastInputNames.map { name ->
            val shape = onnxRuntime.getInputShape(decoderModelPath, name) ?: return@map 0 to 0
            val heads = shape.getOrNull(1)?.toInt()?.takeIf { it > 0 } ?: 0
            val headSize = shape.getOrNull(3)?.toInt()?.takeIf { it > 0 } ?: 0
            heads to headSize
        }
    }

    fun encode(text: String): Pair<LongArray, LongArray> {
        val t = tokenizer ?: throw IllegalStateException("Tokenizer not loaded")
        val encoding = t.encode(text)
        val ids = encoding.ids.toMutableList()
        ids.add(EOS_TOKEN_ID)
        val inputIds = ids.toLongArray()
        val mask = LongArray(inputIds.size) { 1L }
        return inputIds to mask
    }

    fun decode(ids: LongArray): String {
        val t = tokenizer ?: throw IllegalStateException("Tokenizer not loaded")
        return t.decode(ids, true).trim()
    }

    fun translate(inputIds: LongArray, attentionMask: LongArray): LongArray? {
        if (!isLoaded) return null
        val encoderOutput = runEncoder(inputIds, attentionMask) ?: return null
        val (hiddenStates, hiddenShape) = encoderOutput
        val sourceLength = hiddenShape[1]

        val stepper = object : DecoderStep {
            override fun invoke(stepInput: DecoderStepInput): Pair<Long, List<PresentCache>>? {
                val decoderMask = LongArray(stepInput.step + 1) { 1L }
                val currentInputIds = longArrayOf(stepInput.inputId)
                val pastInputs = stepInput.pastCaches.mapIndexed { index, cache ->
                    PastInput(pastInputNames[index], cache.data, cache.shape)
                }

                val outputs = runDecoder(
                    inputIds = currentInputIds,
                    attentionMask = decoderMask,
                    encoderHiddenStates = hiddenStates,
                    encoderHiddenStatesShape = hiddenShape,
                    encoderAttentionMask = attentionMask,
                    encoderAttentionMaskShape = longArrayOf(1, sourceLength),
                    pastInputs = pastInputs
                ) ?: return null

                val logits = outputs[logitsOutputName] ?: return null
                val tokenId = argmaxLast(logits)
                val presentCaches = presentOutputNames.mapNotNull { name ->
                    val data = outputs[name] ?: return@mapNotNull null
                    val shape = computePresentShape(data.size, name)
                    PresentCache(data, shape)
                }
                return tokenId to presentCaches
            }
        }

        return OpusMtDecoder.greedyDecode(MAX_LENGTH, PAD_TOKEN_ID, EOS_TOKEN_ID, stepper)
    }

    private fun runEncoder(inputIds: LongArray, attentionMask: LongArray): Pair<FloatArray, LongArray>? {
        val longInputs = mapOf(
            encoderInputIdsName to inputIds,
            encoderAttentionMaskName to attentionMask
        )
        val longShapes = mapOf(
            encoderInputIdsName to longArrayOf(1, inputIds.size.toLong()),
            encoderAttentionMaskName to longArrayOf(1, attentionMask.size.toLong())
        )
        val outputs = onnxRuntime.runInferenceMixed(
            encoderModelPath,
            emptyMap(),
            emptyMap(),
            longInputs,
            longShapes
        ) ?: return null
        val data = outputs[encoderOutputName] ?: return null
        val shape = computeEncoderOutputShape(data.size)
        return data to shape
    }

    private data class PastInput(
        val name: String,
        val data: FloatArray,
        val shape: LongArray
    )

    private fun runDecoder(
        inputIds: LongArray,
        attentionMask: LongArray,
        encoderHiddenStates: FloatArray,
        encoderHiddenStatesShape: LongArray,
        encoderAttentionMask: LongArray,
        encoderAttentionMaskShape: LongArray,
        pastInputs: List<PastInput>
    ): Map<String, FloatArray>? {
        val floatInputs = mutableMapOf<String, FloatArray>()
        val floatShapes = mutableMapOf<String, LongArray>()
        val longInputs = mutableMapOf(
            decoderInputIdsName to inputIds,
            decoderAttentionMaskName to attentionMask,
            decoderEncoderAttentionMaskName to encoderAttentionMask
        )
        val longShapes = mutableMapOf(
            decoderInputIdsName to longArrayOf(1, inputIds.size.toLong()),
            decoderAttentionMaskName to longArrayOf(1, attentionMask.size.toLong()),
            decoderEncoderAttentionMaskName to encoderAttentionMaskShape
        )

        floatInputs[encoderHiddenStatesName] = encoderHiddenStates
        floatShapes[encoderHiddenStatesName] = encoderHiddenStatesShape

        for (past in pastInputs) {
            floatInputs[past.name] = past.data
            floatShapes[past.name] = past.shape
        }

        return onnxRuntime.runInferenceMixed(
            decoderModelPath,
            floatInputs,
            floatShapes,
            longInputs,
            longShapes
        )
    }

    private fun computeEncoderOutputShape(totalSize: Int): LongArray {
        val shape = onnxRuntime.getOutputShape(encoderModelPath, encoderOutputName)
        val hiddenSize = shape?.last()?.toInt()?.takeIf { it > 0 } ?: return longArrayOf(1, 1, 1)
        val seqLen = totalSize / hiddenSize
        return longArrayOf(1, seqLen.toLong(), hiddenSize.toLong())
    }

    private fun argmaxLast(logits: FloatArray): Long {
        val shape = onnxRuntime.getOutputShape(decoderModelPath, logitsOutputName)
        val vocabSize = shape?.last()?.toInt()?.takeIf { it > 0 } ?: logits.size
        val seqLen = logits.size / vocabSize
        val offset = (seqLen - 1).coerceAtLeast(0) * vocabSize
        var bestIndex = 0
        var bestScore = logits[offset]
        for (i in 1 until vocabSize) {
            val score = logits[offset + i]
            if (score > bestScore) {
                bestScore = score
                bestIndex = i
            }
        }
        return bestIndex.toLong()
    }

    private fun computePresentShape(totalSize: Int, name: String): LongArray {
        val index = presentOutputNames.indexOf(name)
        val (heads, headSize) = kvStaticDims.getOrNull(index) ?: (0 to 0)
        if (heads <= 0 || headSize <= 0) return longArrayOf(1, 1, 1, 1)
        val seqLen = totalSize / (heads * headSize)
        return longArrayOf(1, heads.toLong(), seqLen.toLong(), headSize.toLong())
    }

    override fun close() {
        tokenizer?.close()
        tokenizer = null
        onnxRuntime.releaseModel(encoderModelPath)
        onnxRuntime.releaseModel(decoderModelPath)
        isLoaded = false
    }
}
