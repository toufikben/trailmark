package dev.toufik.trailmark.analytics

import dev.toufik.trailmark.core.geo.bearingDeg
import dev.toufik.trailmark.core.geo.haversineMeters
import dev.toufik.trailmark.core.model.StoredPoint
import dev.toufik.trailmark.core.model.WaypointType

/** A cursor position sampled from a replay timeline. */
class ReplaySample(
    val lat: Double,
    val lon: Double,
    val bearingDeg: Double,
    val distanceM: Double,
    val nanos: Long,
)

/**
 * Time-based cursor over a recorded track, for replay/scrub playback. Points
 * keep their recorded order and rejected spikes are excluded. Sampling clamps
 * to both ends and interpolates linearly inside the bracketing segment. A
 * non-monotonic timestamp (clock jump) keeps the previous time, so the binary
 * search stays well-ordered.
 */
class ReplayTrack(points: List<StoredPoint>) {
    private val lats: DoubleArray
    private val lons: DoubleArray
    private val times: LongArray
    private val distances: DoubleArray

    val totalNanos: Long
    val totalDistanceM: Double get() = if (distances.isEmpty()) 0.0 else distances.last()

    init {
        val kept = points.filter { it.type != WaypointType.REJECTED }
        lats = DoubleArray(kept.size)
        lons = DoubleArray(kept.size)
        times = LongArray(kept.size)
        distances = DoubleArray(kept.size)
        val t0 = kept.firstOrNull()?.timeNanos ?: 0L
        var acc = 0.0
        var prevT = 0L
        kept.forEachIndexed { i, p ->
            if (i > 0) acc += haversineMeters(kept[i - 1].lat, kept[i - 1].lon, p.lat, p.lon)
            lats[i] = p.lat
            lons[i] = p.lon
            times[i] = if (i == 0) 0L else maxOf(prevT, p.timeNanos - t0)
            prevT = times[i]
            distances[i] = acc
        }
        totalNanos = times.lastOrNull() ?: 0L
    }

    val size: Int get() = times.size

    /** Sample at [nanos] since the first kept point; null when nothing to show. */
    fun sampleAt(nanos: Long): ReplaySample? {
        if (times.isEmpty()) return null
        if (times.size == 1) return ReplaySample(lats[0], lons[0], 0.0, 0.0, 0L)
        val t = nanos.coerceIn(0L, totalNanos)
        var lo = 0
        var hi = times.size - 1
        while (lo < hi - 1) {
            val mid = (lo + hi) / 2
            if (times[mid] <= t) lo = mid else hi = mid
        }
        val t0 = times[lo]
        val t1 = times[hi]
        val f = if (t1 > t0) (t - t0).toDouble() / (t1 - t0) else 0.0
        return ReplaySample(
            lat = lats[lo] + (lats[hi] - lats[lo]) * f,
            lon = lons[lo] + (lons[hi] - lons[lo]) * f,
            bearingDeg = bearingDeg(lats[lo], lons[lo], lats[hi], lons[hi]),
            distanceM = distances[lo] + (distances[hi] - distances[lo]) * f,
            nanos = t,
        )
    }
}
