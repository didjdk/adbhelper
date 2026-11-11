package com.rel.adbhelper.core.proc

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ProcessListParserTest {

    // Fixture: real `ps -A` output from the dev emulator, trimmed.
    private val fixture = """
USER           PID  PPID        VSZ    RSS WCHAN            ADDR S NAME
root             1     0   10887928  11348 0                   0 S init
root             2     0          0      0 0                   0 S [kthreadd]
system        1234   900      12345  9999 0                   0 S com.android.systemui
u0_a212       5678   900     990000 512000 0                   0 S com.tranzmate
    """.trimIndent()

    @Test
    fun `parsePs skips the header and reads every process row`() {
        val processes = parsePs(fixture)
        assertEquals(4, processes.size)
        assertEquals(ProcessEntry(pid = 1, user = "root", name = "init"), processes[0])
        assertEquals("com.android.systemui", processes[2].name)
        assertEquals(5678, processes[3].pid)
    }

    @Test
    fun `root and system users count as system processes, app users do not`() {
        val processes = parsePs(fixture)
        assertTrue(processes[0].isSystem)
        assertTrue(processes[2].isSystem)
        assertEquals(false, processes[3].isSystem)
    }

    @Test
    fun `empty and header-only output yield no rows`() {
        assertEquals(0, parsePs("USER PID NAME").size)
        assertEquals(0, parsePs("").size)
    }
}
