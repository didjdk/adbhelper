package com.rel.adbhelper.core.adb

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
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
 * [openService] is deliberately excluded: it hands back a long-lived stream (terminal, mirror)
 * whose whole point is to stay open past any such window. */
private const val COMMAND_TIMEOUT_MILLIS = 15_000L

class AdbClient(private val transport: AdbTransport) {

    suspend fun shell(command: String): AdbResult = try {
        withTimeout(COMMAND_TIMEOUT_MILLIS) { runShell(command) }
    } catch (c: TimeoutCancellationException) {
        AdbResult.TransportUnreachable(c)
    } catch (c: CancellationException) {
        throw c
    } catch (t: Throwable) {
        AdbResult.TransportUnreachable(t)
    }

    /** Opens a long-lived service stream (e.g. `"shell:"` for a terminal, `"localabstract:<socket>"`
     * for mirroring). The caller owns the stream's lifecycle — not time-bounded, see
     * [COMMAND_TIMEOUT_MILLIS]'s doc. */
    suspend fun openService(service: String): AdbStream = transport.open(service)

    /** Reads a raw service's full byte output — no shell exit-marker framing. */
    suspend fun raw(service: String): ByteArray = try {
        withTimeout(COMMAND_TIMEOUT_MILLIS) {
            val stream = transport.open(service)
            try {
                stream.readAll()
            } finally {
                stream.close()
            }
        }
    } catch (c: TimeoutCancellationException) {
        throw IOException("timed out reading from $service", c)
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
        val stream = withTimeout(COMMAND_TIMEOUT_MILLIS) { transport.open("exec:cat > '$path'") }
        try {
            data.toList().chunked(chunkSize).forEach { chunk ->
                withTimeout(COMMAND_TIMEOUT_MILLIS) { stream.write(chunk.toByteArray()) }
            }
        } finally {
            stream.close()
        }
    } catch (c: TimeoutCancellationException) {
        throw IOException("timed out writing to $path", c)
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
            val stream = withTimeout(COMMAND_TIMEOUT_MILLIS) { transport.open("exec:cat '$path'") }
            try {
                while (true) {
                    val chunk = withTimeout(COMMAND_TIMEOUT_MILLIS) { stream.readChunk() } ?: break
                    sink(chunk)
                }
            } finally {
                stream.close()
            }
        } catch (c: TimeoutCancellationException) {
            throw IOException("timed out reading from $path", c)
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

    private suspend fun runShell(command: String): AdbResult {
        val stream = transport.open("shell:$command; echo $EXIT_CODE_MARKER\$?")
        try {
            val text = String(stream.readAll(), Charsets.UTF_8)
            val match = EXIT_CODE_LINE.find(text)
                ?: return AdbResult.CommandFailed("remote shell closed without an exit status")
            val exitCode = match.groupValues[1].toInt()
            val output = text.substring(0, match.range.first)
            return if (exitCode == 0) AdbResult.Success(output) else AdbResult.CommandFailed(
                "command exited with code $exitCode"
            )
        } finally {
            stream.close()
        }
    }
}
