package com.rel.adbhelper.core.device

data class DeviceInfo(
    val model: String,
    val manufacturer: String,
    val serial: String,
    val device: String,
    val cpuAbi: String,
    val cores: Int,
    val androidVersion: String,
    val sdkLevel: String,
    val securityPatch: String,
    val batteryLevel: Int,
    val batteryVoltageMv: Int,
    val batteryTemperatureC: String,
    val batteryState: String,
    val storageTotalKb: Long,
    val storageUsedKb: Long,
    val storageFreeKb: Long,
    val screenResolution: String,
    val screenDensity: Int,
    val wifiIp: String,
)

/** Parses `getprop` output — bracketed `[k]: [v]` is the standard form; bare `k: v` tolerated. */
fun parseGetprop(raw: String): Map<String, String> =
    raw.lineSequence()
        .filter { it.contains(": ") }
        .mapNotNull { line ->
            val bracketed = Regex("^\\[(.+?)]: \\[(.*)]$").find(line)
            if (bracketed != null) {
                bracketed.groupValues[1] to bracketed.groupValues[2]
            } else {
                val idx = line.indexOf(": ")
                line.substring(0, idx) to line.substring(idx + 2)
            }
        }
        .toMap()

/**
 * Assembles the read-only device panel. Every input is raw shell text or an already-known
 * number; anything missing degrades to an empty/zero field, never an exception — the panel
 * shows what a partially-responsive device reports.
 */
fun buildDeviceInfo(
    props: Map<String, String>,
    batteryRaw: String,
    dfRaw: String,
    wmRaw: String,
    ipAddrRaw: String,
    cores: Int,
): DeviceInfo {
    val battery = parseBattery(batteryRaw)
    val storage = parseDf(dfRaw)
    val screen = parseWm(wmRaw)
    return DeviceInfo(
        model = props["ro.product.model"].orEmpty(),
        manufacturer = props["ro.product.manufacturer"].orEmpty(),
        serial = props["ro.serialno"].orEmpty(),
        device = props["ro.product.device"].orEmpty(),
        cpuAbi = props["ro.product.cpu.abi"].orEmpty(),
        cores = cores,
        androidVersion = props["ro.build.version.release"].orEmpty(),
        sdkLevel = props["ro.build.version.sdk"].orEmpty(),
        securityPatch = props["ro.build.version.security_patch"].orEmpty(),
        batteryLevel = battery.level,
        batteryVoltageMv = battery.voltageMv,
        batteryTemperatureC = if (battery.temperatureTenthsC == 0) "" else "${battery.temperatureTenthsC / 10}°C",
        batteryState = battery.state,
        storageTotalKb = storage.totalKb,
        storageUsedKb = storage.usedKb,
        storageFreeKb = storage.freeKb,
        screenResolution = screen.resolution,
        screenDensity = screen.density,
        wifiIp = parseWifiIp(ipAddrRaw),
    )
}

data class BatteryReading(
    val level: Int,
    val voltageMv: Int,
    val temperatureTenthsC: Int,
    val state: String,
)

/** Parses `dumpsys battery`'s indented `key: value` lines. */
fun parseBattery(raw: String): BatteryReading {
    val lines = raw.lines().map { it.trim() }
    fun value(key: String): String =
        lines.firstOrNull { it.startsWith("$key: ") }?.substringAfter(": ")?.trim().orEmpty()
    val statusInt = value("status").toIntOrNull() ?: 0
    val acPowered = value("AC powered") == "true"
    val usbPowered = value("USB powered") == "true"
    val state = when {
        acPowered || usbPowered -> "Charging"
        statusInt == 2 -> "Charging"
        statusInt == 5 -> "Full"
        statusInt == 4 -> "Not charging"
        else -> "Discharging"
    }
    return BatteryReading(
        level = value("level").toIntOrNull() ?: 0,
        voltageMv = value("voltage").toIntOrNull() ?: 0,
        temperatureTenthsC = value("temperature").toIntOrNull() ?: 0,
        state = state,
    )
}

data class StorageReading(val totalKb: Long, val usedKb: Long, val freeKb: Long)

/** Parses the data row of `df -k <dir>`: columns are filesystem, total, used, available. */
fun parseDf(raw: String): StorageReading {
    val row = raw.lines().firstOrNull { it.startsWith("/dev/") } ?: return StorageReading(0, 0, 0)
    val numbers = row.trim().split(Regex("\\s+")).drop(1).take(3).mapNotNull { it.toLongOrNull() }
    if (numbers.size < 3) return StorageReading(0, 0, 0)
    return StorageReading(numbers[0], numbers[1], numbers[2])
}

data class ScreenReading(val resolution: String, val density: Int)

/** Parses combined `wm size` + `wm density` output. */
fun parseWm(raw: String): ScreenReading {
    val size = Regex("Physical size: (\\S+)").find(raw)?.groupValues?.get(1).orEmpty()
    val density = Regex("Physical density: (\\d+)").find(raw)?.groupValues?.get(1)?.toIntOrNull() ?: 0
    return ScreenReading(size, density)
}

/** The wlan0 interface's IPv4 address from `ip addr show wlan0`, or "" when absent. */
fun parseWifiIp(raw: String): String {
    val match = Regex("inet (\\d+\\.\\d+\\.\\d+\\.\\d+)/").find(raw) ?: return ""
    return match.groupValues[1]
}
