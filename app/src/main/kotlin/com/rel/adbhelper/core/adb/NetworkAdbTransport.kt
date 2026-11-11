package com.rel.adbhelper.core.adb

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * [AdbTransport] over a TCP connection to `host:port`, via the real ADB protocol client
 * library (`io.github.muntashirakon:libadb-android`). By default this connects through the
 * shared [AdbKeyConnectionManager] singleton (one active connection at a time), reconnecting
 * first if the manager is currently on a different target (see [AdbKeyConnectionManager.connectTo])
 * — reusing a connection to the wrong device would route later commands (install, uninstall,
 * reboot, ...) to the wrong target. [manager] can be overridden with a disposable instance (see
 * [AdbKeyConnectionManager.createDisposable]) for callers that need several independent, truly
 * concurrent connections instead — e.g. verifying several LAN scan candidates at once.
 */
class NetworkAdbTransport(
    private val context: Context,
    private val host: String,
    private val port: Int,
    private val manager: AdbKeyConnectionManager = AdbKeyConnectionManager.getInstance(context),
) : AdbTransport {

    override suspend fun open(service: String): AdbStream = withContext(Dispatchers.IO) {
        if (!manager.connectTo(host, port)) {
            throw IOException("Connection to $host:$port was rejected or could not be established")
        }
        LibraryAdbStream(manager.openStreamSerialized(service))
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
