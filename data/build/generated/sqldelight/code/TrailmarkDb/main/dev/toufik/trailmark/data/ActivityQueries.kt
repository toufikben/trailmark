package dev.toufik.trailmark.`data`

import app.cash.sqldelight.ExecutableQuery
import app.cash.sqldelight.Query
import app.cash.sqldelight.TransacterImpl
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import kotlin.Any
import kotlin.Double
import kotlin.Long
import kotlin.String

public class ActivityQueries(
  driver: SqlDriver,
) : TransacterImpl(driver) {
  public fun lastId(): ExecutableQuery<Long> = Query(-698_432_036, driver, "Activity.sq", "lastId",
      "SELECT last_insert_rowid()") { cursor ->
    cursor.getLong(0)!!
  }

  public fun countPoints(activity_id: Long): Query<Long> = CountPointsQuery(activity_id) { cursor ->
    cursor.getLong(0)!!
  }

  public fun <T : Any> selectActivity(id: Long, mapper: (
    id: Long,
    uuid: String,
    sport: String,
    start_wall_millis: Long,
    elapsed_nanos: Long,
    distance_m: Double,
    climb_m: Double,
    fix_count: Long,
    rejected_count: Long,
    mock_detected: Long,
    is_deleted: Long,
    server_uuid: String?,
    updated_at: Long?,
    weather_temp_c: Double?,
    weather_wind_kmh: Double?,
    weather_wind_deg: Long?,
    weather_code: Long?,
  ) -> T): Query<T> = SelectActivityQuery(id) { cursor ->
    mapper(
      cursor.getLong(0)!!,
      cursor.getString(1)!!,
      cursor.getString(2)!!,
      cursor.getLong(3)!!,
      cursor.getLong(4)!!,
      cursor.getDouble(5)!!,
      cursor.getDouble(6)!!,
      cursor.getLong(7)!!,
      cursor.getLong(8)!!,
      cursor.getLong(9)!!,
      cursor.getLong(10)!!,
      cursor.getString(11),
      cursor.getLong(12),
      cursor.getDouble(13),
      cursor.getDouble(14),
      cursor.getLong(15),
      cursor.getLong(16)
    )
  }

  public fun selectActivity(id: Long): Query<Activity> = selectActivity(id) { id_, uuid, sport,
      start_wall_millis, elapsed_nanos, distance_m, climb_m, fix_count, rejected_count,
      mock_detected, is_deleted, server_uuid, updated_at, weather_temp_c, weather_wind_kmh,
      weather_wind_deg, weather_code ->
    Activity(
      id_,
      uuid,
      sport,
      start_wall_millis,
      elapsed_nanos,
      distance_m,
      climb_m,
      fix_count,
      rejected_count,
      mock_detected,
      is_deleted,
      server_uuid,
      updated_at,
      weather_temp_c,
      weather_wind_kmh,
      weather_wind_deg,
      weather_code
    )
  }

  public fun <T : Any> selectPoints(activity_id: Long, mapper: (
    id: Long,
    activity_id: Long,
    time_nanos: Long,
    lat: Double,
    lon: Double,
    alt_gnss: Double?,
    accuracy_m: Double?,
    speed_mps: Double?,
    bearing_deg: Double?,
    waypoint_type: String,
  ) -> T): Query<T> = SelectPointsQuery(activity_id) { cursor ->
    mapper(
      cursor.getLong(0)!!,
      cursor.getLong(1)!!,
      cursor.getLong(2)!!,
      cursor.getDouble(3)!!,
      cursor.getDouble(4)!!,
      cursor.getDouble(5),
      cursor.getDouble(6),
      cursor.getDouble(7),
      cursor.getDouble(8),
      cursor.getString(9)!!
    )
  }

  public fun selectPoints(activity_id: Long): Query<Track_point> = selectPoints(activity_id) { id,
      activity_id_, time_nanos, lat, lon, alt_gnss, accuracy_m, speed_mps, bearing_deg,
      waypoint_type ->
    Track_point(
      id,
      activity_id_,
      time_nanos,
      lat,
      lon,
      alt_gnss,
      accuracy_m,
      speed_mps,
      bearing_deg,
      waypoint_type
    )
  }

  public fun <T : Any> pointsAfter(
    activity_id: Long,
    id: Long,
    mapper: (
      id: Long,
      activity_id: Long,
      time_nanos: Long,
      lat: Double,
      lon: Double,
      alt_gnss: Double?,
      accuracy_m: Double?,
      speed_mps: Double?,
      bearing_deg: Double?,
      waypoint_type: String,
    ) -> T,
  ): Query<T> = PointsAfterQuery(activity_id, id) { cursor ->
    mapper(
      cursor.getLong(0)!!,
      cursor.getLong(1)!!,
      cursor.getLong(2)!!,
      cursor.getDouble(3)!!,
      cursor.getDouble(4)!!,
      cursor.getDouble(5),
      cursor.getDouble(6),
      cursor.getDouble(7),
      cursor.getDouble(8),
      cursor.getString(9)!!
    )
  }

  public fun pointsAfter(activity_id: Long, id: Long): Query<Track_point> = pointsAfter(activity_id,
      id) { id_, activity_id_, time_nanos, lat, lon, alt_gnss, accuracy_m, speed_mps, bearing_deg,
      waypoint_type ->
    Track_point(
      id_,
      activity_id_,
      time_nanos,
      lat,
      lon,
      alt_gnss,
      accuracy_m,
      speed_mps,
      bearing_deg,
      waypoint_type
    )
  }

  public fun <T : Any> listActivities(mapper: (
    id: Long,
    uuid: String,
    sport: String,
    start_wall_millis: Long,
    elapsed_nanos: Long,
    distance_m: Double,
    climb_m: Double,
    fix_count: Long,
    rejected_count: Long,
    mock_detected: Long,
    is_deleted: Long,
    server_uuid: String?,
    updated_at: Long?,
    weather_temp_c: Double?,
    weather_wind_kmh: Double?,
    weather_wind_deg: Long?,
    weather_code: Long?,
  ) -> T): Query<T> = Query(2_113_001_398, arrayOf("activity"), driver, "Activity.sq",
      "listActivities",
      "SELECT activity.id, activity.uuid, activity.sport, activity.start_wall_millis, activity.elapsed_nanos, activity.distance_m, activity.climb_m, activity.fix_count, activity.rejected_count, activity.mock_detected, activity.is_deleted, activity.server_uuid, activity.updated_at, activity.weather_temp_c, activity.weather_wind_kmh, activity.weather_wind_deg, activity.weather_code FROM activity WHERE is_deleted = 0 ORDER BY start_wall_millis DESC, id DESC") {
      cursor ->
    mapper(
      cursor.getLong(0)!!,
      cursor.getString(1)!!,
      cursor.getString(2)!!,
      cursor.getLong(3)!!,
      cursor.getLong(4)!!,
      cursor.getDouble(5)!!,
      cursor.getDouble(6)!!,
      cursor.getLong(7)!!,
      cursor.getLong(8)!!,
      cursor.getLong(9)!!,
      cursor.getLong(10)!!,
      cursor.getString(11),
      cursor.getLong(12),
      cursor.getDouble(13),
      cursor.getDouble(14),
      cursor.getLong(15),
      cursor.getLong(16)
    )
  }

  public fun listActivities(): Query<Activity> = listActivities { id, uuid, sport,
      start_wall_millis, elapsed_nanos, distance_m, climb_m, fix_count, rejected_count,
      mock_detected, is_deleted, server_uuid, updated_at, weather_temp_c, weather_wind_kmh,
      weather_wind_deg, weather_code ->
    Activity(
      id,
      uuid,
      sport,
      start_wall_millis,
      elapsed_nanos,
      distance_m,
      climb_m,
      fix_count,
      rejected_count,
      mock_detected,
      is_deleted,
      server_uuid,
      updated_at,
      weather_temp_c,
      weather_wind_kmh,
      weather_wind_deg,
      weather_code
    )
  }

  public fun <T : Any> selectResume(mapper: (
    key: String,
    activity_id: Long,
    last_time_nanos: Long,
    last_lat: Double,
    last_lon: Double,
    last_accuracy_m: Double?,
    last_type: String,
    distance_m: Double,
    last_elapsed_nanos: Long,
  ) -> T): Query<T> = Query(1_370_529_684, arrayOf("track_state"), driver, "Activity.sq",
      "selectResume",
      "SELECT track_state.key, track_state.activity_id, track_state.last_time_nanos, track_state.last_lat, track_state.last_lon, track_state.last_accuracy_m, track_state.last_type, track_state.distance_m, track_state.last_elapsed_nanos FROM track_state WHERE key = 'resume' LIMIT 1") {
      cursor ->
    mapper(
      cursor.getString(0)!!,
      cursor.getLong(1)!!,
      cursor.getLong(2)!!,
      cursor.getDouble(3)!!,
      cursor.getDouble(4)!!,
      cursor.getDouble(5),
      cursor.getString(6)!!,
      cursor.getDouble(7)!!,
      cursor.getLong(8)!!
    )
  }

  public fun selectResume(): Query<Track_state> = selectResume { key, activity_id, last_time_nanos,
      last_lat, last_lon, last_accuracy_m, last_type, distance_m, last_elapsed_nanos ->
    Track_state(
      key,
      activity_id,
      last_time_nanos,
      last_lat,
      last_lon,
      last_accuracy_m,
      last_type,
      distance_m,
      last_elapsed_nanos
    )
  }

  public fun getFlag(key: String): Query<Long> = GetFlagQuery(key) { cursor ->
    cursor.getLong(0)!!
  }

  public fun <T : Any> listMedia(activity_id: Long, mapper: (
    id: Long,
    activity_id: Long,
    kind: String,
    file_path: String,
    lat: Double?,
    lon: Double?,
    bearing_deg: Double?,
    distance_m: Double,
    elapsed_nanos: Long,
    created_wall_millis: Long,
    duration_ms: Long?,
    caption: String?,
  ) -> T): Query<T> = ListMediaQuery(activity_id) { cursor ->
    mapper(
      cursor.getLong(0)!!,
      cursor.getLong(1)!!,
      cursor.getString(2)!!,
      cursor.getString(3)!!,
      cursor.getDouble(4),
      cursor.getDouble(5),
      cursor.getDouble(6),
      cursor.getDouble(7)!!,
      cursor.getLong(8)!!,
      cursor.getLong(9)!!,
      cursor.getLong(10),
      cursor.getString(11)
    )
  }

  public fun listMedia(activity_id: Long): Query<Media> = listMedia(activity_id) { id, activity_id_,
      kind, file_path, lat, lon, bearing_deg, distance_m, elapsed_nanos, created_wall_millis,
      duration_ms, caption ->
    Media(
      id,
      activity_id_,
      kind,
      file_path,
      lat,
      lon,
      bearing_deg,
      distance_m,
      elapsed_nanos,
      created_wall_millis,
      duration_ms,
      caption
    )
  }

  public fun <T : Any> listSegments(mapper: (
    id: Long,
    name: String,
    start_lat: Double,
    start_lon: Double,
    end_lat: Double,
    end_lon: Double,
    created_wall_millis: Long,
  ) -> T): Query<T> = Query(1_511_801_257, arrayOf("segment"), driver, "Activity.sq",
      "listSegments",
      "SELECT segment.id, segment.name, segment.start_lat, segment.start_lon, segment.end_lat, segment.end_lon, segment.created_wall_millis FROM segment ORDER BY id ASC") {
      cursor ->
    mapper(
      cursor.getLong(0)!!,
      cursor.getString(1)!!,
      cursor.getDouble(2)!!,
      cursor.getDouble(3)!!,
      cursor.getDouble(4)!!,
      cursor.getDouble(5)!!,
      cursor.getLong(6)!!
    )
  }

  public fun listSegments(): Query<Segment> = listSegments { id, name, start_lat, start_lon,
      end_lat, end_lon, created_wall_millis ->
    Segment(
      id,
      name,
      start_lat,
      start_lon,
      end_lat,
      end_lon,
      created_wall_millis
    )
  }

  public fun effortExists(segment_id: Long, activity_id: Long): Query<Long> =
      EffortExistsQuery(segment_id, activity_id) { cursor ->
    cursor.getLong(0)!!
  }

  public fun <T : Any> bestEffort(segment_id: Long, mapper: (best: Long?) -> T): Query<T> =
      BestEffortQuery(segment_id) { cursor ->
    mapper(
      cursor.getLong(0)
    )
  }

  public fun bestEffort(segment_id: Long): Query<BestEffort> = bestEffort(segment_id) { best ->
    BestEffort(
      best
    )
  }

  public fun <T : Any> effortsForActivity(activity_id: Long, mapper: (
    id: Long,
    segment_id: Long,
    elapsed_nanos: Long,
    name: String,
  ) -> T): Query<T> = EffortsForActivityQuery(activity_id) { cursor ->
    mapper(
      cursor.getLong(0)!!,
      cursor.getLong(1)!!,
      cursor.getLong(2)!!,
      cursor.getString(3)!!
    )
  }

  public fun effortsForActivity(activity_id: Long): Query<EffortsForActivity> =
      effortsForActivity(activity_id) { id, segment_id, elapsed_nanos, name ->
    EffortsForActivity(
      id,
      segment_id,
      elapsed_nanos,
      name
    )
  }

  public fun <T : Any> segmentStats(mapper: (
    segment_id: Long,
    name: String,
    attempts: Long,
    best_nanos: Long?,
  ) -> T): Query<T> = Query(-104_259_465, arrayOf("segment", "segment_effort"), driver,
      "Activity.sq", "segmentStats", """
  |SELECT s.id AS segment_id, s.name AS name, count(e.id) AS attempts, min(e.elapsed_nanos) AS best_nanos
  |FROM segment s LEFT JOIN segment_effort e ON e.segment_id = s.id
  |GROUP BY s.id, s.name
  |ORDER BY s.id ASC
  """.trimMargin()) { cursor ->
    mapper(
      cursor.getLong(0)!!,
      cursor.getString(1)!!,
      cursor.getLong(2)!!,
      cursor.getLong(3)
    )
  }

  public fun segmentStats(): Query<SegmentStats> = segmentStats { segment_id, name, attempts,
      best_nanos ->
    SegmentStats(
      segment_id,
      name,
      attempts,
      best_nanos
    )
  }

  public fun insertActivity(
    uuid: String,
    sport: String,
    start_wall_millis: Long,
  ) {
    driver.execute(1_869_679_987,
        """INSERT INTO activity(uuid, sport, start_wall_millis) VALUES (?, ?, ?)""", 3) {
          bindString(0, uuid)
          bindString(1, sport)
          bindLong(2, start_wall_millis)
        }
    notifyQueries(1_869_679_987) { emit ->
      emit("activity")
    }
  }

  public fun insertPoint(
    activity_id: Long,
    time_nanos: Long,
    lat: Double,
    lon: Double,
    alt_gnss: Double?,
    accuracy_m: Double?,
    speed_mps: Double?,
    bearing_deg: Double?,
    waypoint_type: String,
  ) {
    driver.execute(1_109_521_836, """
        |INSERT INTO track_point(activity_id, time_nanos, lat, lon, alt_gnss, accuracy_m, speed_mps, bearing_deg, waypoint_type)
        |VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimMargin(), 9) {
          bindLong(0, activity_id)
          bindLong(1, time_nanos)
          bindDouble(2, lat)
          bindDouble(3, lon)
          bindDouble(4, alt_gnss)
          bindDouble(5, accuracy_m)
          bindDouble(6, speed_mps)
          bindDouble(7, bearing_deg)
          bindString(8, waypoint_type)
        }
    notifyQueries(1_109_521_836) { emit ->
      emit("track_point")
    }
  }

  public fun finishActivity(
    elapsed_nanos: Long,
    distance_m: Double,
    fix_count: Long,
    rejected_count: Long,
    mock_detected: Long,
    id: Long,
  ) {
    driver.execute(1_333_222_733,
        """UPDATE activity SET elapsed_nanos = ?, distance_m = ?, fix_count = ?, rejected_count = ?, mock_detected = ? WHERE id = ?""",
        6) {
          bindLong(0, elapsed_nanos)
          bindDouble(1, distance_m)
          bindLong(2, fix_count)
          bindLong(3, rejected_count)
          bindLong(4, mock_detected)
          bindLong(5, id)
        }
    notifyQueries(1_333_222_733) { emit ->
      emit("activity")
    }
  }

  public fun upsertResume(
    key: String,
    activity_id: Long,
    last_time_nanos: Long,
    last_lat: Double,
    last_lon: Double,
    last_accuracy_m: Double?,
    last_type: String,
    distance_m: Double,
    last_elapsed_nanos: Long,
  ) {
    driver.execute(-1_961_143_929, """
        |INSERT OR REPLACE INTO track_state(key, activity_id, last_time_nanos, last_lat, last_lon, last_accuracy_m, last_type, distance_m, last_elapsed_nanos)
        |VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimMargin(), 9) {
          bindString(0, key)
          bindLong(1, activity_id)
          bindLong(2, last_time_nanos)
          bindDouble(3, last_lat)
          bindDouble(4, last_lon)
          bindDouble(5, last_accuracy_m)
          bindString(6, last_type)
          bindDouble(7, distance_m)
          bindLong(8, last_elapsed_nanos)
        }
    notifyQueries(-1_961_143_929) { emit ->
      emit("track_state")
    }
  }

  public fun clearResume(activity_id: Long) {
    driver.execute(-401_259_473,
        """DELETE FROM track_state WHERE key = 'resume' AND activity_id = ?""", 1) {
          bindLong(0, activity_id)
        }
    notifyQueries(-401_259_473) { emit ->
      emit("track_state")
    }
  }

  public fun setFlag(key: String, value_: Long) {
    driver.execute(1_855_105_219, """INSERT OR REPLACE INTO app_flag(key, value) VALUES (?, ?)""",
        2) {
          bindString(0, key)
          bindLong(1, value_)
        }
    notifyQueries(1_855_105_219) { emit ->
      emit("app_flag")
    }
  }

  public fun insertMedia(
    activity_id: Long,
    kind: String,
    file_path: String,
    lat: Double?,
    lon: Double?,
    bearing_deg: Double?,
    distance_m: Double,
    elapsed_nanos: Long,
    created_wall_millis: Long,
    duration_ms: Long?,
    caption: String?,
  ) {
    driver.execute(1_106_448_384, """
        |INSERT INTO media(activity_id, kind, file_path, lat, lon, bearing_deg, distance_m, elapsed_nanos, created_wall_millis, duration_ms, caption)
        |VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimMargin(), 11) {
          bindLong(0, activity_id)
          bindString(1, kind)
          bindString(2, file_path)
          bindDouble(3, lat)
          bindDouble(4, lon)
          bindDouble(5, bearing_deg)
          bindDouble(6, distance_m)
          bindLong(7, elapsed_nanos)
          bindLong(8, created_wall_millis)
          bindLong(9, duration_ms)
          bindString(10, caption)
        }
    notifyQueries(1_106_448_384) { emit ->
      emit("media")
    }
  }

  public fun deleteMedia(id: Long) {
    driver.execute(87_774_286, """DELETE FROM media WHERE id = ?""", 1) {
          bindLong(0, id)
        }
    notifyQueries(87_774_286) { emit ->
      emit("media")
    }
  }

  public fun setMediaCaption(caption: String?, id: Long) {
    driver.execute(-1_627_046_983, """UPDATE media SET caption = ? WHERE id = ?""", 2) {
          bindString(0, caption)
          bindLong(1, id)
        }
    notifyQueries(-1_627_046_983) { emit ->
      emit("media")
    }
  }

  public fun setWeather(
    weather_temp_c: Double?,
    weather_wind_kmh: Double?,
    weather_wind_deg: Long?,
    weather_code: Long?,
    id: Long,
  ) {
    driver.execute(-196_838_595,
        """UPDATE activity SET weather_temp_c = ?, weather_wind_kmh = ?, weather_wind_deg = ?, weather_code = ? WHERE id = ?""",
        5) {
          bindDouble(0, weather_temp_c)
          bindDouble(1, weather_wind_kmh)
          bindLong(2, weather_wind_deg)
          bindLong(3, weather_code)
          bindLong(4, id)
        }
    notifyQueries(-196_838_595) { emit ->
      emit("activity")
    }
  }

  public fun insertSegment(
    name: String,
    start_lat: Double,
    start_lon: Double,
    end_lat: Double,
    end_lon: Double,
    created_wall_millis: Long,
  ) {
    driver.execute(-822_040_497, """
        |INSERT INTO segment(name, start_lat, start_lon, end_lat, end_lon, created_wall_millis)
        |VALUES (?, ?, ?, ?, ?, ?)
        """.trimMargin(), 6) {
          bindString(0, name)
          bindDouble(1, start_lat)
          bindDouble(2, start_lon)
          bindDouble(3, end_lat)
          bindDouble(4, end_lon)
          bindLong(5, created_wall_millis)
        }
    notifyQueries(-822_040_497) { emit ->
      emit("segment")
    }
  }

  public fun insertEffort(
    segment_id: Long,
    activity_id: Long,
    elapsed_nanos: Long,
    created_wall_millis: Long,
  ) {
    driver.execute(-287_882_160, """
        |INSERT INTO segment_effort(segment_id, activity_id, elapsed_nanos, created_wall_millis)
        |VALUES (?, ?, ?, ?)
        """.trimMargin(), 4) {
          bindLong(0, segment_id)
          bindLong(1, activity_id)
          bindLong(2, elapsed_nanos)
          bindLong(3, created_wall_millis)
        }
    notifyQueries(-287_882_160) { emit ->
      emit("segment_effort")
    }
  }

  private inner class CountPointsQuery<out T : Any>(
    public val activity_id: Long,
    mapper: (SqlCursor) -> T,
  ) : Query<T>(mapper) {
    override fun addListener(listener: Query.Listener) {
      driver.addListener("track_point", listener = listener)
    }

    override fun removeListener(listener: Query.Listener) {
      driver.removeListener("track_point", listener = listener)
    }

    override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> =
        driver.executeQuery(-1_231_889_881,
        """SELECT count(*) FROM track_point WHERE activity_id = ?""", mapper, 1) {
      bindLong(0, activity_id)
    }

    override fun toString(): String = "Activity.sq:countPoints"
  }

  private inner class SelectActivityQuery<out T : Any>(
    public val id: Long,
    mapper: (SqlCursor) -> T,
  ) : Query<T>(mapper) {
    override fun addListener(listener: Query.Listener) {
      driver.addListener("activity", listener = listener)
    }

    override fun removeListener(listener: Query.Listener) {
      driver.removeListener("activity", listener = listener)
    }

    override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> =
        driver.executeQuery(1_498_844_342,
        """SELECT activity.id, activity.uuid, activity.sport, activity.start_wall_millis, activity.elapsed_nanos, activity.distance_m, activity.climb_m, activity.fix_count, activity.rejected_count, activity.mock_detected, activity.is_deleted, activity.server_uuid, activity.updated_at, activity.weather_temp_c, activity.weather_wind_kmh, activity.weather_wind_deg, activity.weather_code FROM activity WHERE id = ? LIMIT 1""",
        mapper, 1) {
      bindLong(0, id)
    }

    override fun toString(): String = "Activity.sq:selectActivity"
  }

  private inner class SelectPointsQuery<out T : Any>(
    public val activity_id: Long,
    mapper: (SqlCursor) -> T,
  ) : Query<T>(mapper) {
    override fun addListener(listener: Query.Listener) {
      driver.addListener("track_point", listener = listener)
    }

    override fun removeListener(listener: Query.Listener) {
      driver.removeListener("track_point", listener = listener)
    }

    override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> =
        driver.executeQuery(1_322_202_186,
        """SELECT track_point.id, track_point.activity_id, track_point.time_nanos, track_point.lat, track_point.lon, track_point.alt_gnss, track_point.accuracy_m, track_point.speed_mps, track_point.bearing_deg, track_point.waypoint_type FROM track_point WHERE activity_id = ? ORDER BY id ASC""",
        mapper, 1) {
      bindLong(0, activity_id)
    }

    override fun toString(): String = "Activity.sq:selectPoints"
  }

  private inner class PointsAfterQuery<out T : Any>(
    public val activity_id: Long,
    public val id: Long,
    mapper: (SqlCursor) -> T,
  ) : Query<T>(mapper) {
    override fun addListener(listener: Query.Listener) {
      driver.addListener("track_point", listener = listener)
    }

    override fun removeListener(listener: Query.Listener) {
      driver.removeListener("track_point", listener = listener)
    }

    override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> =
        driver.executeQuery(-1_943_349_746,
        """SELECT track_point.id, track_point.activity_id, track_point.time_nanos, track_point.lat, track_point.lon, track_point.alt_gnss, track_point.accuracy_m, track_point.speed_mps, track_point.bearing_deg, track_point.waypoint_type FROM track_point WHERE activity_id = ? AND id > ? ORDER BY id ASC""",
        mapper, 2) {
      bindLong(0, activity_id)
      bindLong(1, id)
    }

    override fun toString(): String = "Activity.sq:pointsAfter"
  }

  private inner class GetFlagQuery<out T : Any>(
    public val key: String,
    mapper: (SqlCursor) -> T,
  ) : Query<T>(mapper) {
    override fun addListener(listener: Query.Listener) {
      driver.addListener("app_flag", listener = listener)
    }

    override fun removeListener(listener: Query.Listener) {
      driver.removeListener("app_flag", listener = listener)
    }

    override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> =
        driver.executeQuery(-205_004_361, """SELECT value FROM app_flag WHERE key = ? LIMIT 1""",
        mapper, 1) {
      bindString(0, key)
    }

    override fun toString(): String = "Activity.sq:getFlag"
  }

  private inner class ListMediaQuery<out T : Any>(
    public val activity_id: Long,
    mapper: (SqlCursor) -> T,
  ) : Query<T>(mapper) {
    override fun addListener(listener: Query.Listener) {
      driver.addListener("media", listener = listener)
    }

    override fun removeListener(listener: Query.Listener) {
      driver.removeListener("media", listener = listener)
    }

    override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> =
        driver.executeQuery(-1_105_798_533,
        """SELECT media.id, media.activity_id, media.kind, media.file_path, media.lat, media.lon, media.bearing_deg, media.distance_m, media.elapsed_nanos, media.created_wall_millis, media.duration_ms, media.caption FROM media WHERE activity_id = ? ORDER BY elapsed_nanos ASC, id ASC""",
        mapper, 1) {
      bindLong(0, activity_id)
    }

    override fun toString(): String = "Activity.sq:listMedia"
  }

  private inner class EffortExistsQuery<out T : Any>(
    public val segment_id: Long,
    public val activity_id: Long,
    mapper: (SqlCursor) -> T,
  ) : Query<T>(mapper) {
    override fun addListener(listener: Query.Listener) {
      driver.addListener("segment_effort", listener = listener)
    }

    override fun removeListener(listener: Query.Listener) {
      driver.removeListener("segment_effort", listener = listener)
    }

    override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> =
        driver.executeQuery(-1_931_754_573,
        """SELECT count(*) FROM segment_effort WHERE segment_id = ? AND activity_id = ?""", mapper,
        2) {
      bindLong(0, segment_id)
      bindLong(1, activity_id)
    }

    override fun toString(): String = "Activity.sq:effortExists"
  }

  private inner class BestEffortQuery<out T : Any>(
    public val segment_id: Long,
    mapper: (SqlCursor) -> T,
  ) : Query<T>(mapper) {
    override fun addListener(listener: Query.Listener) {
      driver.addListener("segment_effort", listener = listener)
    }

    override fun removeListener(listener: Query.Listener) {
      driver.removeListener("segment_effort", listener = listener)
    }

    override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> =
        driver.executeQuery(456_958_459,
        """SELECT min(elapsed_nanos) AS best FROM segment_effort WHERE segment_id = ?""", mapper, 1)
        {
      bindLong(0, segment_id)
    }

    override fun toString(): String = "Activity.sq:bestEffort"
  }

  private inner class EffortsForActivityQuery<out T : Any>(
    public val activity_id: Long,
    mapper: (SqlCursor) -> T,
  ) : Query<T>(mapper) {
    override fun addListener(listener: Query.Listener) {
      driver.addListener("segment_effort", "segment", listener = listener)
    }

    override fun removeListener(listener: Query.Listener) {
      driver.removeListener("segment_effort", "segment", listener = listener)
    }

    override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> =
        driver.executeQuery(-685_811_076, """
    |SELECT e.id, e.segment_id, e.elapsed_nanos, s.name
    |FROM segment_effort e JOIN segment s ON s.id = e.segment_id
    |WHERE e.activity_id = ? ORDER BY e.id ASC
    """.trimMargin(), mapper, 1) {
      bindLong(0, activity_id)
    }

    override fun toString(): String = "Activity.sq:effortsForActivity"
  }
}
