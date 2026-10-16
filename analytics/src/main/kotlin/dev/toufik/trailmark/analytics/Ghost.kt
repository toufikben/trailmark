package dev.toufik.trailmark.analytics

import dev.toufik.trailmark.core.geo.haversineMeters
import dev.toufik.trailmark.core.model.StoredPoint

/**
 * A previous effort as a distance-vs-time curve. Interpolates linearly between
 * stored points so the live delta can be read at any elapsed time.
 */
class Ghost(points: List<StoredPoint>, startNanos: Long) {
    private val times = LongArray(points.size)
    private val distances = DoubleArray(points.size)

    init {
        var acc = 0.0
        points.forEachIndexed { i, p ->
            if (i > 0) acc += haversineMeters(points[i - 1].lat, points[i - 1].lon, p.lat, p.lon)
            times[i] = p.timeNanos - startNanos
            distances[i] = acc
        }
    }

    val lengthM: Double get() = if (distances.isEmpty()) 0.0 else distances.last()

    fun distanceAt(elapsedNanos: Long): Double {
        if (times.isEmpty()) return 0.0
        if (elapsedNanos <= times.first()) return distances.first()
        if (elapsedNanos >= times.last()) return distances.last()
        var lo = 0
        var hi = times.size - 1
        while (lo < hi - 1) {
            val mid = (lo + hi) / 2
            if (times[mid] <= elapsedNanos) lo = mid else hi = mid
        }
        val t0 = times[lo]
        val t1 = times[hi]
        val f = if (t1 > t0) (elapsedNanos - t0).toDouble() / (t1 - t0) else 0.0
        return distances[lo] + (distances[hi] - distances[lo]) * f
    }
}

/** current minus ghost: positive means the live effort is AHEAD of the ghost. */
fun ghostDeltaM(ghost: Ghost, currentDistanceM: Double, elapsedNanos: Long): Double =
    currentDistanceM - ghost.distanceAt(elapsedNanos)
