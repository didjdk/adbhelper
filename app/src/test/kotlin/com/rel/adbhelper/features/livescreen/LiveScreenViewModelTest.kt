package com.rel.adbhelper.features.livescreen

import com.rel.adbhelper.core.adb.AdbClient
import com.rel.adbhelper.core.adb.FakeAdbTransport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** [Dispatchers.Unconfined], not the production-default [Dispatchers.Default] — see
 * [com.rel.adbhelper.core.adb.AdbClientTest]'s identical `client()` helper's doc. */
private fun client(transport: com.rel.adbhelper.core.adb.AdbTransport) =
    AdbClient(transport, watchdogDispatcher = Dispatchers.Unconfined)

@OptIn(ExperimentalCoroutinesApi::class)
class LiveScreenViewModelTest {

    @Test
    fun `start repeatedly captures frames, one capture at a time, until stop is called`() = runTest {
        val transport = FakeAdbTransport(serviceOutputs = mapOf("exec:screencap -p" to byteArrayOf(1, 2, 3)))
        val vm = LiveScreenViewModel(client(transport), scope = this)

        vm.start()
        advanceTimeBy(100)
        val framesAfter100ms = vm.state.value.frameCount
        assertTrue(framesAfter100ms > 1, "expected more than one frame captured in 100ms of 5ms-spaced polling, got $framesAfter100ms")

        vm.stop()
        val framesAtStop = vm.state.value.frameCount
        advanceTimeBy(100)
        assertEquals(framesAtStop, vm.state.value.frameCount, "expected no further captures once stopped")
    }

    @Test
    fun `start surfaces a connection-lost error and stops polling when a capture fails`() = runTest {
        val transport = FakeAdbTransport(throwOnOpen = java.io.IOException("refused"))
        val vm = LiveScreenViewModel(client(transport), scope = this)

        vm.start()
        advanceUntilIdle()

        assertTrue(vm.state.value.error.isNotEmpty(), "expected an error message once the capture fails")
        val framesAtFailure = vm.state.value.frameCount
        advanceTimeBy(100)
        assertEquals(framesAtFailure, vm.state.value.frameCount, "expected polling to stop after a failure")
    }
}
