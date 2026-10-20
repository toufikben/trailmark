package dev.toufik.trailmark.`data`.db.`data`

import app.cash.sqldelight.TransacterImpl
import app.cash.sqldelight.db.AfterVersion
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema
import dev.toufik.trailmark.`data`.ActivityQueries
import dev.toufik.trailmark.`data`.db.TrailmarkDb
import kotlin.Long
import kotlin.Unit
import kotlin.reflect.KClass

internal val KClass<TrailmarkDb>.schema: SqlSchema<QueryResult.Value<Unit>>
  get() = TrailmarkDbImpl.Schema

internal fun KClass<TrailmarkDb>.newInstance(driver: SqlDriver): TrailmarkDb =
    TrailmarkDbImpl(driver)

private class TrailmarkDbImpl(
  driver: SqlDriver,
) : TransacterImpl(driver), TrailmarkDb {
  override val activityQueries: ActivityQueries = ActivityQueries(driver)

  public object Schema : SqlSchema<QueryResult.Value<Unit>> {
    override val version: Long
      get() = 4

    override fun create(driver: SqlDriver): QueryResult.Value<Unit> {
      driver.execute(null, """
          |CREATE TABLE activity (
          |  id INTEGER PRIMARY KEY AUTOINCREMENT,
          |  uuid TEXT NOT NULL,
          |  sport TEXT NOT NULL,
          |  start_wall_millis INTEGER NOT NULL,
          |  elapsed_nanos INTEGER NOT NULL DEFAULT 0,
          |  distance_m REAL NOT NULL DEFAULT 0,
          |  climb_m REAL NOT NULL DEFAULT 0,
          |  fix_count INTEGER NOT NULL DEFAULT 0,
          |  rejected_count INTEGER NOT NULL DEFAULT 0,
          |  mock_detected INTEGER NOT NULL DEFAULT 0,
          |  is_deleted INTEGER NOT NULL DEFAULT 0,
          |  server_uuid TEXT,
          |  updated_at INTEGER,
          |  weather_temp_c REAL,
          |  weather_wind_kmh REAL,
          |  weather_wind_deg INTEGER,
          |  weather_code INTEGER
          |)
          """.trimMargin(), 0)
      driver.execute(null, """
          |CREATE TABLE track_point (
          |  id INTEGER PRIMARY KEY AUTOINCREMENT,
          |  activity_id INTEGER NOT NULL REFERENCES activity(id),
          |  time_nanos INTEGER NOT NULL,
          |  lat REAL NOT NULL,
          |  lon REAL NOT NULL,
          |  alt_gnss REAL,
          |  accuracy_m REAL,
          |  speed_mps REAL,
          |  bearing_deg REAL,
          |  waypoint_type TEXT NOT NULL
          |)
          """.trimMargin(), 0)
      driver.execute(null, """
          |CREATE TABLE track_state (
          |  key TEXT NOT NULL PRIMARY KEY,
          |  activity_id INTEGER NOT NULL,
          |  last_time_nanos INTEGER NOT NULL,
          |  last_lat REAL NOT NULL,
          |  last_lon REAL NOT NULL,
          |  last_accuracy_m REAL,
          |  last_type TEXT NOT NULL,
          |  distance_m REAL NOT NULL,
          |  last_elapsed_nanos INTEGER NOT NULL DEFAULT 0
          |)
          """.trimMargin(), 0)
      driver.execute(null, """
          |CREATE TABLE app_flag (
          |  key TEXT NOT NULL PRIMARY KEY,
          |  value INTEGER NOT NULL
          |)
          """.trimMargin(), 0)
      driver.execute(null, """
          |CREATE TABLE media (
          |  id INTEGER PRIMARY KEY AUTOINCREMENT,
          |  activity_id INTEGER NOT NULL REFERENCES activity(id),
          |  kind TEXT NOT NULL,
          |  file_path TEXT NOT NULL,
          |  lat REAL,
          |  lon REAL,
          |  bearing_deg REAL,
          |  distance_m REAL NOT NULL DEFAULT 0,
          |  elapsed_nanos INTEGER NOT NULL DEFAULT 0,
          |  created_wall_millis INTEGER NOT NULL,
          |  duration_ms INTEGER,
          |  caption TEXT
          |)
          """.trimMargin(), 0)
      driver.execute(null, """
          |CREATE TABLE segment (
          |  id INTEGER PRIMARY KEY AUTOINCREMENT,
          |  name TEXT NOT NULL,
          |  start_lat REAL NOT NULL,
          |  start_lon REAL NOT NULL,
          |  end_lat REAL NOT NULL,
          |  end_lon REAL NOT NULL,
          |  created_wall_millis INTEGER NOT NULL
          |)
          """.trimMargin(), 0)
      driver.execute(null, """
          |CREATE TABLE segment_effort (
          |  id INTEGER PRIMARY KEY AUTOINCREMENT,
          |  segment_id INTEGER NOT NULL REFERENCES segment(id),
          |  activity_id INTEGER NOT NULL REFERENCES activity(id),
          |  elapsed_nanos INTEGER NOT NULL,
          |  created_wall_millis INTEGER NOT NULL
          |)
          """.trimMargin(), 0)
      return QueryResult.Unit
    }

    private fun migrateInternal(
      driver: SqlDriver,
      oldVersion: Long,
      newVersion: Long,
    ): QueryResult.Value<Unit> {
      if (oldVersion <= 1 && newVersion > 1) {
        driver.execute(null, """
            |CREATE TABLE media (
            |  id INTEGER PRIMARY KEY AUTOINCREMENT,
            |  activity_id INTEGER NOT NULL REFERENCES activity(id),
            |  kind TEXT NOT NULL,
            |  file_path TEXT NOT NULL,
            |  lat REAL,
            |  lon REAL,
            |  bearing_deg REAL,
            |  distance_m REAL NOT NULL DEFAULT 0,
            |  elapsed_nanos INTEGER NOT NULL DEFAULT 0,
            |  created_wall_millis INTEGER NOT NULL,
            |  duration_ms INTEGER,
            |  caption TEXT
            |)
            """.trimMargin(), 0)
      }
      if (oldVersion <= 2 && newVersion > 2) {
        driver.execute(null, "ALTER TABLE activity ADD COLUMN weather_temp_c REAL", 0)
        driver.execute(null, "ALTER TABLE activity ADD COLUMN weather_wind_kmh REAL", 0)
        driver.execute(null, "ALTER TABLE activity ADD COLUMN weather_wind_deg INTEGER", 0)
        driver.execute(null, "ALTER TABLE activity ADD COLUMN weather_code INTEGER", 0)
      }
      if (oldVersion <= 3 && newVersion > 3) {
        driver.execute(null, """
            |CREATE TABLE segment (
            |  id INTEGER PRIMARY KEY AUTOINCREMENT,
            |  name TEXT NOT NULL,
            |  start_lat REAL NOT NULL,
            |  start_lon REAL NOT NULL,
            |  end_lat REAL NOT NULL,
            |  end_lon REAL NOT NULL,
            |  created_wall_millis INTEGER NOT NULL
            |)
            """.trimMargin(), 0)
        driver.execute(null, """
            |CREATE TABLE segment_effort (
            |  id INTEGER PRIMARY KEY AUTOINCREMENT,
            |  segment_id INTEGER NOT NULL REFERENCES segment(id),
            |  activity_id INTEGER NOT NULL REFERENCES activity(id),
            |  elapsed_nanos INTEGER NOT NULL,
            |  created_wall_millis INTEGER NOT NULL
            |)
            """.trimMargin(), 0)
      }
      return QueryResult.Unit
    }

    override fun migrate(
      driver: SqlDriver,
      oldVersion: Long,
      newVersion: Long,
      vararg callbacks: AfterVersion,
    ): QueryResult.Value<Unit> {
      var lastVersion = oldVersion

      callbacks.filter { it.afterVersion in oldVersion until newVersion }
      .sortedBy { it.afterVersion }
      .forEach { callback ->
        migrateInternal(driver, oldVersion = lastVersion, newVersion = callback.afterVersion + 1)
        callback.block(driver)
        lastVersion = callback.afterVersion + 1
      }

      if (lastVersion < newVersion) {
        migrateInternal(driver, lastVersion, newVersion)
      }
      return QueryResult.Unit
    }
  }
}
