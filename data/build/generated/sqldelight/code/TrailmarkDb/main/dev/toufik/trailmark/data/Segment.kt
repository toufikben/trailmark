package dev.toufik.trailmark.`data`

import kotlin.Double
import kotlin.Long
import kotlin.String

public data class Segment(
  public val id: Long,
  public val name: String,
  public val start_lat: Double,
  public val start_lon: Double,
  public val end_lat: Double,
  public val end_lon: Double,
  public val created_wall_millis: Long,
)
