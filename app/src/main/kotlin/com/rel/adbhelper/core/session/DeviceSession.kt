package com.rel.adbhelper.core.session

import com.rel.adbhelper.core.adb.AdbClient

/** The single connected target, shared by every device screen. Attached by the Connect
 * screen on success, detached when leaving the device hub. One connection at a time —
 * enforced by the underlying connection manager being a singleton. */
object DeviceSession {
    var target: String? = null
        private set
    var client: AdbClient? = null
        private set

    fun attach(target: String, client: AdbClient) {
        this.target = target
        this.client = client
    }

    fun detach() {
        target = null
        client = null
    }
}
