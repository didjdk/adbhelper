package com.rel.adbhelper.connect

import com.rel.adbhelper.core.adb.AdbResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Drives the Connect screen's network path. [connect] is a function rather than a concrete
 * transport so this can be unit tested without a real socket; production wiring supplies a
 * lambda that opens a [com.rel.adbhelper.core.adb.NetworkAdbTransport] and probes it.
 */
class ConnectViewModel(
    private val connect: suspend (host: String, port: Int) -> AdbResult,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Unconfined),
) {
    private val _state = MutableStateFlow<ConnectUiState>(ConnectUiState.Idle)
    val state: StateFlow<ConnectUiState> = _state.asStateFlow()

    private var hostPortText: String = ""

    fun onHostPortChanged(text: String) {
        hostPortText = text
    }

    fun onConnectClicked() {
        val current = _state.value
        if (current !is ConnectUiState.Idle && current !is ConnectUiState.Failed) return
        val target = parseHostPort(hostPortText) ?: return
        _state.value = ConnectUiState.Connecting
        scope.launch {
            _state.value = when (val result = connect(target.first, target.second)) {
                is AdbResult.Success -> ConnectUiState.Connected("${target.first}:${target.second}")
                is AdbResult.CommandFailed -> ConnectUiState.Failed(result.message)
                is AdbResult.TransportUnreachable ->
                    ConnectUiState.Failed(result.cause.message ?: "Connection failed")
            }
        }
    }
}

/** "host:port" -> (host, port), or null if blank or malformed. */
private fun parseHostPort(text: String): Pair<String, Int>? {
    val separatorIndex = text.lastIndexOf(':')
    if (separatorIndex <= 0 || separatorIndex == text.lastIndex) return null
    val host = text.substring(0, separatorIndex)
    val port = text.substring(separatorIndex + 1).toIntOrNull() ?: return null
    if (port !in 1..65535) return null
    return host to port
}
