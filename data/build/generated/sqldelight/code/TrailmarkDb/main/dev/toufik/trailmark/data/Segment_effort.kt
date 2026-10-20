package dev.toufik.trailmark.`data`

import kotlin.Long

public data class Segment_effort(
  public val id: Long,
  public val segment_id: Long,
  public val activity_id: Long,
  public val elapsed_nanos: Long,
  public val created_wall_millis: Long,
)
