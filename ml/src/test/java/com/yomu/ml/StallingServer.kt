package com.yomu.ml

import java.io.Closeable
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.CompletableFuture
import kotlin.concurrent.thread

/**
 * Answers one request with the start of a streamed reply, then stalls with the connection held open.
 *
 * MockWebServer can't do this: its body throttle also slows reading the request, so the reply never
 * starts. A plain socket also shows when the client hangs up, which MockWebServer does not.
 */
internal class StallingServer(firstEvents: String) : Closeable {
    private val socket = ServerSocket(0, 1, InetAddress.getLoopbackAddress())

    val baseUrl = "http://127.0.0.1:${socket.localPort}/"

    /** Completes once the client has closed the connection. */
    val hungUp = CompletableFuture<Unit>()

    init {
        thread(isDaemon = true) {
            try {
                socket.accept().use { client ->
                    client.getOutputStream().apply {
                        write(("HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\n\r\n$firstEvents").toByteArray())
                        flush()
                    }
                    // Drains the request; the read ends only when the client closes its end.
                    val input = client.getInputStream()
                    while (input.read() != -1) Unit
                }
            } catch (_: IOException) {
                // A reset is a hang-up too.
            }
            hungUp.complete(Unit)
        }
    }

    override fun close() = socket.close()
}
