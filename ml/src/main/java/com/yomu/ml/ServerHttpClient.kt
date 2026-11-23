package com.yomu.ml

import java.io.IOException
import java.net.InetAddress
import java.net.Proxy
import okhttp3.Call
import okhttp3.Dns
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/**
 * The HTTP client for the reader's server (ADR-0023). `https://` reaches any address. Plain `http://`
 * reaches a host only when every address its name resolves to is nearby ([isNearbyAddress]); otherwise
 * the call fails with [HttpsRequiredException] before anything is sent. The check is on the resolved
 * addresses, so a public name can't pass as local. A redirect that changes scheme is not followed.
 */
class ServerHttpClient(client: OkHttpClient) {
    // A redirect is followed by the client that started the call, so one that changed scheme would get the
    // other scheme's rules: plain http with no check, or https held to the nearby rule.
    private val tls = client.newBuilder().followSslRedirects(false).build()
    // Through a proxy, the proxy's address would be the one checked, and it could carry the page anywhere.
    private val cleartext = tls.newBuilder()
        .proxy(Proxy.NO_PROXY)
        .dns(NearbyOnlyDns(client.dns))
        .addNetworkInterceptor(NearbyOnlyConnection)
        .build()

    fun newCall(request: Request): Call = (if (request.url.isHttps) tls else cleartext).newCall(request)
}

/**
 * A plain `http://` host resolved to an address that isn't nearby; it needs `https://` (ADR-0023). Not an
 * [java.net.UnknownHostException]: the host was found, and a caller must not report it as missing.
 */
class HttpsRequiredException(host: String) : IOException("$host needs https: plain http reaches only nearby addresses")

/** Resolves with [delegate], and refuses a host unless every address it resolves to is nearby. */
private class NearbyOnlyDns(private val delegate: Dns) : Dns {
    override fun lookup(hostname: String): List<InetAddress> {
        val addresses = delegate.lookup(hostname)
        if (!addresses.all(::isNearbyAddress)) throw HttpsRequiredException(hostname)
        return addresses
    }
}

/**
 * Refuses a request, before it is written, on a connection to an address that isn't nearby. OkHttp
 * connects to an IP address without asking the [Dns], so only this check sees one, typed or redirected to.
 */
private object NearbyOnlyConnection : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val address = chain.connection()?.route()?.socketAddress?.address
        if (address == null || !isNearbyAddress(address)) throw HttpsRequiredException(chain.request().url.host)
        return chain.proceed(chain.request())
    }
}
