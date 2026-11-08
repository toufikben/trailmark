package dev.toufik.trailmark.analytics

import dev.toufik.trailmark.core.geo.bearingDeg
import dev.toufik.trailmark.core.geo.haversineMeters
import dev.toufik.trailmark.core.model.StoredPoint
import dev.toufik.trailmark.core.model.WaypointType
import kotlin.math.abs

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

    /**
     * Replay time of something captured along the route. Media carry the moving
     * time (pauses excluded) while replay runs on the recorded clock, so they are
     * pinned by place instead: the nearest kept point, and when the route passes
     * the same spot more than once, the pass whose distance is closest to
     * [distanceHintM]. Without a usable position, [distanceHintM] alone decides.
     */
    fun nanosNear(lat: Double?, lon: Double?, distanceHintM: Double): Long {
        if (times.isEmpty()) return 0L
        if (lat == null || lon == null || !lat.isFinite() || !lon.isFinite()) {
            return nanosAtDistance(distanceHintM)
        }
        val gaps = DoubleArray(times.size) { haversineMeters(lats[it], lons[it], lat, lon) }
        val nearest = gaps.min()
        var best = 0
        var bestHintGap = Double.MAX_VALUE
        for (i in gaps.indices) {
            if (gaps[i] > nearest + SAME_SPOT_M) continue
            val hintGap = abs(distances[i] - distanceHintM)
            if (hintGap < bestHintGap) {
                bestHintGap = hintGap
                best = i
            }
        }
        return times[best]
    }

    /** Replay time at which the cursor has covered [distanceM], clamped to the track. */
    fun nanosAtDistance(distanceM: Double): Long {
        if (times.isEmpty()) return 0L
        if (distanceM <= 0.0) return 0L
        if (distanceM >= distances.last()) return totalNanos
        var lo = 0
        var hi = distances.size - 1
        while (lo < hi - 1) {
            val mid = (lo + hi) / 2
            if (distances[mid] <= distanceM) lo = mid else hi = mid
        }
        val d0 = distances[lo]
        val d1 = distances[hi]
        val f = if (d1 > d0) (distanceM - d0) / (d1 - d0) else 0.0
        return times[lo] + ((times[hi] - times[lo]) * f).toLong()
    }

    private companion object {
        /** Points this close to the nearest one count as the same spot (GPS scatter). */
        const val SAME_SPOT_M = 30.0
    }
}
