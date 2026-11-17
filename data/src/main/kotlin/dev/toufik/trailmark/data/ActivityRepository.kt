package dev.toufik.trailmark.data

import dev.toufik.trailmark.core.model.StoredPoint
import dev.toufik.trailmark.core.model.UNMEASURED_ACCURACY_M
import dev.toufik.trailmark.core.model.WaypointType
import dev.toufik.trailmark.data.db.TrailmarkDb

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
    val climbM: Double,
    val fixCount: Long,
    val rejectedCount: Long,
    val mockDetected: Boolean,
    val isOpen: Boolean,
    val weatherTempC: Double?,
    val weatherWindKmh: Double?,
    val weatherWindDeg: Long?,
    val weatherCode: Long?,
)

/** One captured media item pinned to a place and a moment along the route. */
data class MediaRow(
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

/** One saved local segment with its start/end match anchors. */
class SegmentRow(
    val id: Long,
    val name: String,
    val startLat: Double,
    val startLon: Double,
    val endLat: Double,
    val endLon: Double,
)

/** One matched effort on an activity, carrying the segment name for display. */
class EffortRow(
    val id: Long,
    val segmentId: Long,
    val segmentName: String,
    val elapsedNanos: Long,
)

/** One timed passage of a segment, with when its activity started. */
class SegmentEffort(
    val id: Long,
    val activityId: Long,
    val elapsedNanos: Long,
    val startWallMillis: Long,
)

/** Aggregate per segment: attempt count and the best elapsed time, if any. */
class SegmentStat(
    val id: Long,
    val name: String,
    val attempts: Long,
    val bestNanos: Long?,
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

    fun setWeather(activityId: Long, tempC: Double, windKmh: Double, windDeg: Int, code: Int) {
        q.setWeather(
            weather_temp_c = tempC,
            weather_wind_kmh = windKmh,
            weather_wind_deg = windDeg.toLong(),
            weather_code = code.toLong(),
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
        // A row that is still being recorded has no final totals yet; the resume
        // entry carries the live ones, and the history list already peddles it as
        // an active effort. Without this the detail page of the run in progress
        // read 0.00 km / 0:00 while the list beside it showed the real numbers.
        val resume = q.selectResume().executeAsOneOrNull()?.takeIf { it.activity_id == id }
        return ActivityRow(
            id = r.id,
            sport = r.sport,
            startWallMillis = r.start_wall_millis,
            distanceM = resume?.distance_m ?: r.distance_m,
            elapsedNanos = resume?.last_elapsed_nanos ?: r.elapsed_nanos,
            climbM = r.climb_m,
            fixCount = r.fix_count,
            rejectedCount = r.rejected_count,
            mockDetected = r.mock_detected != 0L,
            isOpen = resume != null,
            weatherTempC = r.weather_temp_c,
            weatherWindKmh = r.weather_wind_kmh,
            weatherWindDeg = r.weather_wind_deg,
            weatherCode = r.weather_code,
        )
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
                climbM = r.climb_m,
                fixCount = r.fix_count,
                rejectedCount = r.rejected_count,
                mockDetected = r.mock_detected != 0L,
                isOpen = open,
                weatherTempC = r.weather_temp_c,
                weatherWindKmh = r.weather_wind_kmh,
                weatherWindDeg = r.weather_wind_deg,
                weatherCode = r.weather_code,
            )
        }
    }

    /**
     * The wall span the track itself covers, in nanoseconds: first to last
     * stored fix. This includes pauses, so a session with a long pause is not
     * reported to Health Connect as ending before it really did.
     */
    fun trackSpanNanos(activityId: Long): Long {
        val span = q.pointsSpan(activity_id = activityId).executeAsOne()
        val first = span.first_nanos ?: return 0L
        val last = span.last_nanos ?: return 0L
        return (last - first).coerceAtLeast(0L)
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

    /** Points whose track-point id is greater than [afterId], paired with their ids. */
    fun pointsAfter(activityId: Long, afterId: Long): List<Pair<Long, StoredPoint>> =
        q.pointsAfter(activity_id = activityId, id = afterId).executeAsList().map { p ->
            p.id to StoredPoint(
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

    /**
     * Removes an activity with its points, media rows, segment efforts and any
     * resume checkpoint, in one transaction. Returns the media files the caller
     * should delete from disk.
     */
    fun deleteActivity(activityId: Long): List<String> {
        var files: List<String> = emptyList()
        db.transaction {
            files = q.mediaPathsForActivity(activityId).executeAsList()
            q.deleteMediaForActivity(activityId)
            q.deleteEffortsForActivity(activityId)
            q.deletePointsForActivity(activityId)
            q.clearResume(activity_id = activityId)
            q.deleteActivityRow(activityId)
        }
        return files
    }

    fun setMediaCaption(id: Long, caption: String?) {
        q.setMediaCaption(caption = caption, id = id)
    }

    fun insertSegment(
        name: String,
        startLat: Double,
        startLon: Double,
        endLat: Double,
        endLon: Double,
    ): Long {
        q.insertSegment(
            name = name,
            start_lat = startLat,
            start_lon = startLon,
            end_lat = endLat,
            end_lon = endLon,
            created_wall_millis = System.currentTimeMillis(),
        )
        return q.lastId().executeAsOne()
    }

    fun listSegments(): List<SegmentRow> =
        q.listSegments().executeAsList().map {
            SegmentRow(it.id, it.name, it.start_lat, it.start_lon, it.end_lat, it.end_lon)
        }

    fun insertEffort(segmentId: Long, activityId: Long, elapsedNanos: Long) {
        q.insertEffort(
            segment_id = segmentId,
            activity_id = activityId,
            elapsed_nanos = elapsedNanos,
            created_wall_millis = System.currentTimeMillis(),
        )
    }

    fun effortExists(segmentId: Long, activityId: Long): Boolean =
        q.effortExists(segment_id = segmentId, activity_id = activityId).executeAsOne() > 0L

    fun bestEffortNanos(segmentId: Long): Long? =
        q.bestEffort(segment_id = segmentId).executeAsOneOrNull()?.best

    fun effortsForActivity(activityId: Long): List<EffortRow> =
        q.effortsForActivity(activity_id = activityId).executeAsList().map {
            EffortRow(it.id, it.segment_id, it.name, it.elapsed_nanos)
        }

    /** Every effort on one segment, fastest first: the "your best times" board. */
    fun effortsForSegment(segmentId: Long): List<SegmentEffort> =
        q.effortsForSegment(segment_id = segmentId).executeAsList().map {
            SegmentEffort(it.id, it.activity_id, it.elapsed_nanos, it.start_wall_millis)
        }

    fun deleteSegment(segmentId: Long) {
        db.transaction {
            q.deleteEffortsForSegment(segmentId)
            q.deleteSegment(segmentId)
        }
    }

    fun segmentStats(): List<SegmentStat> =
        q.segmentStats().executeAsList().map {
            SegmentStat(it.segment_id, it.name, it.attempts, it.best_nanos)
        }

    fun flag(key: String, default: Boolean = false): Boolean =
        (q.getFlag(key).executeAsOneOrNull())?.let { it != 0L } ?: default

    fun setFlag(key: String, value: Boolean) {
        q.setFlag(key, if (value) 1L else 0L)
    }

    private fun parseType(name: String): WaypointType =
        WaypointType.entries.firstOrNull { it.name == name } ?: WaypointType.NORMAL
}
