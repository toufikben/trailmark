package dev.toufik.trailmark.`data`

import kotlin.Long
import kotlin.String

public data class EffortsForActivity(
  public val id: Long,
  public val segment_id: Long,
  public val elapsed_nanos: Long,
  public val name: String,
)
