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

    fun listActivities(): List<ActivityRow> {
        val resume = q.selectResume().executeAsOneOrNull()
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
        q.selectPoints(activityId).executeAsList().map { p ->
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

    fun flag(key: String, default: Boolean = false): Boolean =
        (q.getFlag(key).executeAsOneOrNull())?.let { it != 0L } ?: default

    fun setFlag(key: String, value: Boolean) {
        q.setFlag(key = key, value = if (value) 1L else 0L)
    }

    private fun parseType(name: String): WaypointType =
        WaypointType.entries.firstOrNull { it.name == name } ?: WaypointType.NORMAL
}
