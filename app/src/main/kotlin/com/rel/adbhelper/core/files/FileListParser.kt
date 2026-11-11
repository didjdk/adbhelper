package com.rel.adbhelper.core.files

data class FileEntry(
    val name: String,
    val path: String,
    val isDir: Boolean,
    val isSymlink: Boolean,
    val size: Long,
    val perms: String,
    val owner: String,
    val mtime: String,
)

/**
 * Parses toybox `ls -la <dir>`: perms links owner group size date time name[ -> target].
 * `total N` headers are skipped; names with spaces are re-joined; a file with no columns is
 * dropped rather than crashing the browser.
 */
fun parseLsLa(raw: String, dir: String): List<FileEntry> =
    raw.lines()
        .filter { it.isNotBlank() && !it.startsWith("total ") }
        .mapNotNull { line ->
            val cols = line.trim().split(Regex("\\s+"), limit = 8)
            if (cols.size < 8) return@mapNotNull null
            val perms = cols[0]
            var name = cols[7]
            val isSymlink = perms.startsWith("l")
            if (isSymlink) name = name.substringBefore(" ->")
            FileEntry(
                name = name,
                path = "$dir/$name".replace("//", "/"),
                isDir = perms.startsWith("d"),
                isSymlink = isSymlink,
                size = cols[4].toLongOrNull() ?: 0,
                perms = perms,
                owner = cols[2],
                mtime = "${cols[5]} ${cols[6]}",
            )
        }
