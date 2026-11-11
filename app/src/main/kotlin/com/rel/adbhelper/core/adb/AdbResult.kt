package com.rel.adbhelper.core.adb

/** Distinguishes a successful command from a command the target rejected from the transport itself being unreachable — the three outcomes callers need to show different messages for. */
sealed class AdbResult {
    data class Success(val output: String) : AdbResult()
    data class CommandFailed(val message: String) : AdbResult()
    data class TransportUnreachable(val cause: Throwable) : AdbResult()
}
