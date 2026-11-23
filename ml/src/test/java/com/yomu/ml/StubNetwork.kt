package com.yomu.ml

import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketAddress
import javax.net.SocketFactory
import okhttp3.Dns
import okhttp3.OkHttpClient

internal val LOOPBACK: InetAddress = InetAddress.getByName("127.0.0.1")

/**
 * A client on a network where each name in [hosts] resolves to the given addresses, and every connection,
 * whatever address it was resolved to, reaches [LOOPBACK] on the same port. A test server there stands in
 * for an address anywhere, so a request the rule should have refused shows up at it. IP literals parse as
 * themselves.
 */
internal fun stubNetwork(vararg hosts: Pair<String, List<String>>): OkHttpClient.Builder {
    val table = hosts.toMap()
    val dns = object : Dns {
        override fun lookup(hostname: String): List<InetAddress> =
            table[hostname]?.map(InetAddress::getByName) ?: Dns.SYSTEM.lookup(hostname)
    }
    return OkHttpClient.Builder().dns(dns).socketFactory(LoopbackSocketFactory)
}

private object LoopbackSocketFactory : SocketFactory() {
    override fun createSocket(): Socket = object : Socket() {
        override fun connect(endpoint: SocketAddress?, timeout: Int) {
            val port = (endpoint as InetSocketAddress).port
            super.connect(InetSocketAddress(LOOPBACK, port), timeout)
        }
    }

    override fun createSocket(host: String?, port: Int): Socket = unconnectedOnly()
    override fun createSocket(host: String?, port: Int, localHost: InetAddress?, localPort: Int): Socket = unconnectedOnly()
    override fun createSocket(host: InetAddress?, port: Int): Socket = unconnectedOnly()
    override fun createSocket(address: InetAddress?, port: Int, localAddress: InetAddress?, localPort: Int): Socket =
        unconnectedOnly()

    private fun unconnectedOnly(): Nothing = throw UnsupportedOperationException("OkHttp asks for unconnected sockets")
}
