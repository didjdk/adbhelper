package com.rel.adbhelper.core.adb

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
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
}
