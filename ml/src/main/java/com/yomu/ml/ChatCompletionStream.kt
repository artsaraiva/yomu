package com.yomu.ml

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import java.io.IOException
import okio.BufferedSource

private const val DATA = "data:"
private const val DONE = "[DONE]"

/** The `finish_reason` of a reply the model ended; any other ending, or none, may have cut a line off. */
private const val ENDED_BY_MODEL = "stop"

/**
 * Reads a streamed `/v1/chat/completions` reply's server-sent events, feeding reply text to [reply] as soon
 * as it is known not to be the model's thoughts (see [ThinkBlockStripper]), and returns all of that text
 * (ADR-0023). Only a reply the model ended answers its unterminated last line (#368).
 *
 * Throws [IOException] for a line that is no chat chunk, including an error the server sends mid-reply.
 */
internal fun readChatCompletion(source: BufferedSource, reply: IdKeyedReplyReader): String {
    val stripper = ThinkBlockStripper()
    val text = StringBuilder()
    var finishReason: String? = null
    fun keep(visible: String) {
        text.append(visible)
        reply.feed(visible.toByteArray())
    }
    while (true) {
        val line = source.readUtf8Line() ?: break
        if (!line.startsWith(DATA)) continue
        val data = line.removePrefix(DATA).trim()
        if (data == DONE) break
        val choice = firstChoice(data) ?: continue
        choice.string("finish_reason")?.let { finishReason = it }
        choice.objectAt("delta")?.string("content")?.let { keep(stripper.feed(it)) }
    }
    val endedByModel = finishReason == ENDED_BY_MODEL
    keep(stripper.finish(endedByModel))
    if (endedByModel) reply.finish()
    return text.toString()
}

/** The first choice in one streamed chunk, or null when it carries none, such as a usage-only chunk. */
private fun firstChoice(data: String): JsonObject? {
    val chunk = try {
        JsonParser.parseString(data).asObjectOrNull()
    } catch (_: JsonParseException) {
        null
    }
    if (chunk == null || chunk.has("error")) throw UnreadableReply()
    return chunk.get("choices")?.takeIf { it.isJsonArray }?.asJsonArray?.firstOrNull().asObjectOrNull()
}

private fun JsonElement?.asObjectOrNull(): JsonObject? = this?.takeIf { it.isJsonObject }?.asJsonObject

private fun JsonObject.objectAt(name: String): JsonObject? = get(name).asObjectOrNull()

private fun JsonObject.string(name: String): String? = get(name)?.takeIf { it.isJsonPrimitive }?.asString

private class UnreadableReply : IOException("unreadable reply")
