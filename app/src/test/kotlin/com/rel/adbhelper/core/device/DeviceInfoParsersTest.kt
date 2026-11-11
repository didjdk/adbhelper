package com.rel.adbhelper.core.device

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DeviceInfoParsersTest {

    // Fixture: real `getprop` output from the dev emulator (API 37), trimmed to the
    // properties the parser reads plus noise lines it must ignore.
    private val getpropFixture = """
        [bluetooth.profile.a2dp.source.enabled]: [true]
        [ro.boot.serialno]: [EMULATOR37X2X12X0]
        [ro.build.version.release]: [17]
        [ro.build.version.sdk]: [37]
        [ro.build.version.security_patch]: [2026-08-05]
        [ro.product.cpu.abi]: [x86_64]
        [ro.product.device]: [emu64xa]
        [ro.product.manufacturer]: [Google]
        [ro.product.model]: [sdk_gphone64_x86_64]
        [ro.serialno]: [EMULATOR37X2X12X0]
        [persist.sys.timezone]: [GMT]
    """.trimIndent()

    private val batteryFixture = """
Current Battery Service state:
  AC powered: false
  USB powered: false
  Wireless powered: false
  Max charging current: 0
  Max charging voltage: 0
  Charge counter: 10000
  status: 4
  health: 2
  present: true
  level: 100
  scale: 100
  voltage: 5000
  temperature: 250
  technology: Li-ion
  Charging state: 0
    """.trimIndent()

    private val batteryChargingFixture = """
Current Battery Service state:
  AC powered: true
  USB powered: false
  status: 2
  level: 60
  voltage: 4300
  temperature: 280
    """.trimIndent()

    private val dfFixture = """
Filesystem      1K-blocks    Used Available Use% Mounted on
/dev/block/dm-6  10167132 2547056   7472620  26% /data/user/0
    """.trimIndent()

    private val wmFixture = "Physical size: 1280x2856\nPhysical density: 480"

    private val ipAddrFixture = """
15: wlan0: <BROADCAST,MULTICAST,UP,LOWER_UP> mtu 1500 qdisc noqueue state UP group default qlen 1000
    link/ether 02:15:b2:00:00:00 brd ff:ff:ff:ff:ff:ff
    inet 10.0.2.16/24 brd 10.0.2.255 scope global dynamic wlan0
    inet6 fec0::6d3c:5a9:c1d7:c099/64 scope site temporary dynamic
    """.trimIndent()

    @Test
    fun `parseGetprop reads bracketed key-value pairs and ignores noise`() {
        val props = parseGetprop(getpropFixture)
        assertEquals("sdk_gphone64_x86_64", props["ro.product.model"])
        assertEquals("EMULATOR37X2X12X0", props["ro.serialno"])
        assertEquals("x86_64", props["ro.product.cpu.abi"])
        assertEquals("17", props["ro.build.version.release"])
    }

    @Test
    fun `parseGetprop also tolerates unbracketed getprop lines`() {
        val props = parseGetprop("ro.product.model: plain\n[ro.build.version.sdk]: [37]")
        assertEquals("plain", props["ro.product.model"])
        assertEquals("37", props["ro.build.version.sdk"])
    }

    @Test
    fun `buildDeviceInfo extracts the full panel from all fixtures`() {
        val props = parseGetprop(getpropFixture)
        val info = buildDeviceInfo(
            props = props,
            batteryRaw = batteryFixture,
            dfRaw = dfFixture,
            wmRaw = wmFixture,
            ipAddrRaw = ipAddrFixture,
            cores = 4,
        )
        assertEquals("sdk_gphone64_x86_64", info.model)
        assertEquals("Google", info.manufacturer)
        assertEquals("EMULATOR37X2X12X0", info.serial)
        assertEquals("emu64xa", info.device)
        assertEquals("x86_64", info.cpuAbi)
        assertEquals(4, info.cores)
        assertEquals("17", info.androidVersion)
        assertEquals("37", info.sdkLevel)
        assertEquals("2026-08-05", info.securityPatch)
        assertEquals(100, info.batteryLevel)
        assertEquals("25", info.batteryTemperatureC.replace("°C", ""))
        assertEquals("Not charging", info.batteryState)
        assertEquals(10167132L, info.storageTotalKb)
        assertEquals(2547056L, info.storageUsedKb)
        assertEquals(7472620L, info.storageFreeKb)
        assertEquals("1280x2856", info.screenResolution)
        assertEquals(480, info.screenDensity)
        assertEquals("10.0.2.16", info.wifiIp)
    }

    @Test
    fun `battery state maps charging statuses`() {
        assertEquals(
            "Charging",
            buildDeviceInfo(emptyMap(), batteryChargingFixture, "", "", "", 1).batteryState,
        )
    }

    @Test
    fun `missing inputs degrade to empty strings rather than throwing`() {
        val info = buildDeviceInfo(emptyMap(), "", "", "", "", 0)
        assertEquals("", info.model)
        assertEquals("", info.serial)
        assertEquals(0L, info.storageTotalKb)
    }
}
