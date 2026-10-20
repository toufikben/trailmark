package dev.toufik.trailmark.`data`

import kotlin.Double
import kotlin.Long
import kotlin.String

public data class Media(
  public val id: Long,
  public val activity_id: Long,
  public val kind: String,
  public val file_path: String,
  public val lat: Double?,
  public val lon: Double?,
  public val bearing_deg: Double?,
  public val distance_m: Double,
  public val elapsed_nanos: Long,
  public val created_wall_millis: Long,
  public val duration_ms: Long?,
  public val caption: String?,
)
