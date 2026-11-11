package com.rel.adbhelper.connect

import com.rel.adbhelper.core.adb.AdbResult
import com.rel.adbhelper.core.adb.COMMAND_TIMEOUT_MILLIS
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class ConnectViewModelTest {

    @Test
    fun `CONNECT_TIMEOUT_MILLIS stays strictly above AdbClient's own read-write timeout`() {
        // Live-reproduced bug this guards against: CONNECT_TIMEOUT_MILLIS used to be 10s,
        // shorter than AdbClient's internal 15s read/write watchdog. The outer timeout always
        // fired first and cancelled AdbClient's own watchdog before it ever got to close a
        // genuinely stuck stream — the UI sat on "Connecting…" forever instead of the "Connection
        // timed out" this timeout exists to guarantee. See CONNECT_TIMEOUT_MILLIS's own doc.
        assertTrue(
            CONNECT_TIMEOUT_MILLIS > COMMAND_TIMEOUT_MILLIS,
            "CONNECT_TIMEOUT_MILLIS ($CONNECT_TIMEOUT_MILLIS) must stay strictly greater than " +
                "COMMAND_TIMEOUT_MILLIS ($COMMAND_TIMEOUT_MILLIS), or it races and silently " +
                "defeats AdbClient's own read/write watchdog",
        )
    }

    @Test
    fun `connect click with blank input leaves state Idle and does not invoke connect`() = runTest {
        var invoked = false
        val vm = ConnectViewModel(connect = { _, _ -> invoked = true; AdbResult.Success("") })
        vm.onConnectClicked("")
        assertEquals(ConnectUiState.Idle, vm.state.value)
        assertFalse(invoked)
    }

    @Test
    fun `connect click with malformed non-blank input leaves state Idle and does not invoke connect`() = runTest {
        var invoked = false
        val vm = ConnectViewModel(connect = { _, _ -> invoked = true; AdbResult.Success("") })
        vm.onConnectClicked("not-a-host-port")
        assertEquals(ConnectUiState.Idle, vm.state.value)
        assertFalse(invoked)
    }

    @Test
    fun `successful connect moves Idle to Connecting to Connected`() = runTest {
        val vm = ConnectViewModel(connect = { _, _ -> AdbResult.Success("connected") })
        vm.onConnectClicked("192.168.1.5:5555")
        assertTrue(vm.state.value is ConnectUiState.Connected)
    }

    @Test
    fun `transport failure surfaces a specific Failed state, not Idle`() = runTest {
        val vm = ConnectViewModel(connect = { _, _ -> AdbResult.TransportUnreachable(IOException()) })
        vm.onConnectClicked("192.168.1.5:5555")
        assertTrue(vm.state.value is ConnectUiState.Failed)
    }

    @Test
    fun `clicking again while already Connected does not restart the attempt`() = runTest {
        var callCount = 0
        val vm = ConnectViewModel(connect = { _, _ -> callCount++; AdbResult.Success("") })
        vm.onConnectClicked("192.168.1.5:5555")
        vm.onConnectClicked("192.168.1.5:5555")
        assertEquals(1, callCount)
    }

    @Test
    fun `second click while a connect is still in flight does not launch a second attempt`() = runTest {
        val deferred = CompletableDeferred<AdbResult>()
        var callCount = 0
        val vm = ConnectViewModel(connect = { _, _ -> callCount++; deferred.await() }, scope = this)
        vm.onConnectClicked("192.168.1.5:5555")
        runCurrent()
        assertEquals(ConnectUiState.Connecting, vm.state.value)

        vm.onConnectClicked("192.168.1.5:5555")
        runCurrent()
        assertEquals(1, callCount)

        deferred.complete(AdbResult.Success(""))
        advanceUntilIdle()
        assertTrue(vm.state.value is ConnectUiState.Connected)
    }

    @Test
    fun `a connect that never completes times out and surfaces a specific Failed state`() = runTest {
        val vm = ConnectViewModel(connect = { _, _ -> awaitCancellation() }, scope = this)
        vm.onConnectClicked("192.168.1.5:5555")
        advanceTimeBy(CONNECT_TIMEOUT_MILLIS + 1_000)
        runCurrent()
        val state = vm.state.value
        assertTrue(state is ConnectUiState.Failed)
        assertEquals("Connection timed out", (state as ConnectUiState.Failed).reason)
    }
}
