package com.rel.adbhelper.connect

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ParseHostPortTest {

    @Test
    fun `parses a valid host colon port`() {
        assertEquals("192.168.1.5" to 5555, parseHostPort("192.168.1.5:5555"))
    }

    @Test
    fun `trims surrounding whitespace`() {
        assertEquals("192.168.1.5" to 5555, parseHostPort("  192.168.1.5:5555  "))
    }

    @Test
    fun `rejects blank input`() {
        assertNull(parseHostPort(""))
    }

    @Test
    fun `rejects input with no colon`() {
        assertNull(parseHostPort("192.168.1.5"))
    }

    @Test
    fun `rejects a non-numeric port`() {
        assertNull(parseHostPort("192.168.1.5:abc"))
    }

    @Test
    fun `rejects a port out of range`() {
        assertNull(parseHostPort("192.168.1.5:99999"))
    }
}
