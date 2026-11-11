package com.rel.adbhelper.core.adb

/**
 * Marker appended to every shell command's output so [shell] can recover the remote exit code.
 * Plain `shell:<command>` service strings (what [AdbTransport] implementations expose) return
 * raw decoded stdout with no separate exit-status channel, so the exit code is captured the
 * same way a plain adb shell session would: run the command, then echo `$?`.
 */
private const val EXIT_CODE_MARKER = "__ADBHELPER_EXIT__:"
private val EXIT_CODE_LINE = Regex("$EXIT_CODE_MARKER(\\d+)\\s*\$")

class AdbClient(private val transport: AdbTransport) {

    suspend fun shell(command: String): AdbResult {
        val stream = try {
            transport.open("shell:$command; echo $EXIT_CODE_MARKER\$?")
        } catch (t: Throwable) {
            return AdbResult.TransportUnreachable(t)
        }
        return try {
            val text = String(stream.readAll(), Charsets.UTF_8)
            val match = EXIT_CODE_LINE.find(text)
                ?: return AdbResult.CommandFailed("remote shell closed without an exit status")
            val exitCode = match.groupValues[1].toInt()
            val output = text.substring(0, match.range.first)
            if (exitCode == 0) AdbResult.Success(output) else AdbResult.CommandFailed(
                "command exited with code $exitCode"
            )
        } finally {
            stream.close()
        }
    }
}
