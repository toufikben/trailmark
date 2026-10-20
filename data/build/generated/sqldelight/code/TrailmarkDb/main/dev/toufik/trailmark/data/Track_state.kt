package dev.toufik.trailmark.`data`

import kotlin.Double
import kotlin.Long
import kotlin.String

public data class Track_state(
  public val key: String,
  public val activity_id: Long,
  public val last_time_nanos: Long,
  public val last_lat: Double,
  public val last_lon: Double,
  public val last_accuracy_m: Double?,
  public val last_type: String,
  public val distance_m: Double,
  public val last_elapsed_nanos: Long,
)
