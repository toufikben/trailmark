package dev.toufik.trailmark.app.service

import dev.toufik.trailmark.core.model.RawFix
import dev.toufik.trailmark.engine.FixSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Keeps a resumed track monotonic when the phone rebooted with a checkpoint
 * open: `elapsedRealtimeNanos` restarts at zero after a boot, so new fixes would
 * carry timestamps below the points already stored. Shifting them forward by the
 * gap keeps replay, zones and the exported timeline in order.
 */
internal class OffsetFixSource(
    private val delegate: FixSource,
    private val offsetNanos: Long,
) : FixSource {
    override fun fixes(): Flow<RawFix> =
        if (offsetNanos == 0L) delegate.fixes()
        else delegate.fixes().map { it.copy(timeNanos = it.timeNanos + offsetNanos) }
}
