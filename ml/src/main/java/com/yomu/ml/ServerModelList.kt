package com.yomu.ml

import com.google.gson.JsonParser
import java.io.IOException
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/** What Connect learned from the reader's server (ADR-0023). */
sealed interface ServerModels {
    /** The server listed these model names, in its own order. */
    data class Listed(val names: List<String>) : ServerModels
}

/**
 * Connect's one request: asks the reader's server for its `/v1/models` list (ADR-0023). Nothing else on
 * the Providers screen contacts the server.
 */
class ServerModelList(client: OkHttpClient) {
    private val client = ServerHttpClient(client)

    /**
     * Asks the server at [address] for its models, sending [apiKey] as `Authorization: Bearer` only when
     * it is not blank. Cancelling the coroutine cancels the request.
     */
    suspend fun fetch(address: String, apiKey: String): ServerModels {
        val url = requireNotNull(serverEndpoint(address, "models"))
        val request = Request.Builder()
            .url(url)
            .apply { if (apiKey.isNotBlank()) header("Authorization", "Bearer ${apiKey.trim()}") }
            .build()
        return client.newCall(request).await { response -> ServerModels.Listed(modelNames(response.body?.string().orEmpty())) }
    }

    private fun modelNames(body: String): List<String> =
        JsonParser.parseString(body).asJsonObject.getAsJsonArray("data").map { it.asJsonObject.get("id").asString }
}

// The reply is read on OkHttp's thread, so a caller on the main thread never blocks on the network.
private suspend fun <T> Call.await(read: (Response) -> T): T = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(object : Callback {
        override fun onResponse(call: Call, response: Response) {
            continuation.resumeWith(runCatching { response.use(read) })
        }

        override fun onFailure(call: Call, e: IOException) = continuation.resumeWithException(e)
    })
}
