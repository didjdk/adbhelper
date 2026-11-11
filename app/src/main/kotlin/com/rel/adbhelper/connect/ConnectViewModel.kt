package com.rel.adbhelper.connect

import com.rel.adbhelper.core.adb.AdbResult
import com.rel.adbhelper.core.adb.COMMAND_TIMEOUT_MILLIS
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeout

/**
 * How long a single connect attempt is given before it's reported as failed, rather than
 * leaving the UI on "Connecting…" indefinitely against an unreachable host.
 *
 * Live-reproduced bug this value fixes: it used to be 10s, shorter than [AdbClient][
 * com.rel.adbhelper.core.adb.AdbClient]'s own internal 15s read/write watchdog
 * ([COMMAND_TIMEOUT_MILLIS]). That meant THIS timeout always fired first and cancelled the whole
 * `connect(...)` call tree — including AdbClient's own watchdog coroutine — before that watchdog
 * ever got to run its `delay` out and close the stuck stream. The watchdog never fired, nothing
 * was ever left to unblock the real stuck read, and the UI sat on "Connecting…" forever instead
 * of the "Connection timed out" this timeout was supposed to guarantee. Must stay strictly
 * greater than [COMMAND_TIMEOUT_MILLIS] (with margin) so AdbClient's own bound always resolves
 * the call — successfully or not — before this one would ever need to step in; the real
 * backstop this timeout exists for is [AdbKeyConnectionManager.connectTo][
 * com.rel.adbhelper.core.adb.AdbKeyConnectionManager.connectTo]'s own documented, unfixable
 * limitation (no stream yet to close), not AdbClient's read/write path.
 */
const val CONNECT_TIMEOUT_MILLIS = COMMAND_TIMEOUT_MILLIS + 10_000L

/**
 * Drives the Connect screen's network path. [connect] is a function rather than a concrete
 * transport so this can be unit tested without a real socket; production wiring supplies a
 * lambda that opens a [com.rel.adbhelper.core.adb.NetworkAdbTransport] and probes it.
 *
 * The current `host:port` text is NOT held here — it's passed in on each click. Keeping a
 * second copy here, alongside the Composable's own saveable text-field state, let the two
 * diverge (e.g. across a configuration change, where this class is recreated but the saveable
 * text survives), leaving a "Connect" button that looked enabled but silently did nothing.
 */
class ConnectViewModel(
    private val connect: suspend (host: String, port: Int) -> AdbResult,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Unconfined),
) {
    private val _state = MutableStateFlow<ConnectUiState>(ConnectUiState.Idle)
    val state: StateFlow<ConnectUiState> = _state.asStateFlow()

    fun onConnectClicked(hostPortText: String) {
        val current = _state.value
        if (current !is ConnectUiState.Idle && current !is ConnectUiState.Failed) return
        val target = parseHostPort(hostPortText) ?: return
        _state.value = ConnectUiState.Connecting
        scope.launch {
            _state.value = try {
                withTimeout(CONNECT_TIMEOUT_MILLIS) {
                    when (val result = connect(target.first, target.second)) {
                        is AdbResult.Success -> ConnectUiState.Connected("${target.first}:${target.second}")
                        is AdbResult.CommandFailed -> ConnectUiState.Failed(result.message)
                        is AdbResult.TransportUnreachable ->
                            ConnectUiState.Failed(result.cause.message ?: "Connection failed")
                    }
                }
            } catch (e: TimeoutCancellationException) {
                ConnectUiState.Failed("Connection timed out")
            }
        }
    }
}

/** "host:port" -> (host, port), or null if blank or malformed (leading/trailing whitespace
 * tolerated). Shared by [ConnectViewModel] and the Connect screen's own enabled-state check so
 * both agree on what counts as a valid address. */
fun parseHostPort(text: String): Pair<String, Int>? {
    val trimmed = text.trim()
    val separatorIndex = trimmed.lastIndexOf(':')
    if (separatorIndex <= 0 || separatorIndex == trimmed.lastIndex) return null
    val host = trimmed.substring(0, separatorIndex)
    val port = trimmed.substring(separatorIndex + 1).toIntOrNull() ?: return null
    if (port !in 1..65535) return null
    return host to port
}
