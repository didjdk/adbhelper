package com.rel.adbhelper.core.adb

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.io.IOException

/**
 * Marker appended to every shell command's output so [shell] can recover the remote exit code.
 * Plain `shell:<command>` service strings (what [AdbTransport] implementations expose) return
 * raw decoded stdout with no separate exit-status channel, so the exit code is captured the
 * same way a plain adb shell session would: run the command, then echo `$?`.
 */
private const val EXIT_CODE_MARKER = "__ADBHELPER_EXIT__:"
private val EXIT_CODE_LINE = Regex("$EXIT_CODE_MARKER(\\d+)\\s*\$")

/** Every one-shot call below is bounded by this — without it, an unresponsive or unreachable
 * target leaves the calling screen on "Loading…"/stuck forever instead of surfacing a failure.
 * [AdbClient.openService] is deliberately excluded: it hands back a long-lived stream (terminal,
 * mirror) whose whole point is to stay open past any such window.
 *
 * Public so callers that wrap an [AdbClient] call in their own outer timeout can see this value
 * and stay strictly above it — see [timingOutOnClose]'s doc on why an outer timeout shorter than
 * (or equal to) this one silently defeats the watchdog below instead of just adding a second,
 * redundant bound. [AdbClient]'s own `commandTimeoutMillis` constructor param can also be set
 * below this default instead, which keeps the same invariant without needing an outer wrapper at
 * all — prefer that over adding a shorter outer `withTimeout`.
 */
const val COMMAND_TIMEOUT_MILLIS = 15_000L

/**
 * Per-chunk watchdog bound for [AdbClient.raw] specifically — far shorter than
 * [COMMAND_TIMEOUT_MILLIS]. Live-reproduced on the Live Screen tab: the confirmed
 * `libadb-android` CLSE-races-WRTE bug (see [AdbClient.raw]'s doc) doesn't only hit the rare
 * "stream closed on an already-empty queue, throws immediately" case — the far more common case
 * at the natural end of a one-shot binary read is "queue non-empty when CLSE arrives, next read
 * blocks forever" (the other half of the same bug), which needs this watchdog to force it closed
 * before [AdbClient.raw] can treat it as EOF at all. At [COMMAND_TIMEOUT_MILLIS], that meant
 * *every* screenshot capture risked silently paying a full 15-second stall right at its last
 * chunk — measured live, happening roughly once every 10-15 captures, each one looking like the
 * whole feature had frozen. A one-shot binary read has no shell exit code to legitimately wait
 * 15s for; 2s is already generous for a single already-buffered-locally chunk once the rest of a
 * same-sized capture has consistently finished in a second or two.
 */
const val RAW_CHUNK_TIMEOUT_MILLIS = 2_000L

/**
 * The library's stream reads/writes are raw blocking socket I/O with no timeout of their own —
 * there is no suspension point inside them for coroutine cancellation to land on, so a plain
 * `withTimeout` wrapped around a call to [AdbStream.readAll]/[AdbStream.readChunk]/[AdbStream.write]
 * cannot actually interrupt it: it marks the coroutine cancelled, but the blocked thread only
 * notices once the blocking call itself returns, which on a dead link is never. Closing the
 * stream from a separate watchdog coroutine is what actually unblocks it — the underlying socket
 * throws on the blocked thread once it's closed out from under it (true on Android/Linux, which
 * is what this ships on). Reproduced live against a real, unresponsive target: `pm list packages`
 * sat on "Loading…" for nearly a minute despite this client's own 15s timeout, before this fix.
 *
 * Deliberately NOT nested inside an outer `withTimeout` of the same duration: a same-duration
 * outer timeout races this watchdog's own `delay`, and if the outer cancellation reaches this
 * coroutine scope first, it cancels the watchdog's `delay` before it ever calls `close()` —
 * cooperative cancellation alone, with nothing left to unstick the real blocked thread. This
 * watchdog has to be the only clock racing the blocking call, not one of two.
 */
