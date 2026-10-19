package dev.toufik.trailmark.analytics

import dev.toufik.trailmark.core.geo.haversineMeters
import dev.toufik.trailmark.core.model.StoredPoint
import dev.toufik.trailmark.core.model.WaypointType

/** Fastest personal records found across a set of recorded routes. */
data class PersonalRecords(
    val best1kSec: Double? = null,
    val best5kSec: Double? = null,
    val best10kSec: Double? = null,
) {
    val isEmpty: Boolean get() = best1kSec == null && best5kSec == null && best10kSec == null
}

/**
 * Fastest elapsed time over a [targetM] stretch anywhere along one route,
 * using a two-pointer scan over cumulative distance. Null when the route never
 * reaches the distance. Rejected points are evidence, never path.
 */
fun fastestSplitSeconds(points: List<StoredPoint>, targetM: Double): Double? {
    val route = points.filter { it.type != WaypointType.REJECTED }
    if (route.size < 2) return null

    val cum = DoubleArray(route.size)
    for (i in 1 until route.size) {
        cum[i] = cum[i - 1] +
            haversineMeters(route[i - 1].lat, route[i - 1].lon, route[i].lat, route[i].lon)
    }
    if (cum.last() < targetM) return null

    var best: Double? = null
    var left = 0
    for (right in 1 until route.size) {
        while (left < right && cum[right] - cum[left] >= targetM) {
            val dt = (route[right].timeNanos - route[left].timeNanos) / 1e9
            if (dt > 0 && (best == null || dt < best)) best = dt
            left++
        }
    }
    return best
}

/** Best 1 km / 5 km / 10 km stretches across every route given. */
fun personalRecords(routes: List<List<StoredPoint>>): PersonalRecords {
    var best1k: Double? = null
    var best5k: Double? = null
    var best10k: Double? = null
    for (route in routes) {
        fastestSplitSeconds(route, 1000.0)?.let { if (best1k == null || it < best1k) best1k = it }
        fastestSplitSeconds(route, 5000.0)?.let { if (best5k == null || it < best5k) best5k = it }
        fastestSplitSeconds(route, 10000.0)?.let { if (best10k == null || it < best10k) best10k = it }
    }
    return PersonalRecords(best1k, best5k, best10k)
}
