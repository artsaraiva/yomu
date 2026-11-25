package com.yomu.pipeline.ocr

import kotlin.math.exp

// Safety cap on decoder steps, not the expected length: generation stops at the stop token, so short bubbles end
// early and only genuinely long ones use the budget. Hayai's own host loop uses the same 128.
private const val MAX_LENGTH = 128

/** One greedy step: the highest-scoring token and the probability softmax gives it. */
internal data class TokenChoice(val id: Int, val probability: Float)

/** A greedy decode's tokens, without the stop token, and its mean [TokenChoice.probability]; 0 when no step ran. */
internal data class GreedyDecode(val tokenIds: List<Int>, val confidence: Float)

/**
 * Greedy decode, one token per [step] until [isStop] accepts one or the step budget runs out. Each step gets the
 * token the previous step chose; the first gets null, since its token comes from the prefill. A null step ends the
 * decode with what it has.
 */
internal fun decodeGreedy(isStop: (Int) -> Boolean, step: (previous: Int?) -> TokenChoice?): GreedyDecode {
    var previous: Int? = null
    val generatedIds = mutableListOf<Int>()
    val probabilities = mutableListOf<Float>()
    fun decoded() = GreedyDecode(generatedIds, if (probabilities.isEmpty()) 0f else probabilities.average().toFloat())
    repeat(MAX_LENGTH) {
        val next = step(previous) ?: return decoded()
        probabilities.add(next.probability)
        if (isStop(next.id)) return decoded()
        generatedIds.add(next.id)
        previous = next.id
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
