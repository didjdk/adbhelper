package com.rel.adbhelper.core.files

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FileListParserTest {

    // Fixture: real `ls -la /storage/self/primary/` output from the dev emulator, trimmed.
    private val fixture = """
total 10168
drwxrws--- 2 u0_a212  media_rw    4096 2026-10-26 13:55 .trash-storage
drwxrws--x 5 media_rw media_rw    4096 2026-10-26 13:55 Android
drwxrws--- 2 u0_a212  media_rw    4096 2026-10-26 13:55 DCIM
-rw-rw---- 1 u0_a212  media_rw  123456 2026-10-26 21:30 a file with spaces.txt
lrw-r--r-- 1 root root 21 2009-01-01 00:00 link -> /storage/self/primary
    """.trimIndent()

    @Test
    fun `parseLsLa skips total and reads dir file and symlink entries`() {
        val entries = parseLsLa(fixture, "/storage/self/primary")
        assertEquals(5, entries.size)
        assertEquals(
            FileEntry(
                name = ".trash-storage", path = "/storage/self/primary/.trash-storage",
                isDir = true, isSymlink = false, size = 4096,
                perms = "drwxrws---", owner = "u0_a212", mtime = "2026-10-26 13:55",
            ),
            entries[0],
        )
        assertTrue(entries[0].name.startsWith("."))
    }

    @Test
    fun `names with spaces survive and symlinks are flagged`() {
        val entries = parseLsLa(fixture, "/storage/self/primary")
        assertEquals("a file with spaces.txt", entries[3].name)
        assertFalse(entries[3].isDir)
        assertTrue(entries[4].isSymlink)
        assertEquals("link", entries[4].name)
        assertEquals(21, entries[4].size)
    }

    @Test
    fun `empty directory yields no entries`() {
        assertEquals(0, parseLsLa("total 0", "/x").size)
    }
}
