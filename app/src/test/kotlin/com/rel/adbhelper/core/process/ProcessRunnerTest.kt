package com.rel.adbhelper.core.process

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

private val isWindows = System.getProperty("os.name").orEmpty().contains("Windows", ignoreCase = true)

private fun echoStdoutCommand(text: String): List<String> =
    if (isWindows) listOf("cmd", "/c", "echo $text") else listOf("sh", "-c", "echo $text")

private fun echoStderrAndExitCommand(text: String, code: Int): List<String> =
    if (isWindows) listOf("cmd", "/c", "echo $text 1>&2 & exit /b $code")
    else listOf("sh", "-c", "echo $text 1>&2; exit $code")

class ProcessRunnerTest {

    @Test
    fun `run returns exit code and captured stdout`() = runTest {
        val result = RealProcessRunner().run(echoStdoutCommand("hello"))
        assertEquals(0, result.exitCode)
        assertEquals("hello", result.stdout.trim())
    }

    @Test
    fun `run captures non-zero exit code and stderr separately`() = runTest {
        val result = RealProcessRunner().run(echoStderrAndExitCommand("err", 3))
        assertEquals(3, result.exitCode)
        assertEquals("err", result.stderr.trim())
        assertEquals("", result.stdout.trim())
    }
}
