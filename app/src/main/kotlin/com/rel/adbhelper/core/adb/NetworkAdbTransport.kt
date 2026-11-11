package com.rel.adbhelper.core.adb

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * [AdbTransport] over a TCP connection to `host:port`, via the real ADB protocol client
 * library (`io.github.muntashirakon:libadb-android`). The library's connection manager is a
 * singleton with one active connection at a time; this transport connects it to [host]:[port]
 * on first use if it isn't already connected to that same target.
 */
class NetworkAdbTransport(
    private val context: Context,
    private val host: String,
    private val port: Int,
) : AdbTransport {

    private val manager get() = AdbKeyConnectionManager.getInstance(context)

    override suspend fun open(service: String): AdbStream = withContext(Dispatchers.IO) {
        ensureConnected()
        LibraryAdbStream(manager.openStream(service))
    }

    private fun ensureConnected() {
        if (manager.isConnected) return
        manager.connect(host, port)
    }
}

private class LibraryAdbStream(private val delegate: io.github.muntashirakon.adb.AdbStream) : AdbStream {

    override suspend fun write(data: ByteArray) = withContext(Dispatchers.IO) {
        delegate.openOutputStream().write(data)
    }

    override suspend fun readAll(): ByteArray = withContext(Dispatchers.IO) {
        delegate.openInputStream().readBytes()
    }

    override fun close() = delegate.close()
}
