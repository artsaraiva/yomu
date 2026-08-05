package com.yomu.ml.opusmt

internal data class PresentCache(
    val data: FloatArray,
    val shape: LongArray
)

internal data class DecoderStepInput(
    val inputId: Long,
    val step: Int,
    val pastCaches: List<PresentCache>
)

internal fun interface DecoderStep {
    operator fun invoke(stepInput: DecoderStepInput): Pair<Long, List<PresentCache>>?
}

internal object OpusMtDecoder {
    fun greedyDecode(
        maxLength: Int,
        startTokenId: Long,
        eosTokenId: Long,
        step: DecoderStep
    ): LongArray? {
        val generated = mutableListOf<Long>()
        var currentInputId = startTokenId
        var pastCaches = emptyList<PresentCache>()

        for (stepIndex in 0 until maxLength) {
            val result = step(DecoderStepInput(currentInputId, stepIndex, pastCaches)) ?: return null
            val tokenId = result.first
            generated.add(tokenId)
            if (tokenId == eosTokenId) break
            pastCaches = result.second
            currentInputId = tokenId
        }
        return generated.toLongArray()
    }
}
