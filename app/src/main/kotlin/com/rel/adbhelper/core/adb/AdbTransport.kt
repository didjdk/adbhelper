package com.rel.adbhelper.core.adb

/**
 * One connection to an ADB daemon, opened over whatever medium a concrete implementation uses
 * (network socket, USB). Modeled on the real ADB protocol's "open a stream for a service
 * string" shape (e.g. `"shell,v2,raw:ls /sdcard"`) so a real transport is a straight
 * implementation of this interface, not a redesign.
 */
interface AdbTransport {
    suspend fun open(service: String): AdbStream

    /**
     * Tells this transport its underlying connection may be unhealthy and should not be trusted
     * as-is next time — called when a caller had to force-close a stuck stream (see
     * [AdbKeyConnectionManager]'s doc on why a watchdog-closed stream is not proof the whole
     * shared connection is still fine). Live-reproduced: one command that genuinely timed out
     * left the connection silently broken for every later one-shot call in the same session
     * (screenshot, app list, device info all failing afterward) because nothing told the next
     * [open] to reconnect instead of reusing it. Default no-op for transports with no shared,
     * reusable connection to poison in the first place.
     */
    fun invalidate() {}
}

interface AdbStream {
    suspend fun write(data: ByteArray)
    suspend fun readAll(): ByteArray

    /** Next available bytes, or null once the stream is at EOF/closed — how interactive
     * services (terminal, mirror video) consume output incrementally. */
    suspend fun readChunk(): ByteArray?

    fun close()
}
