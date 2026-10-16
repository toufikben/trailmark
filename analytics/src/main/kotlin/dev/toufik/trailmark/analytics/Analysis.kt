package dev.toufik.trailmark.analytics

import dev.toufik.trailmark.core.geo.haversineMeters
import dev.toufik.trailmark.core.model.StoredPoint
import dev.toufik.trailmark.core.model.WaypointType
import dev.toufik.trailmark.engine.Split
import dev.toufik.trailmark.engine.computeSplits

/** Everything the detail screen derives from a recorded track, in one pass. */
data class Analysis(
    val distanceM: Double,
    val elapsedNanos: Long,
    val movingNanos: Long,
    val elevationGainM: Double,
    val elevationLossM: Double,
    val maxSpeedMps: Double,
    val avgSpeedMps: Double,
    val splits: List<Split>,
)

/** A vertical delta below this is GPS/altimeter noise, not a hill. */
private const val ELEVATION_NOISE_M = 1.0

/** A horizontal delta above this counts as movement (moving-time, not a parked drift). */
private const val MOVING_EPSILON_M = 0.5

/**
 * Rejected points are raw evidence, never path. Elevation sums only measured
 * deltas; max speed prefers the measured value and falls back to dist/dt.
 */
fun analyze(points: List<StoredPoint>, elapsedNanos: Long): Analysis {
    val route = points.filter { it.type != WaypointType.REJECTED }
    var distanceM = 0.0
    var movingNanos = 0L
    var gainM = 0.0
    var lossM = 0.0
    var maxSpeedMps = 0.0

    var prev: StoredPoint? = null
    for (p in route) {
        val a = prev
        if (a != null) {
            val d = haversineMeters(a.lat, a.lon, p.lat, p.lon)
            distanceM += d
            val dt = p.timeNanos - a.timeNanos
            if (d > MOVING_EPSILON_M && dt > 0) movingNanos += dt

            val speed = p.speedMps ?: a.speedMps ?: (if (dt > 0) d / (dt / 1e9) else 0.0)
            if (speed > maxSpeedMps) maxSpeedMps = speed

            val za = a.altitudeM
            val zb = p.altitudeM
            if (za != null && zb != null) {
                val dz = zb - za
                if (dz > ELEVATION_NOISE_M) gainM += dz
                else if (dz < -ELEVATION_NOISE_M) lossM += -dz
            }
        }
        prev = p
    }

    val avgSpeedMps = if (elapsedNanos > 0) distanceM / (elapsedNanos / 1e9) else 0.0

    return Analysis(
        distanceM = distanceM,
        elapsedNanos = elapsedNanos,
        movingNanos = movingNanos,
        elevationGainM = gainM,
        elevationLossM = lossM,
        maxSpeedMps = maxSpeedMps,
        avgSpeedMps = avgSpeedMps,
        splits = computeSplits(points),
    )
}
