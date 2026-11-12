package com.rel.adbhelper.features.shell

import com.rel.adbhelper.core.adb.AdbClient
import com.rel.adbhelper.core.adb.AdbStream
import com.rel.adbhelper.core.adb.AdbTransport
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.IOException
import java.util.concurrent.CountDownLatch

private class RecordingStream : AdbStream {
    var closedOnThread: Thread? = null
    override suspend fun write(data: ByteArray) {}
    override suspend fun readAll(): ByteArray = ByteArray(0)

    // Never resolves on its own — a real terminal session's reader loop sits here until the
    // stream is closed out from under it. If this returned immediately instead, the shell
    // session's own reader loop would null out `stream` before restart() ever got a chance to
    // close it, and the test would pass for the wrong reason (close() skipped as a no-op, not
    // exercised at all).
    override suspend fun readChunk(): ByteArray? = awaitCancellation()
    override fun close() {
        closedOnThread = Thread.currentThread()
    }
}

private class RecordingTransport(val stream: RecordingStream) : AdbTransport {
    override suspend fun open(service: String): AdbStream = stream
}

/**
 * Models the real `io.github.muntashirakon.adb.AdbStream`'s documented behavior when forced
 * closed while a read is blocked on it: the blocked read throws `IOException("Stream closed.")`
 * rather than the coroutine simply being cancelled cleanly. [awaitCancellation]-based fixtures
 * (like [RecordingStream] above) respond to plain cancellation and so never exercise this path —
 * this needs a real blocking wait a real `close()` call unblocks, same reasoning as
 * `FakeAdbTransport`'s `neverSignalsEof` fixture in `AdbClientTest`.
 */
private class ThrowsOnForceCloseStream : AdbStream {
    private val latch = CountDownLatch(1)
    override suspend fun write(data: ByteArray) {}
    override suspend fun readAll(): ByteArray = ByteArray(0)
    override suspend fun readChunk(): ByteArray? {
        withContext(Dispatchers.IO) { latch.await() }
        throw IOException("Stream closed.")
    }
    override fun close() {
        latch.countDown()
    }
}

private class SingleStreamTransport(private val stream: AdbStream) : AdbTransport {
    override suspend fun open(service: String): AdbStream = stream
}

class ShellViewModelTest {

    @Test
    fun `restart closes the stream off the calling thread, not inline`() {
        // Live-reproduced: AdbStream.close() sends a real CLSE packet over the socket despite
        // being exposed as a plain non-suspend function. Calling it straight from restart() on
        // whatever thread invoked it (the UI thread, when wired from the "Restart terminal"
        // button) crashed with android.os.NetworkOnMainThreadException.
        //
        // Deliberately plain runBlocking, not runTest — this ViewModel's real default scope is
        // CoroutineScope(Dispatchers.Unconfined), a genuine dispatcher with no virtual-time
        // scheduler to advance. runTest's TestScope (and its backgroundScope) share one
        // TestCoroutineScheduler that every child launch must be visible to; a coroutine
        // launched here on a real Dispatchers.IO thread never registered with that scheduler at
        // all, so advanceUntilIdle() returned without ever running it. Real dispatchers need a
        // real wait, not a virtual-time one.
        val stream = RecordingStream()
        val transport = RecordingTransport(stream)
        val callingThread = Thread.currentThread()
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val vm = ShellViewModel(
            AdbClient(transport, watchdogDispatcher = Dispatchers.Unconfined),
            scope = scope,
            closeDispatcher = Dispatchers.IO,
        )

        vm.start()
        val connectedDeadline = System.currentTimeMillis() + 2_000
        while (!vm.state.value.connected && System.currentTimeMillis() < connectedDeadline) {
            Thread.sleep(10)
        }
        assertTrue(vm.state.value.connected, "expected the shell session to connect, got ${vm.state.value}")

        vm.restart()
        val closeDeadline = System.currentTimeMillis() + 2_000
        while (stream.closedOnThread == null && System.currentTimeMillis() < closeDeadline) {
            Thread.sleep(10)
        }

        assertTrue(stream.closedOnThread != null, "expected close() to have been called")
        assertFalse(stream.closedOnThread === callingThread, "expected close() to run off the calling thread")
    }

    @Test
    fun `restart does not crash when the old reader's blocked read throws Stream closed`() {
        // Live-reproduced on the "Restart terminal" button, right after fixing the
        // NetworkOnMainThreadException above: once restart() correctly closed the old stream
        // off-thread, the old reader coroutine's pending readChunk() call — exactly as the real
        // library behaves when forced closed mid-read — threw IOException("Stream closed."),
        // uncaught, crashing the whole app. start()'s reader loop had no try/catch around
        // readChunk() at all.
        var uncaught: Throwable? = null
        val scope = CoroutineScope(Dispatchers.Unconfined + CoroutineExceptionHandler { _, e -> uncaught = e })
        val transport = SingleStreamTransport(ThrowsOnForceCloseStream())
        val vm = ShellViewModel(
            AdbClient(transport, watchdogDispatcher = Dispatchers.Unconfined),
            scope = scope,
            closeDispatcher = Dispatchers.IO,
        )

        vm.start()
        val connectedDeadline = System.currentTimeMillis() + 2_000
        while (!vm.state.value.connected && System.currentTimeMillis() < connectedDeadline) {
            Thread.sleep(10)
        }
        assertTrue(vm.state.value.connected, "expected the shell session to connect, got ${vm.state.value}")

        vm.restart()
        val settleDeadline = System.currentTimeMillis() + 2_000
        while (uncaught == null && System.currentTimeMillis() < settleDeadline) {
            Thread.sleep(10)
        }

        assertNull(uncaught, "expected no uncaught exception from the old reader's forced close, got $uncaught")
    }
}
