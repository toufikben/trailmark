package dev.toufik.trailmark.engine

import dev.toufik.trailmark.core.model.RawFix
import kotlinx.coroutines.flow.Flow

/**
 * A source of GPS fixes. Implementations must keep emitting on a later
 * collection after a cancellation: recording twice is an ordinary gesture.
 */
interface FixSource {
    fun fixes(): Flow<RawFix>
}
