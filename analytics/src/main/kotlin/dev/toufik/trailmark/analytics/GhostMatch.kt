package dev.toufik.trailmark.analytics

import dev.toufik.trailmark.core.geo.ElevPoint
import dev.toufik.trailmark.core.geo.bearingDeg
import dev.toufik.trailmark.core.geo.haversineMeters
import dev.toufik.trailmark.core.geo.matchToRoute
import dev.toufik.trailmark.core.model.StoredPoint
import dev.toufik.trailmark.core.model.WaypointType
import kotlin.math.abs

/** A completed activity that can be raced as a ghost. */
data class GhostCandidate(
    val activityId: Long,
    val sport: String,
    val startWallMillis: Long,
    val distanceM: Double,
    val elapsedNanos: Long,
    val points: List<StoredPoint>,
)

/**
 * The best previous route for the current position, with the measurements used
 * to choose it. [fromIndex] is where the race joins the old effort and
 * [remainingM] is how much of the ghost still lies ahead.
 */
data class GhostMatch(
    val candidate: GhostCandidate,
    val startDistanceM: Double,
    val startOffsetM: Double,
    val fromIndex: Int,
    val remainingM: Double,
    val remainingNanos: Long,
    val headingDeltaDeg: Double?,
    val routeOverlapM: Double?,
)

private const val MIN_ROUTE_POINTS = 4
private const val MIN_ROUTE_M = 40.0
private const val MIN_GHOST_REMAINING_M = 100.0
private const val BEARING_STEP_M = 20.0

/**
 * Picks a previous activity whose route passes through the current position.
 *
 * The old rule only accepted a candidate that *started* within 120 m of the new
 * start, so beginning the recorder a few streets before or after the old start
 * point silently produced no ghost at all. Matching is now done against the
 * nearest point of the candidate polyline: the race begins there, the ghost
 * clock and distance both start from that point, and the heading is compared
 * with the direction the ghost travels at that spot.
 *
 * Once a few fixes exist, [routeOverlapM] rejects candidates that merely passed
 * nearby but took another road.
 */
fun matchGhost(
    sport: String,
    startLat: Double,
    startLon: Double,
    currentBearing: Double?,
    currentRoute: List<StoredPoint>,
    candidates: List<GhostCandidate>,
    maxStartM: Double = 250.0,
    maxHeadingDeg: Double = 55.0,
    maxRouteOverlapM: Double = 45.0,
    minRemainingM: Double = MIN_GHOST_REMAINING_M,
): GhostMatch? {
    val live = currentRoute.filter { it.type != WaypointType.REJECTED }
    val liveBearing = currentBearing ?: startBearing(live)
    val hasEnoughLiveRoute = live.size >= MIN_ROUTE_POINTS && routeLengthM(live) >= MIN_ROUTE_M

    data class Scored(val match: GhostMatch, val score: Double)

    return candidates.asSequence()
        .filter { it.sport == sport }
        .mapNotNull { candidate ->
            val points = candidate.points.filter { it.type != WaypointType.REJECTED }
            if (points.size < 2) return@mapNotNull null

            val fromIndex = nearestIndex(points, startLat, startLon)
            val startDistance = haversineMeters(
                startLat,
                startLon,
                points[fromIndex].lat,
                points[fromIndex].lon,
            )
            if (startDistance > maxStartM) return@mapNotNull null

            val offset = distanceUpTo(points, fromIndex)
            val ghost = Ghost(points, fromIndex)
            if (ghost.lengthM < minRemainingM) return@mapNotNull null

            val candidateBearing = bearingAt(points, fromIndex)
            val headingDelta = if (liveBearing != null && candidateBearing != null) {
                angleDelta(liveBearing, candidateBearing)
            } else {
                null
            }
            if (headingDelta != null && headingDelta > maxHeadingDeg) return@mapNotNull null

            val overlap = if (hasEnoughLiveRoute) routeOverlapM(live, points) else null
            if (overlap != null && overlap > maxRouteOverlapM) return@mapNotNull null

            val score = startDistance +
                (headingDelta ?: 0.0) * 0.8 +
                (overlap ?: 0.0) * 2.0
            Scored(
                GhostMatch(
                    candidate = candidate,
                    startDistanceM = startDistance,
                    startOffsetM = offset,
                    fromIndex = fromIndex,
                    remainingM = ghost.lengthM,
                    remainingNanos = ghost.durationNanos,
                    headingDeltaDeg = headingDelta,
                    routeOverlapM = overlap,
                ),
                score,
            )
        }
        .sortedWith(compareBy<Scored> { it.score }.thenByDescending { it.match.candidate.startWallMillis })
        .firstOrNull()
        ?.match
}

