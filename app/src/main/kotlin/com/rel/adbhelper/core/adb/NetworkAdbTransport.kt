package com.rel.adbhelper.core.adb

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * [AdbTransport] over a TCP connection to `host:port`, via the real ADB protocol client
 * library (`io.github.muntashirakon:libadb-android`). The library's connection manager is a
 * singleton with one active connection at a time; this transport connects it to [host]:[port]
 * on every open, reconnecting first if the manager is currently on a different target (see
 * [AdbKeyConnectionManager.connectTo]) — reusing a connection to the wrong device would route
 * later commands (install, uninstall, reboot, ...) to the wrong target.
 */
class NetworkAdbTransport(
    private val context: Context,
    private val host: String,
    private val port: Int,
) : AdbTransport {

    private val manager get() = AdbKeyConnectionManager.getInstance(context)

    override suspend fun open(service: String): AdbStream = withContext(Dispatchers.IO) {
        if (!manager.connectTo(host, port)) {
            throw IOException("Connection to $host:$port was rejected or could not be established")
        }
        LibraryAdbStream(manager.openStream(service))
    }
}

private class LibraryAdbStream(private val delegate: io.github.muntashirakon.adb.AdbStream) : AdbStream {

    private val input by lazy { delegate.openInputStream() }

    override suspend fun write(data: ByteArray) = withContext(Dispatchers.IO) {
        delegate.openOutputStream().write(data)
    }

    override suspend fun readAll(): ByteArray = withContext(Dispatchers.IO) {
        input.readBytes()
    }

    override suspend fun readChunk(): ByteArray? = withContext(Dispatchers.IO) {
        val buffer = ByteArray(4096)
        val read = input.read(buffer)
        if (read <= 0) null else buffer.copyOf(read)
    }

    override fun close() = delegate.close()
}
