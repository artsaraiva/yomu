package com.yomu.ml

import android.util.Log
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import com.yomu.core.ERROR_SERVER_FAILED
import com.yomu.core.ERROR_SERVER_KEY_REJECTED
import com.yomu.core.ERROR_SERVER_MODEL_NOT_FOUND
import com.yomu.core.ERROR_SERVER_TIMEOUT
import com.yomu.core.ERROR_SERVER_UNREACHABLE
import com.yomu.core.GenerationParams
import com.yomu.core.PageTranslation
import com.yomu.core.TranslatablePage
import com.yomu.core.TranslationOutcome
import com.yomu.core.TranslationSlot
import com.yomu.core.TranslationStatus
import com.yomu.core.withinBounds
import java.io.IOException
import java.io.InterruptedIOException
import java.net.HttpURLConnection.HTTP_FORBIDDEN
import java.net.HttpURLConnection.HTTP_NOT_FOUND
import java.net.HttpURLConnection.HTTP_UNAUTHORIZED
import java.util.concurrent.TimeUnit.MILLISECONDS
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
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

    // The page limit is the only one once connected: a reasoning model may think for a minute before
    // its first token, and the client handed in may carry read and write limits of its own. No retry
    // (ADR-0023): OkHttp would otherwise send a page again when a kept-alive connection drops.
    private val client = client.newBuilder()
        .retryOnConnectionFailure(false)
        .connectTimeout(CONNECT_TIMEOUT_MS, MILLISECONDS)
        .readTimeout(0, MILLISECONDS)
        .writeTimeout(0, MILLISECONDS)
        .callTimeout(pageTimeoutMs, MILLISECONDS)
        .build()

    /** Always ready: there is nothing to load, and whether a server is set up is the translator's question. */
    override val status: TranslationStatus = TranslationStatus.Ready

    override suspend fun ensureReady(): Boolean = true

    override suspend fun translatePage(
        page: TranslatablePage,
        onBubble: (bubbleId: Int, text: String) -> Unit
    ): PageTranslation {
        if (page.panels.flatten().isEmpty()) return PageTranslation(emptyMap(), "", 0L)
        val started = System.nanoTime()
        val reply = IdKeyedReplyReader(page, onBubble)
        fun result(outcome: TranslationOutcome, errorCode: String? = null, text: String = "") =
            PageTranslation(reply.byId, text, (System.nanoTime() - started) / NANOS_PER_MS, outcome, errorCode)

        val config = config()
        val url = chatCompletions(config.baseUrl) ?: return result(TranslationOutcome.ERROR, ERROR_SERVER_UNREACHABLE)
        val request = request(config, url, page)
        return withContext(Dispatchers.IO) {
            val call = client.newCall(request)
            // A blocking read can't see the coroutine's cancellation, so cancelling the page cancels the call (#76).
            @OptIn(InternalCoroutinesApi::class)
            val cancelOnCancel = coroutineContext.job.invokeOnCompletion(onCancelling = true) { call.cancel() }
            var replied = false
            try {
                call.execute().use { response ->
                    replied = true
                    if (!response.isSuccessful) {
                        Log.w(TAG, "translatePage http=${response.code}")
                        return@use result(TranslationOutcome.ERROR, httpFailure(response.code))
                    }
                    val text = response.body?.let { readStream(it.source(), reply) }.orEmpty().trim()
                    result(if (text.isEmpty()) TranslationOutcome.BLANK else TranslationOutcome.SUCCESS, text = text)
                }
            } catch (e: IOException) {
                ensureActive()
                Log.w(TAG, "translatePage failed replied=$replied ${e.javaClass.simpleName}")
                when {
                    // Both limits end this way: OkHttp reports the connect limit and the page limit as interruptions.
                    e is InterruptedIOException -> result(TranslationOutcome.TIMEOUT, ERROR_SERVER_TIMEOUT)
                    replied -> result(TranslationOutcome.ERROR, ERROR_SERVER_FAILED)
                    else -> result(TranslationOutcome.ERROR, ERROR_SERVER_UNREACHABLE)
                }
            } finally {
                cancelOnCancel.dispose()
            }
        }
    }

    private fun httpFailure(status: Int): String = when (status) {
        HTTP_UNAUTHORIZED, HTTP_FORBIDDEN -> ERROR_SERVER_KEY_REJECTED
        HTTP_NOT_FOUND -> ERROR_SERVER_MODEL_NOT_FOUND
        else -> ERROR_SERVER_FAILED
    }

    private fun request(config: NetworkTranslatorConfig, url: HttpUrl, page: TranslatablePage): Request {
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
            .url(url)
            .post(body.toString().toRequestBody(JSON))
            .apply { if (config.apiKey.isNotBlank()) header("Authorization", "Bearer ${config.apiKey}") }
            .build()
    }

    /** `{base}/v1/chat/completions`, whether or not the reader's address already ends in `/v1`; null when it is no address. */
    private fun chatCompletions(baseUrl: String): HttpUrl? {
        val base = baseUrl.toHttpUrlOrNull() ?: return null
        val segments = base.pathSegments.filter { it.isNotEmpty() }
        val path = if (segments.lastOrNull() == "v1") segments else segments + "v1"
        return base.newBuilder()
            .encodedPath("/")
            .apply { (path + listOf("chat", "completions")).forEach(::addPathSegment) }
            .build()
    }

    /**
     * Feeds each streamed piece of reply text, without its `<think>` blocks, to [reply] and returns the
     * whole of it. A reply cut off at the token cap leaves its unfinished last line unanswered (#368).
     */
    private fun readStream(source: BufferedSource, reply: IdKeyedReplyReader): String {
        val thoughts = ThinkBlockStripper()
        val text = StringBuilder()
        var finishReason: String? = null
        fun take(visible: String) {
            text.append(visible)
            reply.feed(visible.toByteArray())
        }
        while (true) {
            val line = source.readUtf8Line() ?: break
            if (!line.startsWith(DATA)) continue
            val data = line.removePrefix(DATA).trim()
            if (data == DONE) break
            val choice = choiceIn(data) ?: continue
            choice.string("finish_reason")?.let { finishReason = it }
            val content = choice.obj("delta")?.string("content") ?: continue
            take(thoughts.feed(content))
        }
        take(thoughts.finish())
        if (finishReason != CUT_OFF) reply.finish()
        return text.toString()
    }

    /** The first choice in one streamed chunk, or null when it carries none; throws when [data] is no chunk at all. */
    private fun choiceIn(data: String): JsonObject? {
        val chunk = try {
            JsonParser().parse(data).takeIf { it.isJsonObject }?.asJsonObject
        } catch (_: JsonParseException) {
            null
        }
        if (chunk == null || chunk.has("error")) throw UnreadableReply()
        return chunk.get("choices")?.takeIf { it.isJsonArray }?.asJsonArray?.firstOrNull()?.takeIf { it.isJsonObject }?.asJsonObject
    }

    private fun JsonObject.obj(name: String): JsonObject? = get(name)?.takeIf { it.isJsonObject }?.asJsonObject

    private fun JsonObject.string(name: String): String? = get(name)?.takeIf { it.isJsonPrimitive }?.asString

    /** A streamed line that is no chat chunk: an error the server sent mid-reply, or text that isn't one. */
    private class UnreadableReply : IOException("unreadable reply")

    override fun endSession() = Unit

    override fun close() = Unit

    private companion object {
        const val TAG = "NetworkTranslationSlot"
        val JSON = "application/json".toMediaType()

        /** ADR-0023's limit for reaching the server; the whole page then has [PAGE_TIMEOUT_MS]. */
        const val CONNECT_TIMEOUT_MS = 10_000L

        /** Above the phone's 768: a reasoning model spends tokens thinking before it answers (ADR-0023). */
        const val MAX_REPLY_TOKENS = 4096
        const val DATA = "data:"
        const val DONE = "[DONE]"
        /** The `finish_reason` of a reply that reached [MAX_REPLY_TOKENS] rather than ending. */
        const val CUT_OFF = "length"
        const val NANOS_PER_MS = 1_000_000L
    }
}
