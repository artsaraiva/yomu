package com.yomu.ml

import com.google.gson.JsonArray
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.yomu.core.TranslatableBubble
import com.yomu.core.TranslatablePage
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer

/** One server-sent event per piece of reply text, ending with [finishReason] and `[DONE]`, as an OpenAI-compatible server streams. */
internal fun chatStream(vararg pieces: String, finishReason: String = "stop"): MockResponse =
    MockResponse()
        .setHeader("Content-Type", "text/event-stream")
        .setBody(chatEvents(*pieces, finishReason = finishReason))

internal fun chatEvents(vararg pieces: String, finishReason: String? = "stop"): String = buildString {
    pieces.forEach { append(event(JsonObject().apply { addProperty("content", it) }, null)) }
    if (finishReason != null) {
        append(event(JsonObject(), finishReason))
        append("data: [DONE]\n\n")
    }
}

private fun event(delta: JsonObject, finishReason: String?): String {
    val choice = JsonObject().apply {
        addProperty("index", 0)
        add("delta", delta)
        if (finishReason == null) add("finish_reason", JsonNull.INSTANCE) else addProperty("finish_reason", finishReason)
    }
    val chunk = JsonObject().apply {
        addProperty("object", "chat.completion.chunk")
        add("choices", JsonArray().apply { add(choice) })
    }
    return "data: $chunk\n\n"
}

internal fun page(vararg bubbles: Pair<Int, String>): TranslatablePage =
    TranslatablePage(listOf(bubbles.map { (id, text) -> TranslatableBubble(id, text) }))

internal fun networkSlot(
    baseUrl: String,
    apiKey: String = "",
    model: String = "qwen3:8b",
    pageTimeoutMs: Long = 120_000,
    client: OkHttpClient = OkHttpClient()
): NetworkTranslationSlot =
    NetworkTranslationSlot(client, { NetworkTranslatorConfig(baseUrl, apiKey, model) }, pageTimeoutMs = pageTimeoutMs)

internal fun MockWebServer.base(): String = url("/").toString()
