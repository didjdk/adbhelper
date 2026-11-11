package com.rel.adbhelper.core.adb

/** Test fixture: an in-memory [AdbTransport] that scripts a shell response (or a failure to open). */
class FakeAdbTransport(
    private val scriptedOutput: String = "",
    private val exitCode: Int = 0,
    private val throwOnOpen: Throwable? = null,
) : AdbTransport {

    override suspend fun open(service: String): AdbStream {
        throwOnOpen?.let { throw it }
        val bytes = "${scriptedOutput}__ADBHELPER_EXIT__:$exitCode\n".toByteArray(Charsets.UTF_8)
        return InMemoryAdbStream(bytes)
    }
}

private class InMemoryAdbStream(private val bytes: ByteArray) : AdbStream {
    override suspend fun write(data: ByteArray) = Unit
    override suspend fun readAll(): ByteArray = bytes
    override fun close() = Unit
}
