package dev.toufik.trailmark.analytics

import dev.toufik.trailmark.core.geo.haversineMeters
import dev.toufik.trailmark.core.model.StoredPoint
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Longest real gap between two stored fixes that still counts as racing time.
 * A pause, a lost fix or a crash gap is clamped, so the ghost neither freezes
 * for minutes nor teleports ahead of the live run. Stored timestamps include
 * pause time while `elapsedNanos` excludes it; the clamp keeps both clocks
 * close without needing the pause history.
 */
private const val MAX_GAP_NANOS = 15_000_000_000L

/**
 * A previous effort as a distance-vs-time curve. Interpolates linearly between
 * stored points so the live delta and the ghost marker can be read at any
 * elapsed time.
 */
class Ghost(points: List<StoredPoint>, startNanos: Long) {
    private val pts = points
    private val times = LongArray(points.size)
    private val distances = DoubleArray(points.size)

    init {
        var acc = 0.0
        points.forEachIndexed { i, p ->
            if (i > 0) {
                acc += haversineMeters(points[i - 1].lat, points[i - 1].lon, p.lat, p.lon)
                val rawGap = (p.timeNanos - points[i - 1].timeNanos).coerceAtLeast(0L)
                times[i] = times[i - 1] + rawGap.coerceAtMost(MAX_GAP_NANOS)
            }
            distances[i] = acc
        }
    }

    val lengthM: Double get() = if (distances.isEmpty()) 0.0 else distances.last()

    val durationNanos: Long get() = if (times.isEmpty()) 0L else times.last()

    fun distanceAt(elapsedNanos: Long): Double {
        val (lo, hi, f) = segmentAt(elapsedNanos) ?: return if (distances.isEmpty()) 0.0 else distances.last()
        return distances[lo] + (distances[hi] - distances[lo]) * f
    }

    /**
     * The ghost's interpolated position at [elapsedNanos], with the bearing of
     * the segment it is on. Null while the run has not started.
     */
    fun positionAt(elapsedNanos: Long): StoredPoint? {
        if (pts.isEmpty()) return null
        val (lo, hi, f) = segmentAt(elapsedNanos) ?: return if (elapsedNanos <= 0L) pts.first() else pts.last()
        val a = pts[lo]
        val b = pts[hi]
        if (a === b || f <= 0.0) return a
        val lat = a.lat + (b.lat - a.lat) * f
        val lon = a.lon + (b.lon - a.lon) * f
        val bearing = if (haversineMeters(a.lat, a.lon, b.lat, b.lon) >= 0.5) {
            legBearing(a.lat, a.lon, b.lat, b.lon)
        } else {
            b.bearingDeg ?: a.bearingDeg
        }
        return a.copy(lat = lat, lon = lon, bearingDeg = bearing)
    }

    /** Index pair and fraction, or null when outside the timed range. */
    private fun segmentAt(elapsedNanos: Long): Triple<Int, Int, Double>? {
        if (times.size < 2) return null
        if (elapsedNanos <= times.first()) return Triple(0, 0, 0.0)
        if (elapsedNanos >= times.last()) return Triple(times.size - 1, times.size - 1, 0.0)
        var lo = 0
        var hi = times.size - 1
        while (lo < hi - 1) {
            val mid = (lo + hi) / 2
            if (times[mid] <= elapsedNanos) lo = mid else hi = mid
        }
        val t0 = times[lo]
        val t1 = times[hi]
        val f = if (t1 > t0) (elapsedNanos - t0).toDouble() / (t1 - t0) else 0.0
        return Triple(lo, hi, f)
    }
}

private fun legBearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val p1 = Math.toRadians(lat1)
    val p2 = Math.toRadians(lat2)
    val dl = Math.toRadians(lon2 - lon1)
    val y = sin(dl) * cos(p2)
    val x = cos(p1) * sin(p2) - sin(p1) * cos(p2) * cos(dl)
    return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
}

/** current minus ghost: positive means the live effort is AHEAD of the ghost. */
fun ghostDeltaM(ghost: Ghost, currentDistanceM: Double, elapsedNanos: Long): Double =
    currentDistanceM - ghost.distanceAt(elapsedNanos)
