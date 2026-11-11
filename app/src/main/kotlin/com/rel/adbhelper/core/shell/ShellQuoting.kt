package com.rel.adbhelper.core.shell

/** POSIX single-quote escaping for paths and arguments interpolated into shell service strings. */
fun shellQuote(value: String): String = "'" + value.replace("'", "'\\''") + "'"
