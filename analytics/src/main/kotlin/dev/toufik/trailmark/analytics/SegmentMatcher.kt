package dev.toufik.trailmark.analytics

import dev.toufik.trailmark.core.geo.haversineMeters
import dev.toufik.trailmark.core.model.StoredPoint
import dev.toufik.trailmark.core.model.WaypointType

/** A user-defined local segment (like a Strava segment, but entirely on-device). */
data class Segment(
    val id: Long,
    val name: String,
    val startLat: Double,
    val startLon: Double,
    val endLat: Double,
    val endLon: Double,
)

/** How close a track point must pass to count as entering/leaving a segment. */
const val SEGMENT_MATCH_RADIUS_M = 30.0

/**
 * The elapsed time of the first passage through [segment], or null if the track
 * never entered the start and later reached the end. Rejected points are not path.
 */
fun matchEffort(segment: Segment, track: List<StoredPoint>): Long? {
    var entry: Long? = null
    for (p in track) {
        if (p.type == WaypointType.REJECTED) continue
        if (entry == null) {
            if (haversineMeters(p.lat, p.lon, segment.startLat, segment.startLon) <= SEGMENT_MATCH_RADIUS_M) {
                entry = p.timeNanos
            }
        } else {
            if (haversineMeters(p.lat, p.lon, segment.endLat, segment.endLon) <= SEGMENT_MATCH_RADIUS_M) {
                return p.timeNanos - entry
            }
        }
    }
    return null
}