/** Median distance from live route samples to the candidate polyline, or null when too short. */
fun routeOverlapM(live: List<StoredPoint>, candidate: List<StoredPoint>): Double? {
    val route = live.filter { it.type != WaypointType.REJECTED }
    val ghost = candidate.filter { it.type != WaypointType.REJECTED }
    if (route.size < MIN_ROUTE_POINTS || ghost.size < 2) return null
    val elev = ghost.map { ElevPoint(it.lat, it.lon, it.altitudeM ?: 0.0) }
    val step = (route.size / 16).coerceAtLeast(1)
    val distances = route.filterIndexed { i, _ -> i % step == 0 || i == route.lastIndex }
        .mapNotNull { p -> matchToRoute(elev, p.lat, p.lon)?.offRouteM }
        .sorted()
    if (distances.isEmpty()) return null
    return distances[distances.size / 2]
}

/** Bearing over the first ~15 m of a route, ignoring a stationary start. */
private fun startBearing(points: List<StoredPoint>): Double? {
    val first = points.firstOrNull() ?: return null
    for (p in points.drop(1)) {
        if (haversineMeters(first.lat, first.lon, p.lat, p.lon) >= 15.0) {
            return bearingDeg(first.lat, first.lon, p.lat, p.lon)
        }
    }
    return null
}

/** Direction the candidate travels through [index], measured ~20 m ahead. */
private fun bearingAt(points: List<StoredPoint>, index: Int): Double? {
    val here = points.getOrNull(index) ?: return null
    for (i in index + 1 until points.size) {
        val p = points[i]
        if (haversineMeters(here.lat, here.lon, p.lat, p.lon) >= BEARING_STEP_M) {
            return bearingDeg(here.lat, here.lon, p.lat, p.lon)
        }
    }
    // Near the end of the old track, look backwards for the incoming heading.
    for (i in index - 1 downTo 0) {
        val p = points[i]
        if (haversineMeters(p.lat, p.lon, here.lat, here.lon) >= BEARING_STEP_M) {
            return bearingDeg(p.lat, p.lon, here.lat, here.lon)
        }
    }
    return null
}

private fun nearestIndex(points: List<StoredPoint>, lat: Double, lon: Double): Int {
    var best = 0
    var bestM = Double.MAX_VALUE
    points.forEachIndexed { i, p ->
        val d = haversineMeters(lat, lon, p.lat, p.lon)
        if (d < bestM) {
            bestM = d
            best = i
        }
    }
    return best
}

private fun distanceUpTo(points: List<StoredPoint>, index: Int): Double {
    var out = 0.0
    for (i in 1..index.coerceAtMost(points.lastIndex)) {
        out += haversineMeters(
            points[i - 1].lat,
            points[i - 1].lon,
            points[i].lat,
            points[i].lon,
        )
    }
    return out
}

private fun routeLengthM(points: List<StoredPoint>): Double {
    var out = 0.0
    for (i in 1 until points.size) {
        out += haversineMeters(
            points[i - 1].lat,
            points[i - 1].lon,
            points[i].lat,
            points[i].lon,
        )
    }
    return out
}

private fun angleDelta(a: Double, b: Double): Double {
    val d = abs((a - b + 540.0) % 360.0 - 180.0)
    return if (d > 180.0) 360.0 - d else d
}
