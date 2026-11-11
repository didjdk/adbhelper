package com.rel.adbhelper.core.adb

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.IOException

class AdbClientTest {

    @Test
    fun `shell returns Success with command output when transport succeeds`() = runTest {
        val transport = FakeAdbTransport(scriptedOutput = "hello\n", exitCode = 0)
        val result = AdbClient(transport).shell("echo hello")
        assertEquals(AdbResult.Success("hello\n"), result)
    }

    @Test
    fun `shell returns CommandFailed when the remote command exits non-zero`() = runTest {
        val transport = FakeAdbTransport(scriptedOutput = "", exitCode = 1)
        val result = AdbClient(transport).shell("false")
        assertTrue(result is AdbResult.CommandFailed)
    }

    @Test
    fun `shell returns TransportUnreachable when opening the stream throws`() = runTest {
        val transport = FakeAdbTransport(throwOnOpen = IOException("refused"))
        val result = AdbClient(transport).shell("echo hi")
        assertTrue(result is AdbResult.TransportUnreachable)
    }

    @Test
    fun `shell returns TransportUnreachable when the connection drops mid-read`() = runTest {
        val transport = FakeAdbTransport(throwOnRead = IOException("connection reset"))
        val result = AdbClient(transport).shell("echo hi")
        assertTrue(result is AdbResult.TransportUnreachable)
    }

    @Test
    fun `shell returns CommandFailed when the stream ends without an exit marker`() = runTest {
        val transport = FakeAdbTransport(scriptedOutput = "partial output", omitExitMarker = true)
        val result = AdbClient(transport).shell("echo hi")
        assertTrue(result is AdbResult.CommandFailed)
    }

    @Test
    fun `shell rethrows cancellation rather than reporting it as a transport failure`() = runTest {
        val transport = FakeAdbTransport(throwOnRead = CancellationException("cancelled"))
        assertThrows(CancellationException::class.java) {
            kotlinx.coroutines.runBlocking { AdbClient(transport).shell("echo hi") }
        }
    }

    @Test
    fun `shell times out against an unresponsive transport rather than hanging forever`() = runTest {
        val transport = FakeAdbTransport(hangOnOpen = true)
        val result = AdbClient(transport).shell("echo hi")
        assertTrue(result is AdbResult.TransportUnreachable)
    }

    @Test
    fun `raw times out against an unresponsive transport rather than hanging forever`() = runTest {
        val transport = FakeAdbTransport(hangOnOpen = true)
        var threwIOException = false
        try {
            AdbClient(transport).raw("exec:screencap -p")
        } catch (e: IOException) {
            threwIOException = true
        }
        assertTrue(threwIOException)
    }

    @Test
    fun `raw returns the stream bytes verbatim without any exit-marker framing`() = runTest {
        val payload = byteArrayOf(0x89.toByte(), 0x50.toByte(), 0x4E.toByte(), 0x47.toByte(), 0x00.toByte(), 0x0A.toByte())
        val transport = FakeAdbTransport(serviceOutputs = mapOf("exec:screencap -p" to payload))
        val bytes = AdbClient(transport).raw("exec:screencap -p")
        assertTrue(payload.contentEquals(bytes))
    }

    @Test
    fun `push writes the payload to the exec service and closes the stream`() = runTest {
        val payload = "binary-ish".toByteArray()
        val transport = FakeAdbTransport()
        AdbClient(transport).push("/data/local/tmp/x", payload)
        val written = transport.writtenByService["exec:cat > '/data/local/tmp/x'"].orEmpty()
        assertTrue(written.single().contentEquals(payload))
        assertTrue("exec:cat > '/data/local/tmp/x'" in transport.closedServices)
    }

    @Test
    fun `pull returns the streamed bytes for the requested path`() = runTest {
        val payload = "file content".toByteArray()
        val transport = FakeAdbTransport(serviceOutputs = mapOf("exec:cat '/sdcard/a.txt'" to payload))
        val bytes = AdbClient(transport).pull("/sdcard/a.txt")
        assertTrue(payload.contentEquals(bytes))
    }

    @Test
    fun `pullTo delivers the file in bounded chunks without ever holding it all at once`() = runTest {
        val payload = ByteArray(10) { it.toByte() }
        val transport = FakeAdbTransport(serviceOutputs = mapOf("exec:cat '/sdcard/big.bin'" to payload), chunkSize = 4)
        val delivered = mutableListOf<ByteArray>()
        var maxSeenAtOnce = 0
        AdbClient(transport).pullTo("/sdcard/big.bin") { chunk ->
            delivered += chunk
            maxSeenAtOnce = maxOf(maxSeenAtOnce, chunk.size)
        }
        assertEquals(10, delivered.sumOf { it.size })
        assertTrue(maxSeenAtOnce <= 4)
    }

    @Test
    fun `readChunk yields the stream in bounded chunks and then null`() = runTest {
        val payload = ByteArray(5) { it.toByte() }
        val transport = FakeAdbTransport(serviceOutputs = mapOf("s" to payload), chunkSize = 2)
        val stream = transport.open("s")
        assertEquals(listOf<Byte>(0, 1), stream.readChunk()!!.toList())
        assertEquals(listOf<Byte>(2, 3), stream.readChunk()!!.toList())
        assertEquals(listOf<Byte>(4), stream.readChunk()!!.toList())
        assertEquals(null, stream.readChunk())
    }
}
