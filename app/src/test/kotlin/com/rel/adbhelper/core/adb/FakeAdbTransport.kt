package com.rel.adbhelper.core.adb

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.withContext
import java.util.concurrent.CountDownLatch

/** Test fixture: an in-memory [AdbTransport] that scripts a shell response (or a failure to open/read). */
class FakeAdbTransport(
    private val scriptedOutput: String = "",
    private val exitCode: Int = 0,
    private val throwOnOpen: Throwable? = null,
    private val throwOnRead: Throwable? = null,
    private val omitExitMarker: Boolean = false,
    /** Scripted raw bytes served verbatim for a service string, bypassing shell framing. */
    private val serviceOutputs: Map<String, ByteArray> = emptyMap(),
    /** How many bytes each [AdbStream.readChunk] call returns (test streaming behavior). */
    private val chunkSize: Int = Int.MAX_VALUE,
    /** Never returns from [open] — simulates an unresponsive/unreachable target. */
    private val hangOnOpen: Boolean = false,
    /** Delay before [open] responds — lets a test prove calls run concurrently rather than
     * sequentially, by checking virtual time against this delay via a [kotlinx.coroutines.test.TestScope]. */
    private val openDelayMillis: Long = 0,
    /** Once all scripted bytes are delivered, [AdbStream.readChunk] hangs forever instead of
     * returning null — reproduces a real bug in the underlying ADB protocol library (CLSE
     * racing the last queued WRTE never promotes the stream to a real EOF), live-confirmed via
     * decompiled bytecode of `io.github.muntashirakon.adb.AdbStream.read`. A correct caller must
     * stop reading once it has recognized the command's own embedded exit marker, not wait for
     * the stream's own (sometimes-never-arriving) EOF. */
    private val neverSignalsEof: Boolean = false,
    /** Once all scripted bytes are delivered, [AdbStream.readChunk]/[AdbStream.readAll] throw
     * `IOException("Stream closed.")` immediately instead of returning null — reproduces the
     * OTHER half of the same confirmed library bug: when CLSE arrives on an already-empty
     * queue, `AdbStream.read` throws this exact exception rather than returning -1. Both signal
     * the identical real condition ("the remote is done, nothing more is coming"); a correct
     * caller with no embedded completion marker of its own (unlike [runShell], which uses the
     * exit marker) must treat this specific exception as a normal end-of-stream, not a failure. */
    private val throwsStreamClosedAtEof: Boolean = false,
) : AdbTransport {

    /** Bytes written to each service, for asserting push behavior. */
    val writtenByService = mutableMapOf<String, MutableList<ByteArray>>()
    val closedServices = mutableSetOf<String>()
    var invalidateCallCount = 0
        private set

    override fun invalidate() {
        invalidateCallCount++
    }

    override suspend fun open(service: String): AdbStream {
        if (openDelayMillis > 0) kotlinx.coroutines.delay(openDelayMillis)
        if (hangOnOpen) awaitCancellation()
        throwOnOpen?.let { throw it }
        serviceOutputs[service]?.let { bytes ->
            val hangLatch = if (neverSignalsEof) CountDownLatch(1) else null
            return InMemoryAdbStream(
                bytes, throwOnRead, chunkSize, hangLatch, throwsStreamClosedAtEof,
                onWrite = { writtenByService.getOrPut(service) { mutableListOf() }.add(it) },
                onClose = { closedServices.add(service) },
            )
        }
        val bytes = if (omitExitMarker) {
            scriptedOutput.toByteArray(Charsets.UTF_8)
        } else {
            "${scriptedOutput}__ADBHELPER_EXIT__:$exitCode\n".toByteArray(Charsets.UTF_8)
        }
        val hangLatch = if (neverSignalsEof) CountDownLatch(1) else null
        return InMemoryAdbStream(
            bytes, throwOnRead, chunkSize, hangLatch, throwsStreamClosedAtEof,
            onWrite = { writtenByService.getOrPut(service) { mutableListOf() }.add(it) },
            onClose = { closedServices.add(service) },
        )
    }
}

private class InMemoryAdbStream(
    private val bytes: ByteArray,
    private val throwOnRead: Throwable?,
    private val chunkSize: Int,
    /** Non-null once bytes are exhausted: a real blocked read a close() can unblock (see
     * [FakeAdbTransport]'s `neverSignalsEof` doc) — [kotlinx.coroutines.awaitCancellation] alone
     * doesn't model this, since plain coroutine cancellation can't interrupt it either, same
     * reasoning as this file's "stuck in a real blocking read" test. */
    private val hangLatch: CountDownLatch? = null,
    private val throwsStreamClosedAtEof: Boolean = false,
    private val onWrite: (ByteArray) -> Unit = {},
    private val onClose: () -> Unit = {},
) : AdbStream {
    private var readPosition = 0
    override suspend fun write(data: ByteArray) = onWrite(data)
    override suspend fun readAll(): ByteArray {
        throwOnRead?.let { throw it }
        if (hangLatch != null) {
            withContext(Dispatchers.IO) { hangLatch.await() }
            throw java.io.IOException("Stream closed.")
        }
        if (throwsStreamClosedAtEof) throw java.io.IOException("Stream closed.")
        return bytes
    }
    override suspend fun readChunk(): ByteArray? {
        throwOnRead?.let { throw it }
        if (readPosition >= bytes.size) {
            if (hangLatch != null) {
                withContext(Dispatchers.IO) { hangLatch.await() }
                throw java.io.IOException("Stream closed.")
            }
            if (throwsStreamClosedAtEof) throw java.io.IOException("Stream closed.")
            return null
        }
        val end = minOf(readPosition + chunkSize, bytes.size)
        val chunk = bytes.copyOfRange(readPosition, end)
        readPosition = end
        return chunk
    }
    override fun close() {
        hangLatch?.countDown()
        onClose()
    }
}
