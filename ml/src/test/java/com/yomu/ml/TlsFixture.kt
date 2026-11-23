package com.yomu.ml

import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockWebServer
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate

/** A self-signed certificate for [hostname]: a test server presents it, and a client trusts it. */
internal class TlsFixture(hostname: String) {
    private val certificate = HeldCertificate.Builder().addSubjectAlternativeName(hostname).build()

    fun serve(server: MockWebServer) {
        val certificates = HandshakeCertificates.Builder().heldCertificate(certificate).build()
        server.useHttps(certificates.sslSocketFactory(), false)
    }

    fun trust(client: OkHttpClient.Builder): OkHttpClient.Builder {
        val certificates = HandshakeCertificates.Builder().addTrustedCertificate(certificate.certificate).build()
        return client.sslSocketFactory(certificates.sslSocketFactory(), certificates.trustManager)
    }
}
