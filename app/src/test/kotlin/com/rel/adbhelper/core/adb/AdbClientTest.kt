package com.rel.adbhelper.core.adb

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.IOException

/** Dispatchers.Unconfined, not the production-default Dispatchers.Default: under `runTest`'s
 * virtual time, a watchdog on a real dispatcher never gets its `delay` routed through the test's
 * virtual scheduler, so it just never fires — these tests would hang or mis-report. Unconfined
 * stays inside the ambient (test) dispatcher chain instead of escaping to a real thread pool. */
private fun client(transport: AdbTransport) = AdbClient(transport, watchdogDispatcher = Dispatchers.Unconfined)

class AdbClientTest {

    @Test
    fun `shell returns Success with command output when transport succeeds`() = runTest {
        val transport = FakeAdbTransport(scriptedOutput = "hello\n", exitCode = 0)
        val result = client(transport).shell("echo hello")
        assertEquals(AdbResult.Success("hello\n"), result)
    }

    @Test
    fun `shell returns as soon as the exit marker is seen, without waiting for stream EOF`() = runTest {
        // Live-reproduced, decompiled-bytecode-confirmed bug in the underlying ADB protocol
        // library (io.github.muntashirakon:libadb-android 3.1.1): AdbStream.read() can hang
        // forever instead of ever returning EOF, when the remote CLSE packet arrives while data
        // is still queued — the final queued chunk gets drained through a fast path that never
        // promotes the stream to a real "closed" state, so a later read() call blocks on a wait()
        // nothing will ever wake. Depending on readAll() (which needs a real EOF) to know a
        // one-shot shell command is done is therefore unsafe — every call we already embed our
        // own exit marker specifically so we don't have to trust the stream's own completion
        // signal at all.
        val transport = FakeAdbTransport(scriptedOutput = "hello\n", exitCode = 0, chunkSize = 4, neverSignalsEof = true)
        val result = AdbClient(transport, commandTimeoutMillis = 300, watchdogDispatcher = Dispatchers.Unconfined)
            .shell("echo hello")
        assertEquals(AdbResult.Success("hello\n"), result)
    }

    @Test
    fun `shell returns CommandFailed when the remote command exits non-zero`() = runTest {
        val transport = FakeAdbTransport(scriptedOutput = "", exitCode = 1)
        val result = client(transport).shell("false")
        assertTrue(result is AdbResult.CommandFailed)
    }

    @Test
    fun `shell returns TransportUnreachable when opening the stream throws`() = runTest {
        val transport = FakeAdbTransport(throwOnOpen = IOException("refused"))
        val result = client(transport).shell("echo hi")
        assertTrue(result is AdbResult.TransportUnreachable)
    }

    @Test
    fun `shell returns TransportUnreachable when the connection drops mid-read`() = runTest {
        val transport = FakeAdbTransport(throwOnRead = IOException("connection reset"))
        val result = client(transport).shell("echo hi")
        assertTrue(result is AdbResult.TransportUnreachable)
    }

    @Test
    fun `shell returns CommandFailed when the stream ends without an exit marker`() = runTest {
        val transport = FakeAdbTransport(scriptedOutput = "partial output", omitExitMarker = true)
        val result = client(transport).shell("echo hi")
        assertTrue(result is AdbResult.CommandFailed)
    }

    @Test
    fun `shell rethrows cancellation rather than reporting it as a transport failure`() = runTest {
        val transport = FakeAdbTransport(throwOnRead = CancellationException("cancelled"))
        assertThrows(CancellationException::class.java) {
            kotlinx.coroutines.runBlocking { client(transport).shell("echo hi") }
        }
    }

    @Test
    fun `shell times out against an unresponsive transport rather than hanging forever`() = runTest {
        val transport = FakeAdbTransport(hangOnOpen = true)
        val result = client(transport).shell("echo hi")
        assertTrue(result is AdbResult.TransportUnreachable)
    }

    @Test
    fun `raw times out against an unresponsive transport rather than hanging forever`() = runTest {
        val transport = FakeAdbTransport(hangOnOpen = true)
        var threwIOException = false
        try {
            client(transport).raw("exec:screencap -p")
        } catch (e: IOException) {
            threwIOException = true
        }
        assertTrue(threwIOException)
    }

