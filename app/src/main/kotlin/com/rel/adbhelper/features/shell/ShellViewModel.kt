package com.rel.adbhelper.features.shell

import com.rel.adbhelper.core.adb.AdbClient
import com.rel.adbhelper.core.adb.AdbStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ShellViewModel(
    private val client: AdbClient,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Unconfined),
) {
    data class State(
        val output: List<String> = emptyList(),
        val connected: Boolean = false,
        val message: String = "",
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private var stream: AdbStream? = null
    private var reader: Job? = null

    fun start() {
        if (stream != null) return
        scope.launch {
            try {
                stream = client.openService("shell:")
                _state.value = _state.value.copy(connected = true, message = "")
                reader = scope.launch {
                    while (true) {
                        val chunk = stream?.readChunk() ?: break
                        _state.value = _state.value.copy(
                            output = _state.value.output + chunk.toString(Charsets.UTF_8),
                        )
                    }
                    _state.value = _state.value.copy(connected = false, message = "Shell session ended")
                    stream = null
                }
            } catch (t: Throwable) {
                _state.value = _state.value.copy(connected = false, message = "Could not open shell")
                stream = null
            }
        }
    }

    fun send(command: String) {
        val s = stream ?: return
        scope.launch { s.write((command + "\n").toByteArray(Charsets.UTF_8)) }
    }

    fun clear() {
        _state.value = _state.value.copy(output = emptyList())
    }

    fun restart() {
        stream?.close()
        stream = null
        reader?.cancel()
        reader = null
        _state.value = _state.value.copy(output = emptyList(), connected = false, message = "")
        start()
    }

    /** One-shot `setprop ctl.restart adbd` — needs root on the target; failure surfaces as a message. */
    fun restartAdbServer() {
        scope.launch {
            val result = client.shell("setprop ctl.restart adbd")
            _state.value = _state.value.copy(
                message = if (result is com.rel.adbhelper.core.adb.AdbResult.Success) {
                    "adb server restart requested"
                } else {
                    "Failed to restart adb server (needs root on the target)"
                },
            )
        }
    }
}
