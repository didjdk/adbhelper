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
}
