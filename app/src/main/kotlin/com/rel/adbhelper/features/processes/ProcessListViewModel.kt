package com.rel.adbhelper.features.processes

import com.rel.adbhelper.core.adb.AdbClient
import com.rel.adbhelper.core.proc.ProcessEntry
import com.rel.adbhelper.core.proc.parsePs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ProcessListViewModel(
    private val client: AdbClient,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Unconfined),
) {
    data class State(
        val loading: Boolean = true,
        val processes: List<ProcessEntry> = emptyList(),
        val message: String = "",
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    fun load() {
        scope.launch {
            _state.value = State(loading = true)
            _state.value = try {
                val raw = (client.shell("ps -A") as? com.rel.adbhelper.core.adb.AdbResult.Success)?.output.orEmpty()
                State(loading = false, processes = parsePs(raw))
            } catch (t: Throwable) {
                State(loading = false, message = "Connection to the device was lost")
            }
        }
    }

    fun kill(entry: ProcessEntry) {
        scope.launch {
            val result = client.shell("kill ${entry.pid}")
            _state.value = _state.value.copy(
                message = if (result is com.rel.adbhelper.core.adb.AdbResult.Success) {
                    "Killed ${entry.name}"
                } else {
                    "Failed to kill process"
                },
            )
            load()
        }
    }
}
