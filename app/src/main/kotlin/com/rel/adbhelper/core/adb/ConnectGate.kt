package com.rel.adbhelper.core.adb

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Serializes "ensure we're connected to this target" so concurrent callers (e.g. the several
 * `shell()` calls a single screen's `load()` fires in parallel) can't race into establishing
 * two overlapping connections to the same target at once. Live-reproduced: that race was
 * confusing the underlying multiplexed ADB stream protocol badly enough that even a trivial
 * command (`pm list packages`, under 100ms over plain `adb shell`) took 10+ seconds through
 * this app, against a real device, after [AdbKeyConnectionManager.connectTo]'s own `@Synchronized`
 * was removed (to fix an unrelated non-cancellable-hang bug) without anything else taking over
 * the job of keeping concurrent connect attempts from overlapping.
 *
 * A coroutine [Mutex], not a plain lock: if the one connect attempt holding it genuinely hangs
 * forever — the accepted, documented limitation on a target that completes the TCP handshake but
 * never finishes the ADB handshake — every other caller's wait on this mutex is still a real
 * suspension point, so it stays cancellable by that caller's own timeout instead of also hanging.
 */
class ConnectGate {
    private val mutex = Mutex()

    /**
     * Returns true immediately (without taking the lock's turn to call [connect]) once
     * [alreadyConnected] says so — including for a caller that arrives while another is mid-[connect].
     */
    suspend fun ensureConnected(alreadyConnected: () -> Boolean, connect: suspend () -> Boolean): Boolean =
        mutex.withLock {
            if (alreadyConnected()) true else connect()
        }
}
