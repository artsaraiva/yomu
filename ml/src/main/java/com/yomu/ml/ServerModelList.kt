package com.yomu.ml

import com.google.gson.JsonParser
import java.io.IOException
import java.net.HttpURLConnection.HTTP_FORBIDDEN
import java.net.HttpURLConnection.HTTP_NOT_FOUND
import java.net.HttpURLConnection.HTTP_UNAUTHORIZED
import java.util.concurrent.TimeUnit.MILLISECONDS
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

    /** The server was reached but keeps no list, or an empty one: the reader types a model name. */
    data object Unlisted : ServerModels

    /** Connect did not reach a server that answered with models; the address stays unconfirmed. */
    data class Failed(val failure: ConnectFailure) : ServerModels
}

/** Why Connect failed, each told to the reader in its own words. */
enum class ConnectFailure {
    /** The address doesn't start with `http://` or `https://`, or isn't an address at all; nothing was sent. */
    NOT_AN_ADDRESS,

    /** No server answered at the address: nothing listening, no such host, or no answer in time. */
    UNREACHABLE,

    /** The server answered 401 or 403. */
    KEY_REJECTED,

    /** Plain `http://` to an address that isn't nearby; nothing was sent ([HttpsRequiredException]). */
    HTTPS_REQUIRED,

    /** The server answered with another error status, or with something that is no model list. */
    SERVER_ERROR
}

/**
 * Connect's one request: asks the reader's server for its `/v1/models` list (ADR-0023). Nothing else on
 * the server's setup contacts the server.
 */
class ServerModelList(client: OkHttpClient) {
    // ADR-0023's limit for reaching the server: an address with no machine behind it fails in seconds.
    private val client = ServerHttpClient(client.newBuilder().connectTimeout(CONNECT_TIMEOUT_MS, MILLISECONDS).build())

    /**
     * Asks the server at [address] for its models, sending [apiKey] as `Authorization: Bearer` only when
     * it is not blank. Cancelling the coroutine cancels the request.
     */
    suspend fun fetch(address: String, apiKey: String): ServerModels {
        val url = serverEndpoint(address, "models") ?: return ServerModels.Failed(ConnectFailure.NOT_AN_ADDRESS)
        val request = Request.Builder()
            .url(url)
            .apply { if (apiKey.isNotBlank()) header("Authorization", "Bearer ${apiKey.trim()}") }
            .build()
        return try {
            client.newCall(request).await(::modelsFrom)
        } catch (e: HttpsRequiredException) {
            ServerModels.Failed(ConnectFailure.HTTPS_REQUIRED)
        } catch (e: IOException) {
            ServerModels.Failed(ConnectFailure.UNREACHABLE)
        }
    }

    private fun modelsFrom(response: Response): ServerModels {
        when (response.code) {
            HTTP_NOT_FOUND -> return ServerModels.Unlisted
            HTTP_UNAUTHORIZED, HTTP_FORBIDDEN -> return ServerModels.Failed(ConnectFailure.KEY_REJECTED)
        }
        val names = if (response.isSuccessful) modelNames(response.body?.string().orEmpty()) else null
        return when {
            names == null -> ServerModels.Failed(ConnectFailure.SERVER_ERROR)
            names.isEmpty() -> ServerModels.Unlisted
            else -> ServerModels.Listed(names)
        }
    }

    // Null when the answer is no `/v1/models` list: an error page, another server's own API, an entry with no id.
    // A null list is an empty one: Ollama with no models pulled sends `"data": null`.
    private fun modelNames(body: String): List<String>? = runCatching {
        val data = JsonParser.parseString(body).asJsonObject.get("data")
        if (data.isJsonNull) emptyList() else data.asJsonArray.map { it.asJsonObject.get("id").asString }
    }.getOrNull()

    private companion object {
        const val CONNECT_TIMEOUT_MS = 10_000L
    }
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
