package com.rel.adbhelper.core.adb

/**
 * One connection to an ADB daemon, opened over whatever medium a concrete implementation uses
 * (network socket, USB). Modeled on the real ADB protocol's "open a stream for a service
 * string" shape (e.g. `"shell,v2,raw:ls /sdcard"`) so a real transport is a straight
 * implementation of this interface, not a redesign.
 */
interface AdbTransport {
    suspend fun open(service: String): AdbStream
}

interface AdbStream {
    suspend fun write(data: ByteArray)
    suspend fun readAll(): ByteArray

    /** Next available bytes, or null once the stream is at EOF/closed — how interactive
     * services (terminal, mirror video) consume output incrementally. */
    suspend fun readChunk(): ByteArray?

    fun close()
}
