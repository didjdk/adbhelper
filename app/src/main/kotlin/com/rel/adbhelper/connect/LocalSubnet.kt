package com.rel.adbhelper.connect

import android.content.Context
import android.net.wifi.WifiManager
import com.rel.adbhelper.core.net.LanScanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.Inet4Address
import java.net.NetworkInterface

/** Local device's IPv4 + prefix length for the LAN sweep, or null when no network is up. */
suspend fun localSubnet(context: Context): Pair<String, Int>? = withContext(Dispatchers.IO) {
    val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    val dhcp = wifi?.dhcpInfo
    if (dhcp != null && dhcp.ipAddress != 0) {
        val ip = intToIp(dhcp.ipAddress)
        val prefix = Integer.bitCount(dhcp.netmask).let { if (it !in 8..32) 24 else it }
        return@withContext ip to prefix
    }
    NetworkInterface.getNetworkInterfaces().asSequence()
        .filter { it.isUp && !it.isLoopback }
        .flatMap { it.inetAddresses.asSequence() }
        .filterIsInstance<Inet4Address>()
        .filter { it.isSiteLocalAddress }
        .mapNotNull { address -> address.hostAddress?.let { it to 24 } }
        .firstOrNull()
}

private fun intToIp(value: Int): String =
    listOf(value and 0xFF, value shr 8 and 0xFF, value shr 16 and 0xFF, value shr 24 and 0xFF).joinToString(".")
