package dev.toufik.trailmark.`data`

import kotlin.Long
import kotlin.String

public data class SegmentStats(
  public val segment_id: Long,
  public val name: String,
  public val attempts: Long,
  public val best_nanos: Long?,
)
