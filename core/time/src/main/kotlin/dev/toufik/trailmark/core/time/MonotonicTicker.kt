package dev.toufik.trailmark.core.time

/**
 * Single source of truth for elapsed time. Never the wall clock.
 * A non-increasing sample is a reorder or replay: [accept] refuses it and
 * leaves the last accepted value untouched.
 */
class MonotonicTicker {
    var lastNanos: Long = -1L
        private set

    /** false => this sample cannot be trusted for durations. */
    fun accept(nanos: Long): Boolean {
        if (nanos <= lastNanos) return false
        lastNanos = nanos
        return true
    }

    fun secondsSince(fromNanos: Long): Double = (lastNanos - fromNanos) / 1e9
}
