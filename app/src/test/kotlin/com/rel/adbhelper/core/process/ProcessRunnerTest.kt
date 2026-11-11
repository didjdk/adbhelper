package com.rel.adbhelper.core.process

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

// JVM test host here is Windows, so commands go through cmd /c rather than sh -c.
class ProcessRunnerTest {

    @Test
    fun `run returns exit code and captured stdout on Windows cmd`() = runTest {
        val result = RealProcessRunner().run(listOf("cmd", "/c", "echo hello"))
        assertEquals(0, result.exitCode)
        assertEquals("hello", result.stdout.trim())
    }

    @Test
    fun `run captures non-zero exit code and stderr separately on Windows cmd`() = runTest {
        val result = RealProcessRunner().run(listOf("cmd", "/c", "echo err 1>&2 & exit /b 3"))
        assertEquals(3, result.exitCode)
        assertEquals("err", result.stderr.trim())
        assertEquals("", result.stdout.trim())
    }
}
