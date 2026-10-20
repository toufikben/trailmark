package dev.toufik.trailmark.`data`

import kotlin.Double
import kotlin.Long
import kotlin.String

public data class Track_point(
  public val id: Long,
  public val activity_id: Long,
  public val time_nanos: Long,
  public val lat: Double,
  public val lon: Double,
  public val alt_gnss: Double?,
  public val accuracy_m: Double?,
  public val speed_mps: Double?,
  public val bearing_deg: Double?,
  public val waypoint_type: String,
)
