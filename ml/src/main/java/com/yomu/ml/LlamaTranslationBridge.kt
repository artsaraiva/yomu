package com.yomu.ml

import android.util.Log
import com.yomu.core.ModelProfile
import com.yomu.core.PageTranslation
import com.yomu.core.TranslatableBubble
import com.yomu.core.TranslatablePage
import com.yomu.core.TranslationOutcome
import com.yomu.core.TranslationPromptMode
import com.yomu.core.TranslationSlot
import com.yomu.core.TranslationStatus
import java.io.File
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class LlamaTranslationBridge(
    private val llamaBridge: LlamaBridge,
    profile: ModelProfile
) : TranslationSlot {

    @Volatile
    private var profile: ModelProfile = profile

    /**
     * The profile this slot is actually running, read at the execution boundary.
     *
     * The eval records the call shape and generation settings from here rather than from whatever
     * the harness believes it configured: manifest-only call-shape metadata is the provenance
     * mistake ADR-0010 corrected, and #142 requires expected and observed to be independent.
     */
    val activeProfile: ModelProfile get() = profile

    suspend fun selectModel(newProfile: ModelProfile) = readinessMutex.withLock {
        if (newProfile == profile) return@withLock
        if (newProfile.modelPath != profile.modelPath) {
            llamaBridge.release()
            status = TranslationStatus.NotReady
        }
        profile = newProfile
        Log.i(TAG, "selectModel switched idKeyedBatch=${newProfile.idKeyedBatch}")
    }

    companion object {
        private const val TAG = "LlamaTranslationBridge"
        private const val N_CTX = 2048
        private const val N_GPU_LAYERS = 0
        private val N_THREADS = Runtime.getRuntime().availableProcessors().coerceAtMost(4)
        private const val BATCH_TOKEN_RESERVE = 96
        private const val BATCH_MIN_TOKENS = 256
        private const val TIMEOUT_MS = 15_000
        private const val BATCH_TIMEOUT_MS = 120_000
    }

    private val readinessMutex = Mutex()

    @Volatile
    override var status: TranslationStatus = TranslationStatus.NotReady
        private set

    override suspend fun ensureReady(): Boolean = readinessMutex.withLock {
        if (status is TranslationStatus.Ready) return@withLock true
        if (!llamaBridge.isNativeAvailable) {
            status = TranslationStatus.Error("native_unavailable")
            return@withLock false
        }
        if (llamaBridge.isModelLoaded) {
            status = TranslationStatus.Ready
            return@withLock true
        }
        if (!File(profile.modelPath).exists()) {
            status = TranslationStatus.Error("model_missing")
            return@withLock false
        }
        val loaded = llamaBridge.loadModel(profile.modelPath, N_CTX, N_GPU_LAYERS, N_THREADS)
        status = if (loaded) TranslationStatus.Ready else TranslationStatus.Error("load_failed")
        loaded
    }

    override suspend fun translatePage(
        page: TranslatablePage,
        sessionContext: List<Pair<String, String>>
    ): PageTranslation {
        if (page.panels.flatten().isEmpty()) return PageTranslation(emptyMap(), "", 0L)
        if (status !is TranslationStatus.Ready && !ensureReady()) {
            return PageTranslation.notLoaded(status)
        }
        return if (profile.idKeyedBatch) translateBatch(page, sessionContext) else translatePerLine(page)
    }

    private suspend fun translatePerLine(page: TranslatablePage): PageTranslation {
        val bubbles = page.panels.flatten()
        val generated = bubbles.map { bubble ->
            val prompt = when (profile.promptMode) {
                TranslationPromptMode.MODEL_CARD -> modelCardPrompt(bubble.sourceText)
                TranslationPromptMode.TRANSLATION_ONLY -> translationOnlyPrompt(bubble.sourceText)
            }
            bubble to generate(prompt, profile.generation.maxTokens, TIMEOUT_MS)
        }
        val outputs = generated.filter { it.second.outcome == TranslationOutcome.SUCCESS }
        // The page's outcome is the first bubble-level failure, or SUCCESS when none failed. A
        // per-line page that answered some bubbles and dropped others is still a measured partial:
        // coverage is what reports it, and the outcome names why (#142).
        val failure = generated.firstOrNull { it.second.outcome != TranslationOutcome.SUCCESS }?.second
        return PageTranslation(
            byId = outputs.associate { (bubble, output) -> bubble.bubbleId to output.text },
            rawResponse = outputs.joinToString("\n") { (bubble, output) ->
                "[${bubble.bubbleId}] ${output.text}"
            },
            // Successes only, unchanged: #137 and #153 published latency rows on this definition,
            // and widening it to include failed generations would break comparability with them.
            durationMs = outputs.sumOf { it.second.durationMs },
            outcome = failure?.outcome ?: TranslationOutcome.SUCCESS,
            errorCode = failure?.errorCode
        )
    }

    private suspend fun translateBatch(
        page: TranslatablePage,
        sessionContext: List<Pair<String, String>>
    ): PageTranslation {
        val prompt = buildBatchPrompt(page, sessionContext)
        // ponytail: chars/2 avoids tokenizer overhead; use exact tokenization if dense pages overflow N_CTX.
        val promptTokenEstimate = prompt.length / 2
        val budget = (N_CTX - promptTokenEstimate - BATCH_TOKEN_RESERVE)
            .coerceIn(BATCH_MIN_TOKENS, N_CTX)
        val output = generate(prompt, budget, BATCH_TIMEOUT_MS)
        return PageTranslation(
            byId = parseIdKeyedTranslations(output.text),
            rawResponse = output.text,
            durationMs = output.durationMs,
            outcome = output.outcome,
            errorCode = output.errorCode
        )
    }

    private fun modelCardPrompt(target: String): String =
        "Translate the following Japanese text into English.\n\n$target"

    private fun translationOnlyPrompt(target: String): String = buildString {
        appendLine("Translate the target Japanese manga text into natural English. Return only the translation.")
        appendLine("No introductions, explanations, labels, or added quotes. Preserve meaning and tone.")
        appendLine("Target text (translate this only):")
        append(target)
    }

    private fun buildBatchPrompt(
        page: TranslatablePage,
        sessionContext: List<Pair<String, String>>
    ): String = buildString {
        if (sessionContext.isNotEmpty()) {
            appendLine("Previous page (for context):")
            sessionContext.forEach { (source, target) -> appendLine("$source => $target") }
            appendLine()
        }
        appendLine(
            "Translate each Japanese line to English. Keep the [id] tag before each line. " +
                "Panels are separated by ---."
        )
        page.panels.forEachIndexed { index, panel ->
            if (index > 0) appendLine("---")
            panel.forEach { bubble -> appendLine("[${bubble.bubbleId}] ${bubble.sourceText}") }
        }
    }

    private fun parseIdKeyedTranslations(response: String): Map<Int, String> {
        val regex = "^\\s*\\[(\\d+)]\\s*(.*)$".toRegex()
        return response.lineSequence().mapNotNull { line ->
            val match = regex.matchEntire(line) ?: return@mapNotNull null
            val id = match.groupValues[1].toIntOrNull() ?: return@mapNotNull null
            id to match.groupValues[2].trim()
        }.toMap()
    }

    /**
     * Generate once, carrying the typed terminal cause out with the text.
     *
     * The `when` is exhaustive on purpose: the outcome the eval records must be mapped from the
     * result type, never recovered from a log line (#142). When #149 adds `Timeout` / `Overflow` to
     * [GenerationResult] this stops compiling until they are mapped, which is the point.
     */
    private suspend fun generate(
        prompt: String,
        maxTokens: Int,
        timeoutMs: Int
    ): GeneratedText = readinessMutex.withLock {
        return@withLock when (
            val result = llamaBridge.generate(prompt, profile.generation, maxTokens, timeoutMs)
        ) {
            is GenerationResult.Success -> {
                val text = result.text.trim()
                if (text.isBlank()) {
                    Log.w(TAG, "generate blank promptLength=${prompt.length}")
                    GeneratedText("", result.durationMs, TranslationOutcome.BLANK)
                } else {
                    Log.i(
                        TAG,
                        "generate success promptLength=${prompt.length} translatedLength=${text.length} " +
                            "maxTokens=$maxTokens durationMs=${result.durationMs}"
                    )
                    Log.i(TAG, "generate raw=${text.replace("\n", "\\n")}")
                    GeneratedText(text, result.durationMs, TranslationOutcome.SUCCESS)
                }
            }
            is GenerationResult.Blank ->
                GeneratedText("", result.durationMs, TranslationOutcome.BLANK)
            is GenerationResult.Error ->
                GeneratedText("", result.durationMs, TranslationOutcome.ERROR, result.reason)
            is GenerationResult.NotLoaded ->
                GeneratedText("", result.durationMs, TranslationOutcome.NOT_LOADED, "model_not_loaded")
        }
    }

    override fun endSession() {
        llamaBridge.clearMemory()
    }

    override fun close() {
        llamaBridge.release()
        status = TranslationStatus.NotReady
        Log.i(TAG, "close completed")
    }

    private data class GeneratedText(
        val text: String,
        val durationMs: Long,
        val outcome: TranslationOutcome,
        val errorCode: String? = null
    )
}
