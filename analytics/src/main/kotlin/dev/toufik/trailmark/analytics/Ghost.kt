package dev.toufik.trailmark.analytics

import dev.toufik.trailmark.core.geo.haversineMeters
import dev.toufik.trailmark.core.model.StoredPoint
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Longest gap between two stored fixes that still counts as racing time when the
 * clock has to be rebuilt. Stored timestamps include pause time while
 * `elapsedNanos` excludes it, so a pause looks like one long gap; the clamp keeps
 * a single gap from swallowing the whole race.
 */
private const val MAX_GAP_NANOS = 15_000_000_000L

/** How far the rebuilt clock may be stretched to reach the stored duration. */
private const val MIN_CLOCK_SCALE = 0.5
private const val MAX_CLOCK_SCALE = 3.0

/**
 * A previous effort as a distance-vs-time curve. Interpolates linearly between
 * stored points so the live delta and the ghost marker can be read at any
 * elapsed time.
 *
 * [fromIndex] lets the race begin in the middle of the previous effort: the
 * ghost clock and its distance both start at that point, which is what makes a
 * match useful when the new run starts a little before or after the old one.
 *
 * [totalElapsedNanos] is the effort's own recorded moving time. The per-point
 * clock is rebuilt from the stored stamps (which include pauses), then scaled so
 * the whole curve lasts exactly that long. Without it, every stop in the old
 * effort inflates the ghost by up to [MAX_GAP_NANOS], which makes the ghost
 * slower than the run it is supposed to replay. A zero or missing duration keeps
 * the raw stamps.
 */
class Ghost(
    points: List<StoredPoint>,
    fromIndex: Int = 0,
    totalElapsedNanos: Long = 0L,
) {
    private val pts: List<StoredPoint>
    private val times: LongArray
    private val distances: DoubleArray

    init {
        if (points.size < 2) {
            pts = points
            times = LongArray(points.size)
            distances = DoubleArray(points.size)
        } else {
            val start = fromIndex.coerceIn(0, points.size - 1)
            pts = points.subList(start, points.size)

            val raw = LongArray(points.size)
            for (i in 1 until points.size) {
                val rawGap = (points[i].timeNanos - points[i - 1].timeNanos).coerceAtLeast(0L)
                raw[i] = raw[i - 1] + rawGap.coerceAtMost(MAX_GAP_NANOS)
            }
            val scale = clockScale(raw.last(), totalElapsedNanos)
            val origin = raw[start]

            times = LongArray(pts.size) { i -> ((raw[start + i] - origin) * scale).toLong() }
            distances = DoubleArray(pts.size)
            for (i in 1 until pts.size) {
                distances[i] = distances[i - 1] + haversineMeters(
                    pts[i - 1].lat,
                    pts[i - 1].lon,
                    pts[i].lat,
                    pts[i].lon,
                )
            }
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

    /** True once the ghost has reached the end of the stretch it is racing. */
    fun isFinished(elapsedNanos: Long): Boolean = elapsedNanos >= durationNanos

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

/**
 * Factor that makes the rebuilt clock last [targetNanos]. A missing duration, or
 * a curve with no measurable time, keeps the raw stamps; an absurd ratio is
 * bounded so one corrupt row cannot turn a ghost into a rocket.
 */
private fun clockScale(rawTotalNanos: Long, targetNanos: Long): Double {
    if (rawTotalNanos <= 0L || targetNanos <= 0L) return 1.0
    return (targetNanos.toDouble() / rawTotalNanos).coerceIn(MIN_CLOCK_SCALE, MAX_CLOCK_SCALE)
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
