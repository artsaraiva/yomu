package com.yomu.ml

import java.net.InetAddress

/**
 * Whether plain http may reach [address] (ADR-0023): loopback (`127/8`, `::1`), private (`10/8`,
 * `172.16/12`, `192.168/16`, `fc00::/7`), link-local (`169.254/16`, `fe80::/10`) or carrier-grade NAT
 * (`100.64/10`). Every other address needs https. An IPv4-mapped IPv6 address is judged by its IPv4 address.
 */
fun isNearbyAddress(address: InetAddress): Boolean = NEARBY_RANGES.any { it.contains(address) }

private val NEARBY_RANGES = listOf(
    "127.0.0.0/8", "10.0.0.0/8", "172.16.0.0/12", "192.168.0.0/16", "169.254.0.0/16", "100.64.0.0/10",
    "::1/128", "fc00::/7", "fe80::/10"
).map(::AddressRange)

/** An address block written in CIDR form, such as `10.0.0.0/8`. */
private class AddressRange(cidr: String) {
    private val network: ByteArray = InetAddress.getByName(cidr.substringBefore('/')).address
    private val prefixBits: Int = cidr.substringAfter('/').toInt()

    fun contains(address: InetAddress): Boolean {
        val bytes = address.address
        if (bytes.size != network.size) return false
        val wholeBytes = prefixBits / BITS_PER_BYTE
        if ((0 until wholeBytes).any { bytes[it] != network[it] }) return false
        val restBits = prefixBits % BITS_PER_BYTE
        if (restBits == 0) return true
        val mask = (BYTE_MASK shl (BITS_PER_BYTE - restBits)) and BYTE_MASK
        return (bytes[wholeBytes].toInt() and mask) == (network[wholeBytes].toInt() and mask)
    }

    private companion object {
        const val BITS_PER_BYTE = 8
        const val BYTE_MASK = 0xFF
    }
}
