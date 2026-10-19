package dev.toufik.trailmark.core.geo

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sqrt

/** Direction of a planned-route manoeuvre. */
enum class TurnKind {
    LEFT,
    RIGHT,
    SLIGHT_LEFT,
    SLIGHT_RIGHT,
    SHARP_LEFT,
    SHARP_RIGHT,
    UTURN,
}

/** A manoeuvre on a planned polyline, [distanceAlongM] from the route start. */
data class TurnCue(
    val lat: Double,
    val lon: Double,
    val distanceAlongM: Double,
    val kind: TurnKind,
)

/** Closest point on a planned route: distance travelled and lateral offset. */
data class RouteMatch(val distanceAlongM: Double, val offRouteM: Double)

/**
 * Flattens [RouteChunk]s back into one polyline, dropping the duplicate vertex
 * at every chunk boundary. Chunks are stored for gradient colouring and share
 * their first/last point with the neighbour.
 */
fun flattenChunks(chunks: List<RouteChunk>): List<ElevPoint> {
    val out = ArrayList<ElevPoint>()
    for (chunk in chunks) {
        for (p in chunk.points) {
            val last = out.lastOrNull()
            if (last != null && last.lat == p.lat && last.lon == p.lon) continue
            out.add(p)
        }
    }
    return out
}

/**
 * Finds the turns on a planned route. Bearings are measured over a
 * [probeM]-long window on both sides of each vertex so dense routing points
 * do not turn GPS-sized wiggles into manoeuvres. Nearby detections are merged,
 * keeping the sharpest angle.
 */
fun turnCues(
    points: List<ElevPoint>,
    probeM: Double = 30.0,
    minTurnDeg: Double = 35.0,
    minGapM: Double = 45.0,
): List<TurnCue> {
    if (points.size < 3) return emptyList()
    val cum = DoubleArray(points.size)
    for (i in 1 until points.size) {
        cum[i] = cum[i - 1] + haversineMeters(
            points[i - 1].lat,
            points[i - 1].lon,
            points[i].lat,
            points[i].lon,
        )
    }
    val total = cum.last()
    if (total <= probeM * 2) return emptyList()

    val out = ArrayList<TurnCue>()
    var lastDelta = 0.0
    for (i in 1 until points.size - 1) {
        val along = cum[i]
        if (along < 30.0 || along > total - 15.0) continue
        val before = pointAtDistance(points, cum, along - probeM)
        val after = pointAtDistance(points, cum, along + probeM)
        val vertex = points[i]
        val delta = turnDelta(
            bearingDeg(before.lat, before.lon, vertex.lat, vertex.lon),
            bearingDeg(vertex.lat, vertex.lon, after.lat, after.lon),
        )
        if (abs(delta) < minTurnDeg) continue
        if (out.isNotEmpty() && along - out.last().distanceAlongM < minGapM) {
            if (abs(delta) > abs(lastDelta)) {
                out[out.size - 1] = TurnCue(vertex.lat, vertex.lon, along, kindFor(delta))
                lastDelta = delta
            }
        } else {
            out.add(TurnCue(vertex.lat, vertex.lon, along, kindFor(delta)))
            lastDelta = delta
        }
    }
    return out
}

/**
 * Projects a position onto the polyline and returns how far along the route it
 * is plus the perpendicular distance. Uses an equirectangular projection
 * centred on the query point — accurate well below GPS noise at city scale.
 */
fun matchToRoute(points: List<ElevPoint>, lat: Double, lon: Double): RouteMatch? {
    if (points.size < 2) return null
    val mx = 111_320.0 * cos(lat * PI / 180.0)
    val my = 110_540.0
    var along = 0.0
    var bestD = Double.MAX_VALUE
    var bestAlong = 0.0
    for (i in 1 until points.size) {
        val a = points[i - 1]
        val b = points[i]
        val ax = (a.lon - lon) * mx
        val ay = (a.lat - lat) * my
        val bx = (b.lon - lon) * mx
        val by = (b.lat - lat) * my
        val dx = bx - ax
        val dy = by - ay
        val len2 = dx * dx + dy * dy
        val t = if (len2 <= 0.0) 0.0 else (-(ax * dx + ay * dy) / len2).coerceIn(0.0, 1.0)
        val px = ax + dx * t
        val py = ay + dy * t
        val d = sqrt(px * px + py * py)
        val segLen = haversineMeters(a.lat, a.lon, b.lat, b.lon)
        if (d < bestD) {
            bestD = d
            bestAlong = along + segLen * t
        }
        along += segLen
    }
    return RouteMatch(bestAlong, bestD)
}

private fun pointAtDistance(points: List<ElevPoint>, cum: DoubleArray, d: Double): ElevPoint {
    if (d <= 0.0) return points.first()
    if (d >= cum.last()) return points.last()
    var lo = 0
    var hi = cum.size - 1
    while (lo + 1 < hi) {
        val mid = (lo + hi) / 2
        if (cum[mid] <= d) lo = mid else hi = mid
    }
    val segLen = cum[hi] - cum[lo]
    if (segLen <= 0.0) return points[lo]
    val t = (d - cum[lo]) / segLen
    val a = points[lo]
    val b = points[hi]
    return ElevPoint(
        lat = a.lat + (b.lat - a.lat) * t,
        lon = a.lon + (b.lon - a.lon) * t,
        eleM = a.eleM + (b.eleM - a.eleM) * t,
    )
}

private fun turnDelta(fromDeg: Double, toDeg: Double): Double {
    var d = toDeg - fromDeg
    while (d > 180.0) d -= 360.0
    while (d <= -180.0) d += 360.0
    return d
}

private fun kindFor(delta: Double): TurnKind {
    val right = delta > 0.0
    val angle = abs(delta)
    return when {
        angle >= 160.0 -> TurnKind.UTURN
        angle >= 135.0 -> if (right) TurnKind.SHARP_RIGHT else TurnKind.SHARP_LEFT
        angle >= 45.0 -> if (right) TurnKind.RIGHT else TurnKind.LEFT
        else -> if (right) TurnKind.SLIGHT_RIGHT else TurnKind.SLIGHT_LEFT
    }
}
