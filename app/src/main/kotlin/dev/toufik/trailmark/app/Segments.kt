package dev.toufik.trailmark.app

import dev.toufik.trailmark.analytics.Segment
import dev.toufik.trailmark.analytics.matchEffort
import dev.toufik.trailmark.core.model.StoredPoint
import dev.toufik.trailmark.data.ActivityRepository

/**
 * Matches every saved segment against one activity's points, storing the
 * elapsed time of each first passage. Skips pairs that already have an effort,
 * so it is safe to call repeatedly (recording stop, detail open, backfill).
 */
internal fun matchSegments(
    repo: ActivityRepository,
    activityId: Long,
    points: List<StoredPoint>,
) {
    val segments = runCatching { repo.listSegments() }.getOrNull() ?: return
    if (segments.isEmpty()) return
    for (s in segments) {
        runCatching {
            if (repo.effortExists(s.id, activityId)) return@runCatching
            val dt = matchEffort(
                Segment(s.id, s.name, s.startLat, s.startLon, s.endLat, s.endLon),
                points,
            )
            if (dt != null) repo.insertEffort(s.id, activityId, dt)
        }
    }
}

/** Replays one newly created segment across the whole local history, oldest last. */
internal fun backfillSegment(repo: ActivityRepository, segmentId: Long) {
    val s = runCatching { repo.listSegments().firstOrNull { it.id == segmentId } }.getOrNull()
        ?: return
    val segment = Segment(s.id, s.name, s.startLat, s.startLon, s.endLat, s.endLon)
    val activities = runCatching { repo.listActivities() }.getOrNull() ?: return
    for (row in activities.take(200)) {
        if (row.isOpen) continue
        runCatching {
            if (repo.effortExists(segmentId, row.id)) return@runCatching
            val pts = repo.loadPoints(row.id)
            matchEffort(segment, pts)?.let { repo.insertEffort(segmentId, row.id, it) }
        }
    }
}
