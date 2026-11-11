package com.rel.adbhelper.features.deviceinfo

import android.content.Context
import com.rel.adbhelper.core.adb.AdbClient
import com.rel.adbhelper.core.device.DeviceInfo
import com.rel.adbhelper.core.device.buildDeviceInfo
import com.rel.adbhelper.core.device.parseGetprop
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DeviceInfoViewModel(
    private val client: AdbClient,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Unconfined),
) {
    data class State(
        val loading: Boolean = true,
        val info: DeviceInfo? = null,
        val message: String = "",
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    fun load() {
        scope.launch {
            _state.value = State(loading = true)
            _state.value = try {
                val props = parseGetprop(client.shell("getprop").takeSuccess().orEmpty())
                val battery = client.shell("dumpsys battery").takeSuccess().orEmpty()
                val df = client.shell("df -k /data").takeSuccess().orEmpty()
                val wm = client.shell("wm size; wm density").takeSuccess().orEmpty()
                val ip = client.shell("ip addr show wlan0").takeSuccess().orEmpty()
                val cores = client.shell("nproc").takeSuccess().orEmpty().trim().toIntOrNull() ?: 0
                State(loading = false, info = buildDeviceInfo(props, battery, df, wm, ip, cores))
            } catch (t: Throwable) {
                State(loading = false, message = "Connection to the device was lost")
            }
        }
    }

    fun screenshot(context: Context) {
        scope.launch {
            _state.value = _state.value.copy(message = "Capturing screenshot…")
            try {
                val bytes = client.raw("exec:screencap -p")
                val dir = context.getExternalFilesDir("Screenshots") ?: context.filesDir
                val file = java.io.File(dir, "adbhelper-${System.currentTimeMillis()}.png")
                file.writeBytes(bytes)
                _state.value = _state.value.copy(message = "Screenshot saved to ${file.absolutePath}")
            } catch (t: Throwable) {
                _state.value = _state.value.copy(message = "Failed to capture screenshot")
            }
        }
    }

    private fun com.rel.adbhelper.core.adb.AdbResult.takeSuccess(): String? =
        (this as? com.rel.adbhelper.core.adb.AdbResult.Success)?.output
}
