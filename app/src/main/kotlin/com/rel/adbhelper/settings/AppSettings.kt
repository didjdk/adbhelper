package com.rel.adbhelper.settings

data class AppSettings(
    val defaultPostConnectScreen: String = "device_info",
    val showHiddenFilesByDefault: Boolean = false,
    val screenshotDirectory: String = "/sdcard/Pictures/AdbHelper",
)
