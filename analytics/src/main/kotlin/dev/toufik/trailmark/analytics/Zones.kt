package dev.toufik.trailmark.analytics

import dev.toufik.trailmark.core.geo.haversineMeters
import dev.toufik.trailmark.core.model.StoredPoint
import dev.toufik.trailmark.core.model.WaypointType

/**
 * Speed zones for one sport, as ascending upper bounds in m/s; the last zone is
 * open-ended, so there are `upperMps.size + 1` zones. Runners read them as pace.
 */
class ZoneScale(val upperMps: DoubleArray) {
    val count: Int get() = upperMps.size + 1

    fun zoneOf(speedMps: Double): Int {
        for (i in upperMps.indices) if (speedMps < upperMps[i]) return i
        return upperMps.size
    }

    companion object {
        private fun kmh(vararg v: Double) = DoubleArray(v.size) { v[it] / 3.6 }

        /** Pace zones for running: slower than 7:00, 6:00, 5:00, 4:30 min/km, then faster. */
        val RUN = ZoneScale(DoubleArray(4) { 1000.0 / doubleArrayOf(420.0, 360.0, 300.0, 270.0)[it] })
        val WALK = ZoneScale(kmh(3.0, 4.5, 5.5, 6.5))
        val HIKE = ZoneScale(kmh(2.0, 3.0, 4.0, 5.0))
        val BIKE = ZoneScale(kmh(15.0, 20.0, 25.0, 30.0))

        fun forSport(sport: String): ZoneScale = when (sport) {
            "BIKE" -> BIKE
            "WALK" -> WALK
            "HIKE" -> HIKE
            else -> RUN
        }
    }
}

/** Gaps longer than this are pauses or signal loss, not time spent in a zone. */
private const val MAX_STEP_NANOS = 30_000_000_000L

/**
 * Moving time spent in each zone, in nanoseconds. Each step between two kept
 * points counts once, at the speed it was covered; standing still (IDLE),
 * rejected spikes and long gaps are left out.
 */
fun zoneTimes(points: List<StoredPoint>, scale: ZoneScale): LongArray {
    val out = LongArray(scale.count)
    var prev: StoredPoint? = null
    for (p in points) {
        if (p.type == WaypointType.REJECTED) continue
        val a = prev
        prev = p
        if (a == null || p.type == WaypointType.IDLE || a.type == WaypointType.IDLE) continue
        // A pause gap is not a step: the two ends are not one continuous effort.
        if (p.type == WaypointType.RESUME) continue
        val dt = p.timeNanos - a.timeNanos
        if (dt <= 0L || dt > MAX_STEP_NANOS) continue
        val speed = haversineMeters(a.lat, a.lon, p.lat, p.lon) / (dt / 1e9)
        out[scale.zoneOf(speed)] += dt
    }
    return out
}

/** Totals of one calendar period, for side-by-side comparison. */
data class PeriodTotals(val count: Int, val distanceM: Double, val movingNanos: Long)

/** Relative change from [previous] to [current]; null when there is nothing to compare with. */
fun percentChange(current: Double, previous: Double): Int? =
    if (previous <= 0.0) null else Math.round((current - previous) / previous * 100.0).toInt()
