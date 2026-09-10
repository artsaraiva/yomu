package com.yomu.ml

import android.util.Log
import com.yomu.core.ModelProfile
import com.yomu.core.PageTranslation
import com.yomu.core.TranslatableBubble
import com.yomu.core.TranslatablePage
import com.yomu.core.TranslationPromptMode
import com.yomu.core.TranslationSlot
import com.yomu.core.TranslationStatus
import java.io.File
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class LlamaTranslationBridge(
    private val llamaBridge: LlamaBridge,
    profile: ModelProfile,
    /**
     * SPIKE #137: constrain the page-level batch reply to the `[id] text` shape at sample time.
     * Off by default, so every existing construction is the shipped path unchanged. Deliberately a
     * constructor flag rather than a [ModelProfile] field: this is a throwaway measurement arm, not
     * a catalog capability, and the architecture ticket decides whether it becomes one.
     */
    private val useGrammar: Boolean = false
) : TranslationSlot {

    @Volatile
    private var profile: ModelProfile = profile

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
        private const val MAX_TOKENS = 256
        private const val BATCH_TOKEN_RESERVE = 96
        private const val BATCH_MIN_TOKENS = 256

        // #136: the old budget was "all of N_CTX the prompt does not use", which is fed to the
        // native prompt_fits as `output` and so *shrinks* the prompt allowance to N_CTX - budget - 8
        // -- the model self-refusing prompts that would have fit. Cap it at what a page's reply
        // actually needs (~30 bubbles x ~15 tokens plus id tags) and the binding limit goes back to
        // being n_batch, where #136 says it belongs.
        private const val MAX_BATCH_OUTPUT = 768

        // An unbounded `line` rule is not a neutral choice: with no repetition penalty in the
        // sampler, the model finishes the last id and then keeps writing inside that same line
        // forever -- observed on 5 of 17 pages, each running to the token cap with the final
        // bubble's text turned into a loop. The bound is the escape hatch the grammar itself has to
        // provide. 160 is ~2x the longest line in the corpus's human reference translations (max
        // 75 chars, median 22), so it cannot clip a plausible translation.
        private const val MAX_LINE_CHARS = 160
        private const val TEMPERATURE = 0.2f
        private const val TIMEOUT_MS = 15_000
        private const val BATCH_TIMEOUT_MS = 120_000
        private const val NEIGHBOUR_CHARS = 80
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
            return PageTranslation(emptyMap(), "", 0L)
        }
        return if (profile.idKeyedBatch) translateBatch(page, sessionContext) else translatePerLine(page)
    }

    private suspend fun translatePerLine(page: TranslatablePage): PageTranslation {
        val bubbles = page.panels.flatten()
        val outputs = bubbles.mapIndexedNotNull { index, bubble ->
            val prompt = when (profile.promptMode) {
                TranslationPromptMode.MODEL_CARD -> modelCardPrompt(bubble.sourceText)
                TranslationPromptMode.TRANSLATION_ONLY -> translationOnlyPrompt(bubble.sourceText, "")
                TranslationPromptMode.CAPTURE_CONTEXT -> translationOnlyPrompt(
                    bubble.sourceText,
                    listOfNotNull(bubbles.getOrNull(index - 1), bubbles.getOrNull(index + 1))
                        .joinToString("\n") { it.sourceText.take(NEIGHBOUR_CHARS) }
                )
            }
            generate(prompt, MAX_TOKENS, TIMEOUT_MS)?.let { bubble to it }
        }
        return PageTranslation(
            byId = outputs.associate { (bubble, output) -> bubble.bubbleId to output.text },
            rawResponse = outputs.joinToString("\n") { (bubble, output) ->
                "[${bubble.bubbleId}] ${output.text}"
            },
            durationMs = outputs.sumOf { it.second.durationMs }
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
            .coerceIn(BATCH_MIN_TOKENS, MAX_BATCH_OUTPUT)
        val grammar = if (useGrammar) buildBatchGrammar(page) else ""
        val output = generate(prompt, budget, BATCH_TIMEOUT_MS, grammar)
            ?: return PageTranslation(emptyMap(), "", 0L)
        return PageTranslation(
            byId = parseIdKeyedTranslations(output.text),
            rawResponse = output.text,
            durationMs = output.durationMs
        )
    }

    private fun modelCardPrompt(target: String): String =
        "Translate the following Japanese text into English.\n\n$target"

    private fun translationOnlyPrompt(target: String, surrounding: String): String = buildString {
        appendLine("Translate the target Japanese manga text into natural English. Return only the translation.")
        appendLine("No introductions, explanations, labels, or added quotes. Preserve meaning and tone.")
        if (surrounding.isNotBlank()) {
            appendLine("Nearby dialogue (context only; do not translate):")
            appendLine(surrounding)
        }
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

    /**
     * GBNF pinning the reply to exactly one `[id] text` line per bubble, in prompt order, with the
     * ids as literals. Two rules regardless of bubble count. Preamble, refusal, a dropped id, a
     * merged pair and a re-ordered reply are all structurally unreachable -- which is what
     * [parseIdKeyedTranslations] and the [com.yomu.pipeline.translation.looksLikeNonTranslation]
     * regexes are currently detecting after the fact.
     *
     * Deliberately structural only: nothing here forbids Japanese characters or constrains content,
     * so the residue and meaning metrics still measure the model rather than the grammar.
     */
    private fun buildBatchGrammar(page: TranslatablePage): String {
        val ids = page.panels.flatten().map { it.bubbleId }
        val root = ids.joinToString(" ") { "\"[$it] \" line \"\\n\"" }
        return "root ::= $root\nline ::= [^\\r\\n]{1,$MAX_LINE_CHARS}\n"
    }

    private fun parseIdKeyedTranslations(response: String): Map<Int, String> {
        val regex = "^\\s*\\[(\\d+)]\\s*(.*)$".toRegex()
        return response.lineSequence().mapNotNull { line ->
            val match = regex.matchEntire(line) ?: return@mapNotNull null
            val id = match.groupValues[1].toIntOrNull() ?: return@mapNotNull null
            id to match.groupValues[2].trim()
        }.toMap()
    }

    private suspend fun generate(
        prompt: String,
        maxTokens: Int,
        timeoutMs: Int,
        grammar: String = ""
    ): GeneratedText? = readinessMutex.withLock {
        return@withLock when (
            val result = llamaBridge.generate(prompt, maxTokens, TEMPERATURE, timeoutMs, grammar)
        ) {
            is GenerationResult.Success -> {
                val text = result.text.trim()
                if (text.isBlank()) {
                    Log.w(TAG, "generate blank promptLength=${prompt.length}")
                    null
                } else {
                    Log.i(
                        TAG,
                        "generate success promptLength=${prompt.length} translatedLength=${text.length} " +
                            "maxTokens=$maxTokens durationMs=${result.durationMs}"
                    )
                    Log.i(TAG, "generate raw=${text.replace("\n", "\\n")}")
                    GeneratedText(text, result.durationMs)
                }
            }
            is GenerationResult.Blank,
            is GenerationResult.Error,
            is GenerationResult.NotLoaded -> null
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

    private data class GeneratedText(val text: String, val durationMs: Long)
}
