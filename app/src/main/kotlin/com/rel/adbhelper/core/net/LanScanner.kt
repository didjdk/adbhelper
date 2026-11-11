package com.rel.adbhelper.core.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

/**
 * The LAN "auto search": a parallel TCP connect sweep of the device's own /24 subnet for
 * hosts with the adb TCP port (5555) open. mDNS is deliberately not used (plan ruling).
 * `shortcut: only port 5555 is probed — wireless-debugging targets on random ports are
 * found by pairing/manual entry instead — upgrade when mDNS discovery is worth the code.`
 */
object LanScanner {

    const val ADB_PORT = 5555
    const val PARALLELISM = 32
    const val PROBE_TIMEOUT_MS = 250

    /** Every other usable host of the /24 containing [ip] (prefix clamped to 24 — larger
     * sweeps are 65k+ sockets for marginal benefit). */
    fun subnetHosts(ip: String, prefix: Int): List<String> {
        val octets = ip.split(".").map { it.toIntOrNull() ?: 0 }
        if (octets.size != 4) return emptyList()
        val base = octets.take(3).joinToString(".")
        return (1..254).map { "$base.$it" }
    }

    /** "host:port" — the same address format [com.rel.adbhelper.connect.parseHostPort] expects,
     * so a scan result can be connected to directly without a separate formatting step. */
    fun formatTarget(host: String, port: Int): String = "$host:$port"

    suspend fun scan(ip: String, prefix: Int, port: Int = ADB_PORT): List<String> = withContext(Dispatchers.IO) {
        subnetHosts(ip, prefix)
            .map { host -> async { if (probe(host, port)) formatTarget(host, port) else null } }
            .awaitAll()
            .filterNotNull()
    }

    suspend fun probe(host: String, port: Int, timeoutMs: Int = PROBE_TIMEOUT_MS): Boolean = withContext(Dispatchers.IO) {
        try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), timeoutMs)
                true
            }
        } catch (_: Throwable) {
            false
        }
    }
}
