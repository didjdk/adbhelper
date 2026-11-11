package com.rel.adbhelper.core.adb

import java.io.ByteArrayOutputStream

/** Test fixture: an in-memory [AdbTransport] that scripts a shell v2 packet response (or a failure to open). */
class FakeAdbTransport(
    private val scriptedOutput: String = "",
    private val exitCode: Int = 0,
    private val throwOnOpen: Throwable? = null,
) : AdbTransport {

    override suspend fun open(service: String): AdbStream {
        throwOnOpen?.let { throw it }
        return InMemoryAdbStream(encodeShellV2Packets(scriptedOutput, exitCode))
    }
}

private class InMemoryAdbStream(private val bytes: ByteArray) : AdbStream {
    override suspend fun write(data: ByteArray) = Unit
    override suspend fun readAll(): ByteArray = bytes
    override fun close() = Unit
}

private fun encodeShellV2Packets(output: String, exitCode: Int): ByteArray {
    val buffer = ByteArrayOutputStream()
    fun writePacket(id: Int, payload: ByteArray) {
        buffer.write(id)
        val length = payload.size
        buffer.write(length and 0xFF)
        buffer.write((length shr 8) and 0xFF)
        buffer.write((length shr 16) and 0xFF)
        buffer.write((length shr 24) and 0xFF)
        buffer.write(payload)
    }
    val outputBytes = output.toByteArray(Charsets.UTF_8)
    if (outputBytes.isNotEmpty()) writePacket(1, outputBytes)
    writePacket(3, byteArrayOf(exitCode.toByte()))
    return buffer.toByteArray()
}