    @Test
    fun `raw treats the library's own Stream-closed exception as a normal EOF, not a failure`() = runTest {
        // The other half of the same confirmed library bug runShell's exit-marker fix works
        // around: when CLSE arrives on an already-empty read queue, AdbStream.read throws
        // IOException("Stream closed.") instead of returning -1 — live-reproduced against a real
        // target capturing a screenshot. raw() has no embedded completion marker of its own
        // (binary data), so it can't sidestep the library's signal the way shell() does; it must
        // instead recognize this exact, confirmed-benign exception as "done", not propagate it.
        val payload = byteArrayOf(0x89.toByte(), 0x50.toByte(), 0x4E.toByte(), 0x47.toByte())
        val transport = FakeAdbTransport(
            serviceOutputs = mapOf("exec:screencap -p" to payload),
            chunkSize = 2,
            throwsStreamClosedAtEof = true,
        )
        val bytes = client(transport).raw("exec:screencap -p")
        assertTrue(payload.contentEquals(bytes))
    }

    @Test
    fun `raw returns the stream bytes verbatim without any exit-marker framing`() = runTest {
        val payload = byteArrayOf(0x89.toByte(), 0x50.toByte(), 0x4E.toByte(), 0x47.toByte(), 0x00.toByte(), 0x0A.toByte())
        val transport = FakeAdbTransport(serviceOutputs = mapOf("exec:screencap -p" to payload))
        val bytes = client(transport).raw("exec:screencap -p")
        assertTrue(payload.contentEquals(bytes))
    }

    @Test
    fun `push writes the payload to the exec service and closes the stream`() = runTest {
        val payload = "binary-ish".toByteArray()
        val transport = FakeAdbTransport()
        client(transport).push("/data/local/tmp/x", payload)
        val written = transport.writtenByService["exec:cat > '/data/local/tmp/x'"].orEmpty()
        assertTrue(written.single().contentEquals(payload))
        assertTrue("exec:cat > '/data/local/tmp/x'" in transport.closedServices)
    }

    @Test
    fun `pull returns the streamed bytes for the requested path`() = runTest {
        val payload = "file content".toByteArray()
        val transport = FakeAdbTransport(serviceOutputs = mapOf("exec:cat '/sdcard/a.txt'" to payload))
        val bytes = client(transport).pull("/sdcard/a.txt")
        assertTrue(payload.contentEquals(bytes))
    }

    @Test
    fun `pullTo delivers the file in bounded chunks without ever holding it all at once`() = runTest {
        val payload = ByteArray(10) { it.toByte() }
        val transport = FakeAdbTransport(serviceOutputs = mapOf("exec:cat '/sdcard/big.bin'" to payload), chunkSize = 4)
        val delivered = mutableListOf<ByteArray>()
        var maxSeenAtOnce = 0
        client(transport).pullTo("/sdcard/big.bin") { chunk ->
            delivered += chunk
            maxSeenAtOnce = maxOf(maxSeenAtOnce, chunk.size)
        }
        assertEquals(10, delivered.sumOf { it.size })
        assertTrue(maxSeenAtOnce <= 4)
    }

