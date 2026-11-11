package com.rel.adbhelper.features.apps

import com.rel.adbhelper.core.adb.AdbClient
import com.rel.adbhelper.core.adb.FakeAdbTransport
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppsViewModelTest {

    @Test
    fun `load fetches third-party, system, and disabled packages concurrently, not sequentially`() = runTest {
        // Each of the three shell calls load() makes takes 1s on this (simulated slow) link.
        // Run one after another, the whole load takes ~3s; run together, it takes ~1s.
        // Loading… for 3x as long as necessary is exactly the "nice UI" this app is after.
        val transport = FakeAdbTransport(openDelayMillis = 1_000)
        val vm = AppsViewModel(AdbClient(transport), scope = this)

        vm.load()
        advanceUntilIdle()

        assertTrue(
            testScheduler.currentTime < 2_000,
            "expected the three fetches to overlap (~1s), took ${testScheduler.currentTime}ms — they ran sequentially",
        )
    }
}
