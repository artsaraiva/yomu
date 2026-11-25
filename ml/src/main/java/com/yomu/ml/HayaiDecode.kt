package com.yomu.ml

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.util.Log
import java.nio.LongBuffer

/**
 * One crop's decode with Hayai's two graphs (#510): the prefill read the image and gave [firstLogits]; each [step]
 * sends one token through the decode graph with the key/value cache, which stays in native memory between steps.
 * Close it after the decode; that frees every step's results.
 */
class HayaiDecode internal constructor(
    private val env: OrtEnvironment,
    private val decode: OrtSession,
    prefill: OrtSession.Result
) : AutoCloseable {

    /** The prefill's logits for the first generated token. */
    val firstLogits: FloatArray = readFloatBuffer(prefill.tensor("logits").floatBuffer)

    // The prefill marks which of its padded image positions are real; the start token and each generated one add one.
    private val mask = readLongBuffer(prefill.tensor("vis_valid").longBuffer).toMutableList()
    private var keys = prefill.tensor("k_cache")
    private var values = prefill.tensor("v_cache")
    private val results = mutableListOf(prefill)
    private var position = 0L

    /** Logits for the token after [token], the one the previous step (or the prefill) chose; null on failure. */
    fun step(token: Long): FloatArray? = try {
        mask += 1L
        position += 1
        val inputs = mapOf(
            "input_ids" to OnnxTensor.createTensor(env, LongBuffer.wrap(longArrayOf(token)), longArrayOf(1, 1)),
            "past_attention_mask" to OnnxTensor.createTensor(env, LongBuffer.wrap(mask.toLongArray()), longArrayOf(1, mask.size.toLong())),
            "position_id" to OnnxTensor.createTensor(env, LongBuffer.wrap(longArrayOf(position)), longArrayOf(1))
        )
        try {
            val result = decode.run(inputs + mapOf("k_cache_in" to keys, "v_cache_in" to values))
            results += result
            keys = result.tensor("k_cache_out")
            values = result.tensor("v_cache_out")
            // This step read the previous result's cache; anything older is no longer referenced.
            while (results.size > 2) results.removeAt(0).close()
            readFloatBuffer(result.tensor("logits").floatBuffer)
        } finally {
            inputs.values.forEach { it.close() }
        }
    } catch (e: Exception) {
        Log.e(TAG, "Hayai decode step failed: ${e.message}", e)
        null
    }

    override fun close() {
        results.forEach { it.close() }
        results.clear()
    }

    private fun OrtSession.Result.tensor(name: String): OnnxTensor = get(name).get() as OnnxTensor

    private companion object {
        const val TAG = "HayaiDecode"
    }
}
