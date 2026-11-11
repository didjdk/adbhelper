package com.rel.adbhelper.core.adb

import kotlinx.coroutines.CancellationException

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
