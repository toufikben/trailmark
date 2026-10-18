package dev.toufik.trailmark.data

import dev.toufik.trailmark.core.model.StoredPoint
import dev.toufik.trailmark.core.model.WaypointType
import dev.toufik.trailmark.data.db.TrailmarkDb
import dev.toufik.trailmark.engine.UNMEASURED_ACCURACY_M

/** R86: durable "has this install been onboarded" flag. */
const val ONBOARDED_FLAG = "onboarded"

/** A resume checkpoint decoded back into engine types. */
class ResumeRow(
    val activityId: Long,
    val lastStored: StoredPoint,
    val distanceM: Double,
    val elapsedNanos: Long,
)

/** One history-list row: the activity's stored TOTALS and nothing else. */
class ActivityRow(
    val id: Long,
    val sport: String,
    val startWallMillis: Long,
    val distanceM: Double,
    val elapsedNanos: Long,
    val fixCount: Long,
    val rejectedCount: Long,
    val mockDetected: Boolean,
    val isOpen: Boolean,
)

/** One captured media item pinned to a place and a moment along the route. */
class MediaRow(
    val id: Long,
    val activityId: Long,
    val kind: String,
    val filePath: String,
    val lat: Double?,
    val lon: Double?,
    val bearingDeg: Double?,
    val distanceM: Double,
    val elapsedNanos: Long,
    val createdWallMillis: Long,
    val durationMs: Long?,
    val caption: String?,
)

/**
 * The write/read boundary. Every point is durable before the next arrives; the
 * checkpoint maps the unmeasured 1σ to SQL NULL and back to the sentinel, so a
 * missing measurement is never stored as a number.
 */
class ActivityRepository(private val db: TrailmarkDb) {
    private val q get() = db.activityQueries

    fun insertActivity(uuid: String, sport: String, startWallMillis: Long): Long {
        q.insertActivity(uuid = uuid, sport = sport, start_wall_millis = startWallMillis)
        return q.lastId().executeAsOne()
    }

    fun appendPoints(activityId: Long, points: List<StoredPoint>) {
        db.transaction {
            for (p in points) {
                q.insertPoint(
                    activity_id = activityId,
                    time_nanos = p.timeNanos,
                    lat = p.lat,
                    lon = p.lon,
                    alt_gnss = p.altitudeM,
                    accuracy_m = if (p.accuracyM == UNMEASURED_ACCURACY_M) null else p.accuracyM,
                    speed_mps = p.speedMps,
                    bearing_deg = p.bearingDeg,
                    waypoint_type = p.type.name,
                )
            }
        }
    }

    fun pointCount(activityId: Long): Long = q.countPoints(activityId).executeAsOne()

    fun finishActivity(
        activityId: Long,
        elapsedNanos: Long,
        distanceM: Double,
        fixCount: Long,
        rejectedCount: Long,
        mockDetected: Boolean,
    ) {
        q.finishActivity(
            elapsed_nanos = elapsedNanos,
            distance_m = distanceM,
            fix_count = fixCount,
            rejected_count = rejectedCount,
            mock_detected = if (mockDetected) 1 else 0,
            id = activityId,
        )
    }

    fun saveResume(
        activityId: Long,
        lastStored: StoredPoint,
        distanceM: Double,
        elapsedNanos: Long,
    ) {
        q.upsertResume(
            key = "resume",
            activity_id = activityId,
            last_time_nanos = lastStored.timeNanos,
            last_lat = lastStored.lat,
            last_lon = lastStored.lon,
            last_accuracy_m = if (lastStored.accuracyM == UNMEASURED_ACCURACY_M) null else lastStored.accuracyM,
            last_type = lastStored.type.name,
            distance_m = distanceM,
            last_elapsed_nanos = elapsedNanos,
        )
    }