    @Test
    fun `shell unblocks a stream stuck in a real blocking read by closing it, not just cancelling the coroutine`() {
        // Reproduces the real bug, live-observed against a real (slow/unresponsive) target: the
        // library's stream read is a blocking java.net.Socket read with no socket timeout of its
        // own — there's no suspension point inside it for plain coroutine cancellation to land
        // on, so the old code's `withTimeout` alone left the UI on "Loading…" far past its own
        // 15s bound (`pm list packages` sat for nearly a minute before this fix). A real socket's
        // close() unblocks a thread parked in its pending read (true on Android/Linux, which is
        // what this ships on) — modeled here with a CountDownLatch standing in for that blocked
        // read, released only by close(), so the test doesn't depend on this JVM/OS's own socket
        // behavior to prove that OUR code calls close() once the watchdog timeout elapses.
        // assertTimeoutPreemptively is the safety net: if a regression drops the watchdog, the
        // test fails instead of hanging CI.
        org.junit.jupiter.api.Assertions.assertTimeoutPreemptively(java.time.Duration.ofSeconds(5)) {
            val latch = java.util.concurrent.CountDownLatch(1)
            var closeCalled = false
            var invalidateCalled = false
            val transport = object : AdbTransport {
                override suspend fun open(service: String): AdbStream = object : AdbStream {
                    override suspend fun write(data: ByteArray) {}
                    override suspend fun readAll(): ByteArray =
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            latch.await() // blocks the real thread; only close() below releases it
                            ByteArray(0)
                        }
                    override suspend fun readChunk(): ByteArray? =
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            latch.await() // blocks the real thread; only close() below releases it
                            null
                        }
                    override fun close() {
                        closeCalled = true
                        latch.countDown()
                    }
                }
                override fun invalidate() {
                    invalidateCalled = true
                }
            }
            val result = kotlinx.coroutines.runBlocking {
                AdbClient(transport, commandTimeoutMillis = 300).shell("echo hi")
            }
            assertTrue(closeCalled, "expected the watchdog to close the stuck stream")
            assertTrue(result is AdbResult.CommandFailed, "expected the unblocked (empty) read to parse as a failed command, not hang")
            assertTrue(
                invalidateCalled,
                "expected the watchdog to invalidate the transport's connection, not just close this one " +
                    "stream — live-reproduced: without this, every later call silently reused the same " +
                    "now-broken connection and failed",
            )
        }
    }

    @Test
    fun `watchdog still fires when launched the same way every ViewModel in this app does`() {
        // Live-observed against a real target, with logging that watched the watchdog arm but
        // never fire even 30+ seconds past its own configured timeout: "Connecting…" stayed on
        // screen well past every layer of timeout this app has. The fix was pinning the watchdog
        // to Dispatchers.Default instead of letting it inherit its dispatcher from the caller
        // (every ViewModel here launches on CoroutineScope(Dispatchers.Unconfined), reproduced
        // below). This test does NOT reproduce that exact failure on a clean JVM — it passes
        // either way, most likely because the live hang needed a process already carrying
        // several other permanently-blocked IO threads from earlier stuck attempts, a precondition
        // a fresh test can't set up. Kept anyway as a real, if weaker, safety net: it still proves
        // the watchdog mechanism closes a genuinely stuck stream under this exact calling pattern.
        // Uses AdbClient's production default watchdogDispatcher (no override), unlike the other
        // tests in this file.
        org.junit.jupiter.api.Assertions.assertTimeoutPreemptively(java.time.Duration.ofSeconds(5)) {
            val latch = java.util.concurrent.CountDownLatch(1)
            var closeCalled = false
            val transport = object : AdbTransport {
                override suspend fun open(service: String): AdbStream = object : AdbStream {
                    override suspend fun write(data: ByteArray) {}
                    override suspend fun readAll(): ByteArray =
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            latch.await()
                            ByteArray(0)
                        }
                    override suspend fun readChunk(): ByteArray? =
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            latch.await()
                            null
                        }
                    override fun close() {
                        closeCalled = true
                        latch.countDown()
                    }
                }
            }
            val resultDeferred = java.util.concurrent.CompletableFuture<AdbResult>()
            val viewModelScope = CoroutineScope(Dispatchers.Unconfined)
            viewModelScope.launch {
                resultDeferred.complete(AdbClient(transport, commandTimeoutMillis = 300).shell("echo hi"))
            }
            val result = resultDeferred.get(4, java.util.concurrent.TimeUnit.SECONDS)
            assertTrue(closeCalled, "expected the watchdog to fire and close the stuck stream")
            assertTrue(result is AdbResult.CommandFailed)
        }
    }

    @Test
    fun `readChunk yields the stream in bounded chunks and then null`() = runTest {
        val payload = ByteArray(5) { it.toByte() }
        val transport = FakeAdbTransport(serviceOutputs = mapOf("s" to payload), chunkSize = 2)
        val stream = transport.open("s")
        assertEquals(listOf<Byte>(0, 1), stream.readChunk()!!.toList())
        assertEquals(listOf<Byte>(2, 3), stream.readChunk()!!.toList())
        assertEquals(listOf<Byte>(4), stream.readChunk()!!.toList())
        assertEquals(null, stream.readChunk())
    }
}
