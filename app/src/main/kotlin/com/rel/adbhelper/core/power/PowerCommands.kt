package com.rel.adbhelper.core.power

/** Exact shell strings the Power screen runs — the unit-tested surface of Task 6. */
object PowerCommands {
    fun reboot() = "reboot"
    fun rebootRecovery() = "reboot recovery"
    fun rebootBootloader() = "reboot bootloader"
    fun setDpi(dpi: Int) = "wm density $dpi"
    fun resetDpi() = "wm density reset"
    fun enableWirelessDebugging() = "settings put global adb_wifi_enabled 1"
    fun disableWirelessDebugging() = "settings put global adb_wifi_enabled 0"
    fun currentDensity() = "wm density"
    fun screenshot() = "screencap -p"
    fun rootProbe() = "su -c id"
}