private suspend fun <T> AdbStream.timingOutOnClose(
    timeoutMillis: Long,
    watchdogDispatcher: CoroutineDispatcher,
    onTimeout: () -> Unit,
    block: suspend AdbStream.() -> T,
): T = coroutineScope {
    val work = async { block() }
    // watchdogDispatcher, not inherited: when this whole chain is kicked off from a caller
    // sitting on Dispatchers.Unconfined (several ViewModels' default scope), an inherited
    // watchdog's own `delay` never got a dispatcher that would actually resume it — it just
    // never fired, leaving nothing to unstick the blocked read. Reproduced live against a real
    // target: watched the watchdog arm but never fire, even tens of seconds past its timeout,
    // with "Connecting…" stuck on screen the whole time. Production always passes
    // [Dispatchers.Default]; tests pass their own test dispatcher so the watchdog still
    // participates in the test's virtual time instead of racing real wall-clock time.
    val watchdog = launch(watchdogDispatcher) {
        delay(timeoutMillis)
        // A stream forced closed this way means something was genuinely stuck mid-protocol, not
        // just slow — onTimeout flags the shared connection as suspect (see
        // AdbTransport.invalidate's doc) *before* closing, so the next caller reconnects instead
        // of silently reusing a connection this close() may have left broken. Live-reproduced:
        // without this, one timed-out command left every later one-shot call in the same
        // session failing silently against the same now-dead connection.
        onTimeout()
        close()
    }
    try {
        work.await()
    } finally {
        watchdog.cancel()
    }
}

