package com.rel.adbhelper.core.adb

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class ConnectGateTest {

    @Test
    fun `concurrent callers racing in before any connect exists invoke connect exactly once`() = runTest {
        val gate = ConnectGate()
        val connected = AtomicBoolean(false)
        val connectCalls = AtomicInteger(0)

        val results = (1..5).map {
            async {
                gate.ensureConnected(
                    alreadyConnected = { connected.get() },
                    connect = {
                        connectCalls.incrementAndGet()
                        delay(100) // simulates a slow real handshake the others must wait out
                        connected.set(true)
                        true
                    },
                )
            }
        }.awaitAll()

        assertEquals(1, connectCalls.get(), "expected only one of the 5 racing callers to actually connect")
        assertTrue(results.all { it })
    }

    @Test
    fun `a caller that arrives already connected never calls connect`() = runTest {
        val gate = ConnectGate()
        var connectCalled = false

        val result = gate.ensureConnected(
            alreadyConnected = { true },
            connect = { connectCalled = true; true },
        )

        assertTrue(result)
        assertTrue(!connectCalled)
    }
}
