package dev.toufik.trailmark.app.gps

import dev.toufik.trailmark.core.model.RawFix
import dev.toufik.trailmark.engine.FixSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * A synthetic walk for on-device testing (no GPS hardware needed). Starts inside
 * the bundled Alger pack's bbox and heads north-east at a steady pace, so the
 * whole pipeline and the offline map can be exercised without leaving the desk.
 */
class SyntheticFixSource(
    private val everyMs: Long = 1000,
    private val speedMps: Double = 1.4,
    private val accuracyM: Double = 3.0,
) : FixSource {
    override fun fixes(): Flow<RawFix> = flow {
        var lat = 36.7538
        var lon = 3.0588
        var t = 0L
        while (true) {
            emit(RawFix(lat, lon, t, accuracyM, speedMps = speedMps))
            val dLat = speedMps * (everyMs / 1000.0) / 111111.0
            lat += dLat
            lon += dLat * 0.7
            t += everyMs * 1_000_000L
            delay(everyMs)
        }
    }
}
