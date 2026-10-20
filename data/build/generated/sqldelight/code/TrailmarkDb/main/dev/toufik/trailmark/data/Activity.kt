package dev.toufik.trailmark.`data`

import kotlin.Double
import kotlin.Long
import kotlin.String

public data class Activity(
  public val id: Long,
  public val uuid: String,
  public val sport: String,
  public val start_wall_millis: Long,
  public val elapsed_nanos: Long,
  public val distance_m: Double,
  public val climb_m: Double,
  public val fix_count: Long,
  public val rejected_count: Long,
  public val mock_detected: Long,
  public val is_deleted: Long,
  public val server_uuid: String?,
  public val updated_at: Long?,
  public val weather_temp_c: Double?,
  public val weather_wind_kmh: Double?,
  public val weather_wind_deg: Long?,
  public val weather_code: Long?,
)
