package com.rel.adbhelper.features.livescreen

import com.rel.adbhelper.core.adb.AdbClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Delay between captures. Deliberately tiny — this screen's whole point is to feel live; the
 * real pacing comes from how long each [AdbClient.raw] round trip takes, not from this value. */
private const val POLL_DELAY_MILLIS = 5L

private const val SCREEN_SERVICE = "exec:screencap -p"

/**
 * Drives the "Live Screen" tab: repeatedly captures a full-screen PNG from the target and
 * publishes it as [State.frame]. One capture at a time — the next request is only sent after the
 * previous one's bytes are fully in hand, so a slow link naturally paces itself instead of
 * piling up concurrent `exec:screencap` calls on the one shared connection (the same class of
 * problem [AppsViewModel.load]'s doc already covers for concurrent reads on one connection).
 */
class LiveScreenViewModel(
    private val client: AdbClient,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Unconfined),
) {
    data class State(
        val frame: ByteArray? = null,
        val frameCount: Int = 0,
        val error: String = "",
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private var job: Job? = null

    fun start() {
        if (job?.isActive == true) return
        _state.value = _state.value.copy(error = "")
        job = scope.launch {
            while (isActive) {
                try {
                    val bytes = client.raw(SCREEN_SERVICE)
                    _state.value = _state.value.copy(
                        frame = bytes,
                        frameCount = _state.value.frameCount + 1,
                        error = "",
                    )
                } catch (c: CancellationException) {
                    throw c
                } catch (t: Throwable) {
                    _state.value = _state.value.copy(error = "Lost connection to device")
                    return@launch
                }
                delay(POLL_DELAY_MILLIS)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }
}
