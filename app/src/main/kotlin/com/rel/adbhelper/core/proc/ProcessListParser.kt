package com.rel.adbhelper.core.proc

data class ProcessEntry(val pid: Int, val user: String, val name: String) {
    val isSystem: Boolean get() = user == "root" || user == "system"
}

/**
 * Parses toybox `ps -A` output: USER PID PPID VSZ RSS WCHAN ADDR S NAME. Only USER, PID and
 * NAME are consumed; NAME is the last column and may itself contain no spaces in ps output.
 */
fun parsePs(raw: String): List<ProcessEntry> =
    raw.lines()
        .filter { it.isNotBlank() && !it.startsWith("USER") }
        .map { line ->
            val cols = line.trim().split(Regex("\\s+"))
            ProcessEntry(
                pid = cols.getOrNull(1)?.toIntOrNull() ?: 0,
                user = cols.getOrNull(0).orEmpty(),
                name = cols.last(),
            )
        }
        .filter { it.pid > 0 }
