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
    /** True when the platform flagged the fixes as mocked; never used as a rival. */
    val mockDetected: Boolean = false,
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
    /** The rival itself, ready to race from [fromIndex] with its own clock. */
    val ghost: Ghost,
    /** Lower is better. Exposed so a rejected pick can be skipped for the next one. */
    val score: Double = 0.0,
) {
    /** The part of the old effort still to be raced, for drawing. */
    val ghostRoute: List<StoredPoint> = candidate.points
        .filter { it.type != WaypointType.REJECTED }
        .let { points ->
            // coerceIn(0, -1) throws on an empty list, and a match can be built
            // from a candidate whose points were dropped after the fact.
            if (points.isEmpty()) emptyList()
            else points.subList(fromIndex.coerceIn(0, points.size - 1), points.size)
        }
}

private const val MIN_ROUTE_POINTS = 4
private const val MIN_ROUTE_M = 40.0
private const val MIN_GHOST_REMAINING_M = 100.0
private const val BEARING_STEP_M = 20.0

/** Screens out implausibly short efforts that make a pointless rival. */
const val MIN_GHOST_DURATION_NANOS = 60_000_000_000L
const val MIN_GHOST_DISTANCE_M = 300.0
const val MAX_GHOST_AGE_MILLIS = 180L * 24 * 60 * 60 * 1000

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
 * On an out-and-back or a self-crossing route the same ground is covered twice,
 * so "nearest point" alone can join the return leg and race the runner
 * backwards. Every point within reach is therefore scored by distance *and* by
 * how well its direction agrees with the live heading, and the point that keeps
 * enough route ahead wins.
 *
 * Once a few fixes exist, [routeOverlapM] rejects candidates that merely passed
 * nearby but took another road. [rankGhosts] exposes the whole ranking so the
 * caller can fall back to the next rival instead of giving up.
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
    minDurationNanos: Long = MIN_GHOST_DURATION_NANOS,
    minDistanceM: Double = MIN_GHOST_DISTANCE_M,
    maxAgeMillis: Long = MAX_GHOST_AGE_MILLIS,
    nowWallMillis: Long = 0L,
): GhostMatch? = rankGhosts(
    sport = sport,
    startLat = startLat,
    startLon = startLon,
    currentBearing = currentBearing,
    currentRoute = currentRoute,
    candidates = candidates,
    maxStartM = maxStartM,
    maxHeadingDeg = maxHeadingDeg,
    maxRouteOverlapM = maxRouteOverlapM,
    minRemainingM = minRemainingM,
    minDurationNanos = minDurationNanos,
    minDistanceM = minDistanceM,
    maxAgeMillis = maxAgeMillis,
    nowWallMillis = nowWallMillis,
).firstOrNull()

/**
 * Every candidate that could be raced, best first. The caller keeps this list
 * so a rival that later turns out to share no ground can be replaced without
 * another database pass.
 */
fun rankGhosts(
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
    minDurationNanos: Long = MIN_GHOST_DURATION_NANOS,
    minDistanceM: Double = MIN_GHOST_DISTANCE_M,
    maxAgeMillis: Long = MAX_GHOST_AGE_MILLIS,
    nowWallMillis: Long = 0L,
): List<GhostMatch> {
    val live = currentRoute.filter { it.type != WaypointType.REJECTED }
    val liveBearing = currentBearing ?: startBearing(live)
    val hasEnoughLiveRoute = live.size >= MIN_ROUTE_POINTS && routeLengthM(live) >= MIN_ROUTE_M

    return candidates.asSequence()
        .filter { it.sport == sport }
        .filter { !it.mockDetected }
        .filter { it.elapsedNanos >= minDurationNanos }
        .filter { it.distanceM >= minDistanceM }
        .filter { maxAgeMillis <= 0L || nowWallMillis <= 0L || nowWallMillis - it.startWallMillis <= maxAgeMillis }
        .mapNotNull { candidate ->
            val points = candidate.points.filter { it.type != WaypointType.REJECTED }
            if (points.size < 2) return@mapNotNull null

            val prefix = prefixDistances(points)
            val total = prefix.last()
            val join = chooseJoinIndex(
                points = points,
                prefix = prefix,
                lat = startLat,
                lon = startLon,
                bearing = liveBearing,
                maxStartM = maxStartM,
                maxHeadingDeg = maxHeadingDeg,
                minRemainingM = minRemainingM,
                totalM = total,
            ) ?: return@mapNotNull null

            val fromIndex = join.index
            val ghost = Ghost(points, fromIndex, candidate.elapsedNanos)
            if (ghost.lengthM < minRemainingM) return@mapNotNull null

            val overlap = if (hasEnoughLiveRoute) routeOverlapM(live, points) else null
            if (overlap != null && overlap > maxRouteOverlapM) return@mapNotNull null

            val score = join.startDistanceM +
                (join.headingDeltaDeg ?: 0.0) * 0.8 +
                (overlap ?: 0.0) * 2.0

            GhostMatch(
                candidate = candidate,
                startDistanceM = join.startDistanceM,
                startOffsetM = prefix[fromIndex],
                fromIndex = fromIndex,
                remainingM = ghost.lengthM,
                remainingNanos = ghost.durationNanos,
                headingDeltaDeg = join.headingDeltaDeg,
                routeOverlapM = overlap,
                ghost = ghost,
                score = score,
            )
        }
        // Best match first; on a tie the most recent effort wins, because it is
        // the one the runner is most likely trying to beat.
        .sortedWith(
            compareBy<GhostMatch> { it.score }
                .thenByDescending { it.candidate.startWallMillis },
        )
        .toList()
}

private class Join(
    val index: Int,
    val startDistanceM: Double,
    val headingDeltaDeg: Double?,
)

/**
 * Where the race joins the old track. Points beyond [maxStartM] are ignored; the
 * rest are scored by distance to the live position plus how far their direction
 * disagrees with [bearing]. A wrong leg of an out-and-back is the same ground
 * but the opposite heading, so the heading term keeps it from winning.
 *
 * The join is the point nearest *and* most aligned with the runner — it is never
 * slid along the route to manufacture a longer race, because that would start
 * the ghost behind a runner who never covered the ground in between.
 */
private fun chooseJoinIndex(
    points: List<StoredPoint>,
    prefix: DoubleArray,
    lat: Double,
    lon: Double,
    bearing: Double?,
    maxStartM: Double,
    maxHeadingDeg: Double,
    minRemainingM: Double,
    totalM: Double,
): Join? {
    var best: Join? = null
    var bestScore = Double.MAX_VALUE
    for (i in points.indices) {
        val p = points[i]
        val d = haversineMeters(lat, lon, p.lat, p.lon)
        if (d > maxStartM) continue
        val delta = if (bearing != null) {
            bearingAt(points, i)?.let { angleDelta(bearing, it) }
        } else {
            null
        }
        if (delta != null && delta > maxHeadingDeg) continue
        val score = d + (delta ?: 0.0) * 0.8
        if (score < bestScore) {
            bestScore = score
            best = Join(i, d, delta)
        }
    }
    val join = best ?: return null
    // Standing at the end of the old route leaves nothing to race.
    if (totalM - prefix[join.index] < minRemainingM) return null
    return join
}

/** Cumulative distance from the first point to each index. */
private fun prefixDistances(points: List<StoredPoint>): DoubleArray {
    val out = DoubleArray(points.size)
    for (i in 1 until points.size) {
        out[i] = out[i - 1] + haversineMeters(
            points[i - 1].lat,
            points[i - 1].lon,
            points[i].lat,
            points[i].lon,
        )
    }
    return out
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