class AdbClient(
    private val transport: AdbTransport,
    /** Exposed for tests only, to prove the watchdog-close behavior below without a real 15s wait. */
    private val commandTimeoutMillis: Long = COMMAND_TIMEOUT_MILLIS,
    /** Exposed for tests only — see [timingOutOnClose]'s doc on why production never overrides this. */
    private val watchdogDispatcher: CoroutineDispatcher = Dispatchers.Default,
    /** Exposed for tests only — see [raw]'s doc on why this is its own, much shorter, value than
     * [commandTimeoutMillis]. */
    private val rawChunkTimeoutMillis: Long = RAW_CHUNK_TIMEOUT_MILLIS,
) {

    suspend fun shell(command: String): AdbResult = try {
        runShell(command)
    } catch (c: CancellationException) {
        throw c
    } catch (t: Throwable) {
        AdbResult.TransportUnreachable(t)
    }

    /** Opens a long-lived service stream (e.g. `"shell:"` for a terminal, `"localabstract:<socket>"`
     * for mirroring). The caller owns the stream's lifecycle — not time-bounded, see
     * [COMMAND_TIMEOUT_MILLIS]'s doc. */
    suspend fun openService(service: String): AdbStream = transport.open(service)

    /**
     * Reads a raw service's full byte output — no shell exit-marker framing. Reads chunk-by-chunk
     * rather than via [AdbStream.readAll] so a confirmed library bug (see [runShell]'s doc) has
     * only two failure modes left to handle here, both live-reproduced capturing screenshots on
     * the Live Screen tab: when the remote CLSE packet races an already-drained read queue,
     * [AdbStream.read] throws `IOException("Stream closed.")` instead of signaling EOF the normal
     * way; when CLSE instead races a still-queued read, the next read blocks forever, needing
     * [RAW_CHUNK_TIMEOUT_MILLIS]'s watchdog to force it closed — which throws that exact same
     * message. Unlike [runShell], this has no embedded completion marker to fall back on (binary
     * data), so that exact, confirmed-benign exception is treated as a normal end of stream
     * instead of a failure, from either path.
     */
    suspend fun raw(service: String): ByteArray = try {
        val stream = openWithTimeout(service)
        try {
            val buffer = java.io.ByteArrayOutputStream()
            while (true) {
                val chunk = try {
                    stream.timingOutOnClose(rawChunkTimeoutMillis, watchdogDispatcher, transport::invalidate) {
                        readChunk()
                    }
                } catch (e: IOException) {
                    if (e.message == "Stream closed.") null else throw e
                } ?: break
                buffer.write(chunk)
            }
            buffer.toByteArray()
        } finally {
            stream.close()
        }
    } catch (c: CancellationException) {
        throw c
    } catch (t: Throwable) {
        throw IOException("failed to read from $service", t)
    }

    /**
     * Streams [data] onto the target's [path] via `exec:cat >`, one [chunkSize]-sized write at
     * a time. Each write (and the initial open) is individually timeout-bounded, not the whole
     * transfer — a large file legitimately taking minutes isn't penalized the way one
     * whole-operation timeout would, while a connection that goes quiet mid-transfer still gets
     * caught within [COMMAND_TIMEOUT_MILLIS] of its last successful write.
     */
    suspend fun push(path: String, data: ByteArray, chunkSize: Int = 256 * 1024): Unit = try {
        val stream = openWithTimeout("exec:cat > '$path'")
        try {
            data.toList().chunked(chunkSize).forEach { chunk ->
                stream.timingOutOnClose(commandTimeoutMillis, watchdogDispatcher, transport::invalidate) {
                    write(chunk.toByteArray())
                }
            }
        } finally {
            stream.close()
        }
    } catch (c: CancellationException) {
        throw c
    } catch (t: Throwable) {
        throw IOException("failed to write to $path", t)
    }

    /**
     * Reads a file off the target via `exec:cat`, one chunk at a time into [sink] — never holds
     * the whole file in memory, and (see [push]'s doc) each chunk is individually
     * timeout-bounded rather than the whole transfer.
     */
    suspend fun pullTo(path: String, sink: (ByteArray) -> Unit) {
        try {
            val stream = openWithTimeout("exec:cat '$path'")
            try {
                while (true) {
                    val chunk = stream.timingOutOnClose(commandTimeoutMillis, watchdogDispatcher, transport::invalidate) {
                        readChunk()
                    } ?: break
                    sink(chunk)
                }
            } finally {
                stream.close()
            }
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            throw IOException("failed to read from $path", t)
        }
    }

    /** Small-payload convenience over [pullTo] (e.g. short config files) — loads the whole
     * result into memory, so callers expecting a possibly-large file should use [pullTo]
     * directly instead (see [com.rel.adbhelper.features.files.FilesViewModel.download]). */
    suspend fun pull(path: String): ByteArray {
        val buffer = java.io.ByteArrayOutputStream()
        pullTo(path) { buffer.write(it) }
        return buffer.toByteArray()
    }

    /** Bounds [transport.open] — the one place this client still relies on plain coroutine
     * cancellation, because there's no stream yet to close. A target that accepts the raw
     * connection but never completes the handshake can still hang this past [commandTimeoutMillis]
     * (same accepted limitation as [AdbKeyConnectionManager.connectTo]'s doc). */
    private suspend fun openWithTimeout(service: String): AdbStream = try {
        withTimeout(commandTimeoutMillis) { transport.open(service) }
    } catch (c: TimeoutCancellationException) {
        throw IOException("timed out opening $service", c)
    }

    /**
     * Reads chunk-by-chunk, stopping as soon as the embedded exit marker is seen in the
     * accumulated output, rather than waiting for [AdbStream.readAll]'s EOF. Live-reproduced,
     * decompiled-bytecode-confirmed bug in the underlying ADB protocol library
     * (`io.github.muntashirakon:libadb-android` 3.1.1): when the remote CLSE packet races the
     * last queued WRTE, [AdbStream.read] can block forever instead of ever signaling EOF — the
     * exact "Stream closed" / "timed out opening ..." failures this app kept hitting against
     * real targets. The exit marker this app already appends to every command is a
     * protocol-independent completion signal; trusting it instead of the stream's own (unreliable)
     * end-of-stream notification sidesteps the library bug entirely for every one-shot command.
     */
    private suspend fun runShell(command: String): AdbResult {
        val stream = openWithTimeout("shell:$command; echo $EXIT_CODE_MARKER\$?")
        try {
            val buffer = java.io.ByteArrayOutputStream()
            while (true) {
                val chunk = stream.timingOutOnClose(commandTimeoutMillis, watchdogDispatcher, transport::invalidate) {
                    readChunk()
                } ?: return AdbResult.CommandFailed("remote shell closed without an exit status")
                buffer.write(chunk)
                val text = buffer.toString(Charsets.UTF_8.name())
                val match = EXIT_CODE_LINE.find(text) ?: continue
                val exitCode = match.groupValues[1].toInt()
                val output = text.substring(0, match.range.first)
                return if (exitCode == 0) AdbResult.Success(output) else AdbResult.CommandFailed(
                    output.trim().ifEmpty { "command exited with code $exitCode" }
                )
            }
        } finally {
            stream.close()
        }
    }
}
