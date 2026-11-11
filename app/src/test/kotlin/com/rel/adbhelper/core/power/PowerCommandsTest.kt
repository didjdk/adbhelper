package com.rel.adbhelper.core.power

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PowerCommandsTest {

    @Test
    fun `command strings match the spec`() {
        assertEquals("reboot", PowerCommands.reboot())
        assertEquals("reboot recovery", PowerCommands.rebootRecovery())
        assertEquals("reboot bootloader", PowerCommands.rebootBootloader())
        assertEquals("wm density 480", PowerCommands.setDpi(480))
        assertEquals("wm density reset", PowerCommands.resetDpi())
        assertEquals("settings put global adb_wifi_enabled 1", PowerCommands.enableWirelessDebugging())
        assertEquals("settings put global adb_wifi_enabled 0", PowerCommands.disableWirelessDebugging())
        assertEquals("screencap -p", PowerCommands.screenshot())
        assertEquals("su -c id", PowerCommands.rootProbe())
    }
}
