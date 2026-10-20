package com.yomu.pipeline.ocr

import kotlin.math.exp

// Safety cap on decoder steps, not the expected length: generation stops at the real [SEP]
// (resolved from the vocab, see OcrEngine.sepTokenId), so short bubbles end early and only genuinely
// long ones use the budget. 32 truncated lines longer than the 36-char annotated max (#82).
private const val MAX_LENGTH = 128
// [CLS], the model's decoder_start_token_id. Starting from [PAD] (0) garbled hard reads (#365).
private const val DECODER_START_TOKEN_ID = 2L

/** One greedy step: the highest-scoring token and the probability softmax gives it. */
internal data class TokenChoice(val id: Int, val probability: Float)

/** A greedy decode's tokens, without the stop token, and its mean [TokenChoice.probability]; 0 when no step ran. */
internal data class GreedyDecode(val tokenIds: List<Int>, val confidence: Float)

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

internal fun decodeGreedy(stopTokenId: Int, nextToken: (LongArray) -> TokenChoice?): GreedyDecode {
    val inputIds = mutableListOf(DECODER_START_TOKEN_ID)
    val generatedIds = mutableListOf<Int>()
    val probabilities = mutableListOf<Float>()
    fun decoded() = GreedyDecode(generatedIds, if (probabilities.isEmpty()) 0f else probabilities.average().toFloat())
    repeat(MAX_LENGTH) {
        val choice = nextToken(inputIds.toLongArray()) ?: return decoded()
        probabilities.add(choice.probability)
        if (choice.id == stopTokenId) return decoded()
        generatedIds.add(choice.id)
        inputIds.add(choice.id.toLong())
    }
    return decoded()
}

/** The argmax of [count] logits from [start], with its softmax probability over the same span. */
internal fun chooseToken(logits: FloatArray, start: Int, count: Int): TokenChoice {
    var bestIndex = 0
    var bestValue = Float.NEGATIVE_INFINITY
    for (i in 0 until count) {
        val value = logits[start + i]
        if (value > bestValue) {
            bestValue = value
            bestIndex = i
        }
    }
    var sum = 0.0
    for (i in 0 until count) sum += exp((logits[start + i] - bestValue).toDouble())
    return TokenChoice(bestIndex, (1.0 / sum).toFloat())
}

private fun isSpecialToken(token: String): Boolean =
    token.length >= 2 && token.startsWith("[") && token.endsWith("]")
