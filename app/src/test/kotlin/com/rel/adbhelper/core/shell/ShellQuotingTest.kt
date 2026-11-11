package com.rel.adbhelper.core.shell

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ShellQuotingTest {

    @Test
    fun `plain paths are single-quoted`() {
        assertEquals("'/sdcard/DCIM'", shellQuote("/sdcard/DCIM"))
    }

    @Test
    fun `embedded single quotes are closed-escaped-reopened`() {
        assertEquals("'bob'\\''s file.txt'", shellQuote("bob's file.txt"))
    }
}
