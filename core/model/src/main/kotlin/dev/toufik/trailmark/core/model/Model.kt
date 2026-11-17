package dev.toufik.trailmark.core.model

/**
 * Waypoint classification. SKIPPED is a sink verdict, never persisted. RESUME
 * marks the first point of a segment that follows a pause, so the leg across
 * the pause — which the runner never travelled — is never billed as distance
 * or moving time.
 */
enum class WaypointType { NORMAL, IDLE, REJECTED, SKIPPED, RESUME }

/** One GPS fix as delivered by the platform, in monotonic time. */
data class RawFix(
    val lat: Double,
    val lon: Double,
    val timeNanos: Long,
    val accuracyM: Double,
    val altitudeM: Double? = null,
    val speedMps: Double? = null,
    val bearingDeg: Double? = null,
    val isMocked: Boolean = false,
    val wallMillis: Long = 0L,
)

/** A point the engine decided to keep (normal, idle marker, or rejected-but-preserved). */
data class StoredPoint(
    val lat: Double,
    val lon: Double,
    val timeNanos: Long,
    val accuracyM: Double,
    val type: WaypointType,
    val altitudeM: Double? = null,
    val speedMps: Double? = null,
    val bearingDeg: Double? = null,
)

/** The points a display surface may DRAW: rejected spikes are never part of the path. */
fun displayablePoints(points: List<StoredPoint>): List<StoredPoint> =
    points.filter { it.type != WaypointType.REJECTED }

/**
 * Splits a stored track into the segments that were actually travelled. A
 * RESUME point opens a new segment, because the leg from the previous segment
 * ends somewhere the runner never walked to — pausing the run and driving
 * 5 km before resuming used to add those kilometres to the splits.
 *
 * Rejected points are dropped; an empty track yields no segments.
 */
fun routeSegments(points: List<StoredPoint>): List<List<StoredPoint>> {
    val out = ArrayList<List<StoredPoint>>()
    var current = ArrayList<StoredPoint>()
    for (p in points) {
        if (p.type == WaypointType.REJECTED) continue
        if (p.type == WaypointType.RESUME && current.isNotEmpty()) {
            out.add(current)
            current = ArrayList()
        }
        current.add(p)
    }
    if (current.isNotEmpty()) out.add(current)
    return out
}
