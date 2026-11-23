package com.yomu.ml

import java.net.InetAddress
import java.net.UnknownHostException
import okhttp3.Call
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * The HTTP client for the reader's server, used by the network translator and the Providers screen
 * (ADR-0023). `https://` reaches any address. Plain `http://` reaches a host only when every address its
 * name resolves to is nearby ([isNearbyAddress]); otherwise the call fails with [HttpsRequiredException]
 * before anything is sent. The check is on the resolved addresses, so a public name can't pass as local.
 */
class ServerHttpClient(client: OkHttpClient) {
    private val tls = client
    private val cleartext = client.newBuilder().dns(NearbyOnlyDns(client.dns)).build()

    fun newCall(request: Request): Call = (if (request.url.isHttps) tls else cleartext).newCall(request)
}

/** A plain `http://` host resolved to an address that isn't nearby; it needs `https://` (ADR-0023). */
class HttpsRequiredException(host: String) :
    UnknownHostException("$host needs https: plain http reaches only nearby addresses")

/** Resolves with [delegate], and refuses a host unless every address it resolves to is nearby. */
private class NearbyOnlyDns(private val delegate: Dns) : Dns {
    override fun lookup(hostname: String): List<InetAddress> {
        val addresses = delegate.lookup(hostname)
        if (!addresses.all(::isNearbyAddress)) throw HttpsRequiredException(hostname)
        return addresses
    }
}