    fun loadResume(): ResumeRow? {
        val row = q.selectResume().executeAsOneOrNull() ?: return null
        return ResumeRow(
            activityId = row.activity_id,
            lastStored = StoredPoint(
                lat = row.last_lat,
                lon = row.last_lon,
                timeNanos = row.last_time_nanos,
                accuracyM = row.last_accuracy_m ?: UNMEASURED_ACCURACY_M,
                type = parseType(row.last_type),
            ),
            distanceM = row.distance_m,
            elapsedNanos = row.last_elapsed_nanos,
        )
    }

    fun clearResume(activityId: Long) {
        q.clearResume(activity_id = activityId)
    }

    fun activityById(id: Long): ActivityRow? {
        val r = q.selectActivity(id).executeAsOneOrNull() ?: return null
        return ActivityRow(
            id = r.id,
            sport = r.sport,
            startWallMillis = r.start_wall_millis,
            distanceM = r.distance_m,
            elapsedNanos = r.elapsed_nanos,
            fixCount = r.fix_count,
            rejectedCount = r.rejected_count,
            mockDetected = r.mock_detected != 0L,
            isOpen = false,
        )
    }

    fun listActivities(): List<ActivityRow> {        val resume = q.selectResume().executeAsOneOrNull()
        return q.listActivities().executeAsList().map { r ->
            val open = resume?.activity_id == r.id
            ActivityRow(
                id = r.id,
                sport = r.sport,
                startWallMillis = r.start_wall_millis,
                distanceM = if (open) resume!!.distance_m else r.distance_m,
                elapsedNanos = if (open) resume!!.last_elapsed_nanos else r.elapsed_nanos,
                fixCount = r.fix_count,
                rejectedCount = r.rejected_count,
                mockDetected = r.mock_detected != 0L,
                isOpen = open,
            )
        }
    }

    fun loadPoints(activityId: Long): List<StoredPoint> =
        q.selectPoints(activity_id = activityId).executeAsList().map { p ->
            StoredPoint(
                lat = p.lat,
                lon = p.lon,
                timeNanos = p.time_nanos,
                accuracyM = p.accuracy_m ?: UNMEASURED_ACCURACY_M,
                type = parseType(p.waypoint_type),
                altitudeM = p.alt_gnss,
                speedMps = p.speed_mps,
                bearingDeg = p.bearing_deg,
            )
        }

    fun insertMedia(
        activityId: Long,
        kind: String,
        filePath: String,
        lat: Double?,
        lon: Double?,
        bearingDeg: Double?,
        distanceM: Double,
        elapsedNanos: Long,
        createdWallMillis: Long,
        durationMs: Long?,
        caption: String?,
    ): Long {
        q.insertMedia(
            activity_id = activityId,
            kind = kind,
            file_path = filePath,
            lat = lat,
            lon = lon,
            bearing_deg = bearingDeg,
            distance_m = distanceM,
            elapsed_nanos = elapsedNanos,
            created_wall_millis = createdWallMillis,
            duration_ms = durationMs,
            caption = caption,
        )
        return q.lastId().executeAsOne()
    }

    fun listMedia(activityId: Long): List<MediaRow> =
        q.listMedia(activity_id = activityId).executeAsList().map { m ->
            MediaRow(
                id = m.id,
                activityId = m.activity_id,
                kind = m.kind,
                filePath = m.file_path,
                lat = m.lat,
                lon = m.lon,
                bearingDeg = m.bearing_deg,
                distanceM = m.distance_m,
                elapsedNanos = m.elapsed_nanos,
                createdWallMillis = m.created_wall_millis,
                durationMs = m.duration_ms,
                caption = m.caption,
            )
        }

    fun deleteMedia(id: Long) {
        q.deleteMedia(id = id)
    }

    fun flag(key: String, default: Boolean = false): Boolean =
        (q.getFlag(key).executeAsOneOrNull())?.let { it != 0L } ?: default

    fun setFlag(key: String, value: Boolean) {
        q.setFlag(key, if (value) 1L else 0L)
    }

    private fun parseType(name: String): WaypointType =
        WaypointType.entries.firstOrNull { it.name == name } ?: WaypointType.NORMAL
}
