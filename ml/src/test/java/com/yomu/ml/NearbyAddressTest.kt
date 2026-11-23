package com.yomu.ml

import java.net.InetAddress
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The address ranges plain http may reach, each with the address just past either end (#488, ADR-0023). */
class NearbyAddressTest {

    @Test
    fun isNearbyAddress_eachIpv4RangeIsNearbyEdgeToEdge() {
        val ranges = mapOf(
            "loopback 127/8" to listOf("127.0.0.0", "127.0.0.1", "127.255.255.255"),
            "private 10/8" to listOf("10.0.0.0", "10.0.2.2", "10.255.255.255"),
            "private 172.16/12" to listOf("172.16.0.0", "172.31.255.255"),
            "private 192.168/16" to listOf("192.168.0.0", "192.168.1.10", "192.168.255.255"),
            "link-local 169.254/16" to listOf("169.254.0.0", "169.254.255.255"),
            "carrier-grade NAT 100.64/10" to listOf("100.64.0.0", "100.127.255.255")
        )
        for ((range, addresses) in ranges) {
            addresses.forEach { assertTrue("$range: $it", isNearbyAddress(ip(it))) }
        }
    }

    @Test
    fun isNearbyAddress_theIpv4AddressJustPastEachRangeIsNot() {
        val outside = mapOf(
            "loopback 127/8" to listOf("126.255.255.255", "128.0.0.0"),
            "private 10/8" to listOf("9.255.255.255", "11.0.0.0"),
            "private 172.16/12" to listOf("172.15.255.255", "172.32.0.0"),
            "private 192.168/16" to listOf("192.167.255.255", "192.169.0.0"),
            "link-local 169.254/16" to listOf("169.253.255.255", "169.255.0.0"),
            "carrier-grade NAT 100.64/10" to listOf("100.63.255.255", "100.128.0.0")
        )
        for ((range, addresses) in outside) {
            addresses.forEach { assertFalse("past $range: $it", isNearbyAddress(ip(it))) }
        }
    }

    @Test
    fun isNearbyAddress_eachIpv6RangeIsNearbyEdgeToEdge() {
        val ranges = mapOf(
            "loopback ::1" to listOf("::1"),
            "unique local fc00::/7" to listOf("fc00::", "fd12:3456:789a::1", "fdff:ffff:ffff:ffff:ffff:ffff:ffff:ffff"),
            "link-local fe80::/10" to listOf("fe80::", "fe80::1", "febf:ffff:ffff:ffff:ffff:ffff:ffff:ffff")
        )
        for ((range, addresses) in ranges) {
            addresses.forEach { assertTrue("$range: $it", isNearbyAddress(ip(it))) }
        }
    }

    @Test
    fun isNearbyAddress_theIpv6AddressJustPastEachRangeIsNot() {
        val outside = mapOf(
            "loopback ::1" to listOf("::", "::2"),
            "unique local fc00::/7" to listOf("fbff:ffff:ffff:ffff:ffff:ffff:ffff:ffff", "fe00::"),
            "link-local fe80::/10" to listOf("fe7f:ffff:ffff:ffff:ffff:ffff:ffff:ffff", "fec0::")
        )
        for ((range, addresses) in outside) {
            addresses.forEach { assertFalse("past $range: $it", isNearbyAddress(ip(it))) }
        }
    }

    @Test
    fun isNearbyAddress_publicAddressesAreNot() {
        listOf("8.8.8.8", "203.0.113.7", "0.0.0.0", "255.255.255.255", "2001:4860:4860::8888", "64:ff9b::a00:1")
            .forEach { assertFalse(it, isNearbyAddress(ip(it))) }
    }

    @Test
    fun isNearbyAddress_ipv4MappedIpv6AddressIsJudgedByItsIpv4Address() {
        assertTrue(isNearbyAddress(ip("::ffff:192.168.1.10")))
        assertFalse(isNearbyAddress(ip("::ffff:8.8.8.8")))
    }

    /** Parses a literal; no lookup is made. */
    private fun ip(literal: String): InetAddress = InetAddress.getByName(literal)
}
