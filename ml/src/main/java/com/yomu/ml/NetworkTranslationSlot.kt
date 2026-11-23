package com.yomu.ml

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.yomu.core.GenerationParams
import com.yomu.core.PageTranslation
import com.yomu.core.TranslatablePage
import com.yomu.core.TranslationOutcome
import com.yomu.core.TranslationSlot
import com.yomu.core.TranslationStatus
import com.yomu.core.withinBounds
import java.util.concurrent.TimeUnit.MILLISECONDS
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSource

/**
 * Where the network translator sends a page, and how the server samples (ADR-0023). Read again for
 * every page, so a change takes effect from the next page on.
 */
data class NetworkTranslatorConfig(
    /** The server's address, with or without a trailing `/v1`. */
    val baseUrl: String,
    /** Sent as `Authorization: Bearer` when not blank. */
    val apiKey: String,
    /** The model name the server knows, as its `/v1/models` lists it. */
    val model: String,
    val generation: GenerationParams = GenerationParams()
) {
    /** Leaves out the key and the address: neither may reach a log or a page report. */
    override fun toString(): String = "NetworkTranslatorConfig(model=$model, generation=$generation)"
}

/**
 * The network translator: translates a page on an OpenAI-compatible server with one streamed
 * `/v1/chat/completions` request (ADR-0023). It sends the on-device batch's id-keyed prompt and reads
 * the reply with the same line parse, without the grammar.
 */
class NetworkTranslationSlot(
    client: OkHttpClient,
    private val config: () -> NetworkTranslatorConfig,
    pageTimeoutMs: Long = PAGE_TIMEOUT_MS.toLong()
) : TranslationSlot {

    private val client = client.newBuilder()
        .callTimeout(pageTimeoutMs, MILLISECONDS)
        .build()

    /** Always ready: there is nothing to load, and whether a server is set up is the translator's question. */
    override val status: TranslationStatus = TranslationStatus.Ready

    override suspend fun ensureReady(): Boolean = true

    override suspend fun translatePage(
        page: TranslatablePage,
        onBubble: (bubbleId: Int, text: String) -> Unit
    ): PageTranslation {
        val started = System.nanoTime()
        val reply = IdKeyedReplyReader(page, onBubble)
        val request = request(config(), page)
        val text = withContext(Dispatchers.IO) {
            client.newCall(request).execute().use { response -> response.body?.let { readStream(it.source(), reply) } }
        }.orEmpty().trim()
        return PageTranslation(reply.byId, text, (System.nanoTime() - started) / NANOS_PER_MS, TranslationOutcome.SUCCESS)
    }

    private fun request(config: NetworkTranslatorConfig, page: TranslatablePage): Request {
        val message = JsonObject().apply {
            addProperty("role", "user")
            addProperty("content", idKeyedPagePrompt(page))
        }
        // Re-checked here, as at the on-device boundary: a caller that bypasses the store must not send an unsafe value (#192).
        val generation = config.generation.withinBounds()
        val body = JsonObject().apply {
            addProperty("model", config.model)
            add("messages", JsonArray().apply { add(message) })
            addProperty("stream", true)
            // Top-k and the penalties are not part of the protocol, and a server may reject them (ADR-0023).
            addProperty("temperature", generation.temperature)
            addProperty("top_p", generation.topP)
            addProperty("max_tokens", MAX_REPLY_TOKENS)
        }
        return Request.Builder()
            .url(chatCompletions(config.baseUrl))
            .post(body.toString().toRequestBody(JSON))
            .build()
    }

    /** `{base}/v1/chat/completions`, whether or not the reader's address already ends in `/v1`. */
    private fun chatCompletions(baseUrl: String): HttpUrl {
        val base = requireNotNull(baseUrl.toHttpUrlOrNull())
        val segments = base.pathSegments.filter { it.isNotEmpty() }
        val path = if (segments.lastOrNull() == "v1") segments else segments + "v1"
        return base.newBuilder()
            .encodedPath("/")
            .apply { (path + listOf("chat", "completions")).forEach(::addPathSegment) }
            .build()
    }

    /** Feeds each streamed piece of reply text to [reply] and returns the whole text. */
    private fun readStream(source: BufferedSource, reply: IdKeyedReplyReader): String {
        val text = StringBuilder()
        while (true) {
            val line = source.readUtf8Line() ?: break
            if (!line.startsWith(DATA)) continue
            val data = line.removePrefix(DATA).trim()
            if (data == DONE) break
            val choice = JsonParser().parse(data).asJsonObject.getAsJsonArray("choices").first().asJsonObject
            val content = choice.getAsJsonObject("delta").get("content").stringOrNull() ?: continue
            text.append(content)
            reply.feed(content.toByteArray())
        }
        reply.finish()
        return text.toString()
    }

    private fun JsonElement?.stringOrNull(): String? = if (this == null || isJsonNull) null else asString

    override fun endSession() = Unit

    override fun close() = Unit

    private companion object {
        val JSON = "application/json".toMediaType()

        /** Above the phone's 768: a reasoning model spends tokens thinking before it answers (ADR-0023). */
        const val MAX_REPLY_TOKENS = 4096
        const val DATA = "data:"
        const val DONE = "[DONE]"
        const val NANOS_PER_MS = 1_000_000L
    }
}
