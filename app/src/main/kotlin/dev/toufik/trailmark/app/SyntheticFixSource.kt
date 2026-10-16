package dev.toufik.trailmark.app

import dev.toufik.trailmark.core.model.RawFix
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * A straight-line walk at a constant speed, monotonic timestamps identical to
 * the platform shape. Proves the whole pipeline with no GPS hardware.
 */
class SyntheticFixSource(
    private val everyMs: Long = 1000,
    private val speedMps: Double = 1.4,
    private val accuracyM: Double = 3.0,
) {
    fun fixes(startNanos: Long): Flow<RawFix> = flow {
        var lat = 36.7538
        var t = startNanos
        while (true) {
            emit(RawFix(lat, 3.1856, t, accuracyM, speedMps = speedMps))
            lat += speedMps * (everyMs / 1000.0) / 111111.0
            t += everyMs * 1_000_000L
            delay(everyMs)
        }
    }
}
