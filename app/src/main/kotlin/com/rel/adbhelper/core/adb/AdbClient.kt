package com.rel.adbhelper.core.adb

/** Shell protocol v2 packet type ids, per the ADB protocol's SHELL_PROTOCOL spec. */
private const val SHELL_PACKET_STDOUT = 1
private const val SHELL_PACKET_EXIT = 3

class AdbClient(private val transport: AdbTransport) {

    suspend fun shell(command: String): AdbResult {
        val stream = try {
            transport.open("shell,v2,raw:$command")
        } catch (t: Throwable) {
            return AdbResult.TransportUnreachable(t)
        }
        return try {
            val (stdout, exitCode) = parseShellV2Packets(stream.readAll())
            if (exitCode == 0) AdbResult.Success(stdout) else AdbResult.CommandFailed(
                "command exited with code $exitCode"
            )
        } finally {
            stream.close()
        }
    }
}

/** Decodes a shell v2 packet stream (id:1 byte, length:4 bytes LE, payload) into stdout text and exit code. */
private fun parseShellV2Packets(bytes: ByteArray): Pair<String, Int> {
    var offset = 0
    val stdout = StringBuilder()
    var exitCode = 0
    while (offset + 5 <= bytes.size) {
        val id = bytes[offset].toInt() and 0xFF
        val length = (bytes[offset + 1].toInt() and 0xFF) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
            ((bytes[offset + 3].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 4].toInt() and 0xFF) shl 24)
        val payloadStart = offset + 5
        val payloadEnd = payloadStart + length
        if (payloadEnd > bytes.size) break
        when (id) {
            SHELL_PACKET_STDOUT -> stdout.append(String(bytes, payloadStart, length, Charsets.UTF_8))
            SHELL_PACKET_EXIT -> if (length >= 1) exitCode = bytes[payloadStart].toInt() and 0xFF
        }
        offset = payloadEnd
    }
    return stdout.toString() to exitCode
}
