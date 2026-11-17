package dev.toufik.trailmark.engine

import kotlin.math.max

enum class Sport { WALK, RUN, BIKE, HIKE }

/**
 * Per-sport engine constants. [gateFor] is the store gate:
 * `max(minStoreDistanceM, accuracyFactor * accuracy)` — no upper ceiling, so a
 * poor fix demands more movement than its own uncertainty. An unmeasured 1σ
 * (infinity) makes the gate infinite and bills nothing.
 */
data class SportConfig(
    val sport: Sport,
    val maxSpeedMps: Double,
    val minStoreDistanceM: Double = 3.0,
    val accuracyFactor: Double = 2.0,
    val forceStoreAfterS: Double = 15.0,
    val maxJumpM: Double = 200.0,
    val idleSpeedMps: Double = 0.4,
    val idleAfterS: Double = 12.0,
) {
    fun gateFor(accuracyM: Double): Double =
        max(minStoreDistanceM, accuracyFactor * accuracyM)

    companion object {
        fun forSport(s: Sport): SportConfig = when (s) {
            // Ceilings are "physically impossible", not "faster than average":
            // a sprint, a fast descent or a brisk walk used to be thrown away as
            // a GPS jump and needed three consistent pairs to recover.
            Sport.WALK -> SportConfig(Sport.WALK, maxSpeedMps = 4.0)
            Sport.RUN -> SportConfig(Sport.RUN, maxSpeedMps = 12.0)
            Sport.BIKE -> SportConfig(Sport.BIKE, maxSpeedMps = 25.0)
            Sport.HIKE -> SportConfig(Sport.HIKE, maxSpeedMps = 4.0)
        }
    }
}
