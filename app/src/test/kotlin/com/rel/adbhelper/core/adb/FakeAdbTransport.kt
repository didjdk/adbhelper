package com.rel.adbhelper.core.adb

import kotlinx.coroutines.awaitCancellation

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
) : AdbTransport {

    /** Bytes written to each service, for asserting push behavior. */
    val writtenByService = mutableMapOf<String, MutableList<ByteArray>>()
    val closedServices = mutableSetOf<String>()

    override suspend fun open(service: String): AdbStream {
        if (hangOnOpen) awaitCancellation()
        throwOnOpen?.let { throw it }
        serviceOutputs[service]?.let { bytes ->
            return InMemoryAdbStream(
                bytes, throwOnRead, chunkSize,
                onWrite = { writtenByService.getOrPut(service) { mutableListOf() }.add(it) },
                onClose = { closedServices.add(service) },
            )
        }
        val bytes = if (omitExitMarker) {
            scriptedOutput.toByteArray(Charsets.UTF_8)
        } else {
            "${scriptedOutput}__ADBHELPER_EXIT__:$exitCode\n".toByteArray(Charsets.UTF_8)
        }
        return InMemoryAdbStream(
            bytes, throwOnRead, chunkSize,
            onWrite = { writtenByService.getOrPut(service) { mutableListOf() }.add(it) },
            onClose = { closedServices.add(service) },
        )
    }
}

private class InMemoryAdbStream(
    private val bytes: ByteArray,
    private val throwOnRead: Throwable?,
    private val chunkSize: Int,
    private val onWrite: (ByteArray) -> Unit = {},
    private val onClose: () -> Unit = {},
) : AdbStream {
    private var readPosition = 0
    override suspend fun write(data: ByteArray) = onWrite(data)
    override suspend fun readAll(): ByteArray {
        throwOnRead?.let { throw it }
        return bytes
    }
    override suspend fun readChunk(): ByteArray? {
        throwOnRead?.let { throw it }
        if (readPosition >= bytes.size) return null
        val end = minOf(readPosition + chunkSize, bytes.size)
        val chunk = bytes.copyOfRange(readPosition, end)
        readPosition = end
        return chunk
    }
    override fun close() = onClose()
}
