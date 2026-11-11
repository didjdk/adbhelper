package com.rel.adbhelper.features.power

import com.rel.adbhelper.core.adb.AdbClient
import com.rel.adbhelper.core.adb.AdbResult
import com.rel.adbhelper.core.power.PowerCommands
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PowerViewModel(
    private val client: AdbClient,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Unconfined),
) {
    data class State(
        val currentDensity: Int? = null,
        val densityInput: String = "",
        val message: String = "",
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    fun load() {
        scope.launch {
            try {
                val wm = (client.shell(PowerCommands.currentDensity()) as? AdbResult.Success)?.output.orEmpty()
                val density = Regex("Physical density: (\\d+)").find(wm)?.groupValues?.get(1)?.toIntOrNull()
                _state.value = State(currentDensity = density, densityInput = density?.toString().orEmpty())
            } catch (t: Throwable) {
                _state.value = State(message = "Connection to the device was lost")
            }
        }
    }

    fun setDensityInput(text: String) {
        _state.value = _state.value.copy(densityInput = text)
    }

    fun applyDensity() {
        val dpi = _state.value.densityInput.toIntOrNull() ?: return
        scope.launch {
            val result = client.shell(PowerCommands.setDpi(dpi))
            _state.value = _state.value.copy(
                message = if (result is AdbResult.Success) "Density set to $dpi" else "Failed to change DPI",
                currentDensity = if (result is AdbResult.Success) dpi else _state.value.currentDensity,
            )
        }
    }

    fun resetDensity() {
        scope.launch {
            val result = client.shell(PowerCommands.resetDpi())
            _state.value = _state.value.copy(
                message = if (result is AdbResult.Success) "Density reset" else "Failed to change DPI",
            )
            load()
        }
    }

    private fun run(command: String, successMessage: String, onDone: () -> Unit = {}) {
        scope.launch {
            val result = client.shell(command)
            _state.value = _state.value.copy(
                message = if (result is AdbResult.Success) successMessage else "Command failed",
            )
            onDone()
        }
    }

    fun reboot() = run(PowerCommands.reboot(), "Rebooting")
    fun rebootRecovery() = run(PowerCommands.rebootRecovery(), "Rebooting to recovery")
    fun rebootBootloader() = run(PowerCommands.rebootBootloader(), "Rebooting to bootloader")
    fun enableWirelessDebugging() = run(PowerCommands.enableWirelessDebugging(), "Wireless debugging enabled")
    fun disableWirelessDebugging() = run(PowerCommands.disableWirelessDebugging(), "Wireless debugging disabled")
}
