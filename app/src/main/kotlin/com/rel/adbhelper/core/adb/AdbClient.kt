package com.rel.adbhelper.core.adb

import kotlinx.coroutines.CancellationException
import java.io.IOException

/**
 * Marker appended to every shell command's output so [shell] can recover the remote exit code.
 * Plain `shell:<command>` service strings (what [AdbTransport] implementations expose) return
 * raw decoded stdout with no separate exit-status channel, so the exit code is captured the
 * same way a plain adb shell session would: run the command, then echo `$?`.
 */
private const val EXIT_CODE_MARKER = "__ADBHELPER_EXIT__:"
private val EXIT_CODE_LINE = Regex("$EXIT_CODE_MARKER(\\d+)\\s*\$")

class AdbClient(private val transport: AdbTransport) {

    suspend fun shell(command: String): AdbResult = try {
        runShell(command)
    } catch (c: CancellationException) {
        throw c
    } catch (t: Throwable) {
        AdbResult.TransportUnreachable(t)
    }

    /** Opens a long-lived service stream (e.g. `"shell:"` for a terminal, `"localabstract:<socket>"`
     * for mirroring). The caller owns the stream's lifecycle. */
    suspend fun openService(service: String): AdbStream = transport.open(service)

    /** Reads a raw service's full byte output — no shell exit-marker framing. */
    suspend fun raw(service: String): ByteArray = try {
        val stream = transport.open(service)
        try {
            stream.readAll()
        } finally {
            stream.close()
        }
    } catch (c: CancellationException) {
        throw c
    } catch (t: Throwable) {
        throw IOException("failed to read from $service", t)
    }

    /** Streams [data] onto the target's [path] via `exec:cat >`. */
    suspend fun push(path: String, data: ByteArray, chunkSize: Int = 256 * 1024): Unit {
        val stream = transport.open("exec:cat > '$path'")
        try {
            data.toList().chunked(chunkSize).forEach { chunk ->
                stream.write(chunk.toByteArray())
            }
        } finally {
            stream.close()
        }
    }

    /** Reads a file off the target via `exec:cat`. */
    suspend fun pull(path: String): ByteArray = raw("exec:cat '$path'")

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
