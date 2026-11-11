package com.rel.adbhelper.connect

import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger

class ScanVerifierTest {

    @Test
    fun `never runs more than the given concurrency at once`() = runTest {
        val inFlight = AtomicInteger(0)
        var maxInFlight = 0
        val candidates = (1..20).map { "10.0.2.$it:5555" }

        verifyScanCandidates(
            candidates = candidates,
            concurrency = 5,
            verify = { _, _ ->
                val current = inFlight.incrementAndGet()
                maxInFlight = maxOf(maxInFlight, current)
                delay(100)
                inFlight.decrementAndGet()
                true
            },
            onFound = {},
        )

        assertTrue(maxInFlight <= 5, "expected at most 5 concurrent verifications, saw $maxInFlight")
    }

    @Test
    fun `calls onFound as each candidate validates, not after the whole batch finishes`() = runTest {
        val delivered = mutableListOf<String>()
        val candidates = listOf("10.0.2.1:5555", "10.0.2.2:5555", "10.0.2.3:5555")

        verifyScanCandidates(
            candidates = candidates,
            concurrency = 5,
            // The second candidate "validates" fastest; proves delivery order follows
            // completion order, not input order (i.e. not buffered until the batch ends).
            verify = { host, _ ->
                delay(if (host == "10.0.2.2") 10L else 100L)
                true
            },
            onFound = { delivered += it },
        )

        assertEquals("10.0.2.2:5555", delivered.first())
        assertEquals(3, delivered.size)
    }

    @Test
    fun `candidates that fail verification are never reported found`() = runTest {
        val delivered = mutableListOf<String>()
        val candidates = listOf("10.0.2.1:5555", "10.0.2.2:5555")

        verifyScanCandidates(
            candidates = candidates,
            concurrency = 5,
            verify = { host, _ -> host == "10.0.2.1" },
            onFound = { delivered += it },
        )

        assertEquals(listOf("10.0.2.1:5555"), delivered)
    }

    @Test
    fun `a target that fails to parse is skipped rather than crashing the scan`() = runTest {
        val delivered = mutableListOf<String>()
        verifyScanCandidates(
            candidates = listOf("not-a-host-port", "10.0.2.1:5555"),
            concurrency = 5,
            verify = { _, _ -> true },
            onFound = { delivered += it },
        )
        assertEquals(listOf("10.0.2.1:5555"), delivered)
    }
}
