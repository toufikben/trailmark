package dev.toufik.trailmark.core.model

/** Waypoint classification. SKIPPED is a sink verdict, never persisted. */
enum class WaypointType { NORMAL, IDLE, REJECTED, SKIPPED }

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
