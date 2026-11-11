package com.rel.adbhelper.connect

import com.rel.adbhelper.core.adb.AdbResult
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.IOException

class ConnectViewModelTest {

    @Test
    fun `connect click with blank input leaves state Idle and does not invoke connect`() = runTest {
        var invoked = false
        val vm = ConnectViewModel(connect = { _, _ -> invoked = true; AdbResult.Success("") })
        vm.onHostPortChanged("")
        vm.onConnectClicked()
        assertEquals(ConnectUiState.Idle, vm.state.value)
        assertFalse(invoked)
    }

    @Test
    fun `successful connect moves Idle to Connecting to Connected`() = runTest {
        val vm = ConnectViewModel(connect = { _, _ -> AdbResult.Success("connected") })
        vm.onHostPortChanged("192.168.1.5:5555")
        vm.onConnectClicked()
        assertTrue(vm.state.value is ConnectUiState.Connected)
    }

    @Test
    fun `transport failure surfaces a specific Failed state, not Idle`() = runTest {
        val vm = ConnectViewModel(connect = { _, _ -> AdbResult.TransportUnreachable(IOException()) })
        vm.onHostPortChanged("192.168.1.5:5555")
        vm.onConnectClicked()
        assertTrue(vm.state.value is ConnectUiState.Failed)
    }

    @Test
    fun `second connect click while Connecting does not restart the attempt`() = runTest {
        var callCount = 0
        val vm = ConnectViewModel(connect = { _, _ -> callCount++; AdbResult.Success("") })
        vm.onHostPortChanged("192.168.1.5:5555")
        vm.onConnectClicked()
        vm.onConnectClicked()
        assertEquals(1, callCount)
    }
}
