package com.rel.adbhelper.core.net

import com.rel.adbhelper.core.net.LanScanner.subnetHosts
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class LanScannerTest {

    @Test
    fun `subnetHosts enumerates every usable host of a slash-24`() {
        val hosts = subnetHosts("10.0.2.16", 24)
        assertEquals(254, hosts.size)
        assertEquals("10.0.2.1", hosts.first())
        assertEquals("10.0.2.254", hosts.last())
        assertEquals("10.0.2.16", hosts[15])
    }

    @Test
    fun `non-slash-24 prefixes are clamped to the last octet`() {
        // /16 sweeps are 65k connects; the scanner deliberately limits itself to one /24
        // slice around the device's own address (see the shortcut ruling in the plan).
        val hosts = subnetHosts("192.168.44.9", 16)
        assertEquals(254, hosts.size)
        assertEquals("192.168.44.1", hosts.first())
    }

    @Test
    fun `formatTarget produces a host colon port string parseHostPort accepts`() {
        assertEquals("10.0.2.15:5555", LanScanner.formatTarget("10.0.2.15", 5555))
    }
}
