package dev.toufik.trailmark.core.time

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MonotonicTickerTest {
    @Test
    fun rejectsNonMonotonicTimestampInsteadOfCorruptingDuration() {
        val t = MonotonicTicker()
        assertTrue(t.accept(1000))
        assertFalse(t.accept(900))
        assertFalse(t.accept(1000))
        // Rejection must not mutate state: lastNanos stays the last ACCEPTED value.
        assertEquals(1000L, t.lastNanos)
        assertTrue(t.accept(3_000_000_000L))
        assertEquals(2.999999, t.secondsSince(1000), 1e-9)
    }
}
