package dev.toufik.trailmark.engine

import dev.toufik.trailmark.core.geo.haversineMeters
import dev.toufik.trailmark.core.model.StoredPoint
import dev.toufik.trailmark.core.model.WaypointType
import kotlin.math.roundToLong

/** One completed kilometre of the travelled path. */
data class Split(
    val kmIndex: Int,
    val distanceM: Double,
    val elapsedNanos: Long,
    val paceSecPerKm: Double,
)

/**
 * Interpolates the exact position/time at each 1000 m crossing along the stored
 * polyline. Rejected points are raw archive entries, not path, and are skipped.
 * Never extrapolates beyond the last point.
 */
fun computeSplits(points: List<StoredPoint>): List<Split> {
    val out = ArrayList<Split>()
    var travelled = 0.0
    var km = 1
    var prev: StoredPoint? = null
    val startNanos = if (points.isNotEmpty()) points.first().timeNanos else 0L

    for (p in points) {
        if (p.type == WaypointType.REJECTED) continue
        val a = prev
        if (a != null) {
            val seg = haversineMeters(a.lat, a.lon, p.lat, p.lon)
            val segTime = p.timeNanos - a.timeNanos
            var remaining = seg
            val segStartTravelled = travelled
            while (travelled + remaining >= km * 1000.0) {
                val need = km * 1000.0 - travelled
                val frac = if (seg == 0.0) 0.0 else (km * 1000.0 - segStartTravelled) / seg
                val nanosAt = a.timeNanos + (segTime * frac).roundToLong()
                val prevNanosAbs =
                    if (out.isEmpty()) startNanos else startNanos + out.last().elapsedNanos
                out.add(
                    Split(
                        kmIndex = km,
                        distanceM = km * 1000.0,
                        elapsedNanos = nanosAt - startNanos,
                        paceSecPerKm = (nanosAt - prevNanosAbs) / 1e9,
                    ),
                )
                travelled += need
                remaining -= need
                km++
            }
            travelled += remaining
        }
        prev = p
    }
    return out
}
