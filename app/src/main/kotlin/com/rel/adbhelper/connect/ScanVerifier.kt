package com.rel.adbhelper.connect

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * Verifies every `host:port` in [candidates] with [verify], running up to [concurrency] at once,
 * and calls [onFound] the instant each one passes — not after the whole batch finishes. A raw TCP
 * accept on the scan port isn't proof it's really adb on the other end; [verify] is expected to
 * do the real (and slower) handshake check. Running several of those at once instead of one at a
 * time is what keeps a scan with a handful of survivors from taking several seconds per candidate
 * in sequence, and surfacing each result as it lands — rather than collecting the whole list
 * first — is what keeps the screen from sitting blank until the slowest (or a timed-out)
 * candidate finishes.
 */
suspend fun verifyScanCandidates(
    candidates: List<String>,
    concurrency: Int,
    verify: suspend (host: String, port: Int) -> Boolean,
    onFound: (String) -> Unit,
) {
    val semaphore = Semaphore(concurrency)
    coroutineScope {
        candidates.map { target ->
            async {
                val (host, port) = parseHostPort(target) ?: return@async
                val reachable = semaphore.withPermit { verify(host, port) }
                if (reachable) onFound(target)
            }
        }.forEach { it.await() }
    }
}
