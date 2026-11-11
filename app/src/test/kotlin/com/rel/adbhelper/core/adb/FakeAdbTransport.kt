package com.rel.adbhelper.core.adb

/** Test fixture: an in-memory [AdbTransport] that scripts a shell response (or a failure to open/read). */
class FakeAdbTransport(
    private val scriptedOutput: String = "",
    private val exitCode: Int = 0,
    private val throwOnOpen: Throwable? = null,
    private val throwOnRead: Throwable? = null,
    private val omitExitMarker: Boolean = false,
) : AdbTransport {

    override suspend fun open(service: String): AdbStream {
        throwOnOpen?.let { throw it }
        val bytes = if (omitExitMarker) {
            scriptedOutput.toByteArray(Charsets.UTF_8)
        } else {
            "${scriptedOutput}__ADBHELPER_EXIT__:$exitCode\n".toByteArray(Charsets.UTF_8)
        }
        return InMemoryAdbStream(bytes, throwOnRead)
    }
}

private class InMemoryAdbStream(
    private val bytes: ByteArray,
    private val throwOnRead: Throwable?,
) : AdbStream {
    override suspend fun write(data: ByteArray) = Unit
    override suspend fun readAll(): ByteArray {
        throwOnRead?.let { throw it }
        return bytes
    }
    override fun close() = Unit
}
