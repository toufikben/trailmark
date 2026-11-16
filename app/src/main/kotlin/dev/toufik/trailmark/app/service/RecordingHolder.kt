package dev.toufik.trailmark.app.service

import dev.toufik.trailmark.analytics.GhostMatch
import dev.toufik.trailmark.core.model.StoredPoint
import dev.toufik.trailmark.recording.RecordingState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * A ghost race in progress. It lives here, and not in the recording screen,
 * because that screen is destroyed whenever the tab changes or the device
 * rotates: rebuilt state would restart the clock and report the whole run as a
 * lead over the ghost. The session is also what lets the foreground service keep
 * measuring and speaking the gap while no screen exists.
 *
 * [t0Nanos] and [baselineM] are the live elapsed time and distance at the moment
 * this race joined the old track, so the gap is measured from the join and not
 * from the start of the run.
 */
data class GhostSession(
    /** Live activity being recorded. A session for another activity is stale. */
    val activityId: Long,
    val match: GhostMatch,
    val t0Nanos: Long,
    val baselineM: Double,
    /** Set once the first few hundred metres confirmed the routes share ground. */
    val validated: Boolean = false,
    /** The gap at the moment the ghost crossed its own finish, kept so it stops moving. */
    val frozenDelta: Int? = null,
) {
    val candidateId: Long get() = match.candidate.activityId

    /** The stretch of the old effort still to be raced, for drawing. */
    val route: List<StoredPoint> get() = match.ghostRoute

    /** Live elapsed time inside the race, never past the end of the ghost. */
    fun raceElapsed(elapsedNanos: Long): Long =
        (elapsedNanos - t0Nanos).coerceIn(0L, match.ghost.durationNanos)

    /** Gap in metres from the join onward; positive means the live run leads. */
    fun deltaM(elapsedNanos: Long, distanceM: Double): Double =
        distanceM - baselineM - match.ghost.distanceAt(raceElapsed(elapsedNanos))

    /** True once the ghost has reached the end of its stretch. */
    fun ghostFinished(elapsedNanos: Long): Boolean = elapsedNanos - t0Nanos >= match.ghost.durationNanos
}

/** Shared recording state between the foreground service and the UI. */
object RecordingHolder {
    private val _state = MutableStateFlow(RecordingState())
    val state: StateFlow<RecordingState> = _state

    fun update(s: RecordingState) {
        _state.value = s
    }

    private val _session = MutableStateFlow<GhostSession?>(null)
    val session: StateFlow<GhostSession?> = _session

    fun setSession(s: GhostSession?) {
        if (_session.value != s) _session.value = s
    }

    /** Every rival that matched at the start, best first, so a rejected one can be replaced. */
    private val _pool = MutableStateFlow<List<GhostMatch>>(emptyList())
    val pool: StateFlow<List<GhostMatch>> = _pool

    fun setPool(matches: List<GhostMatch>) {
        _pool.value = matches
    }

    /** True when rivals matched but none survived the on-route check. */
    private val _noMatch = MutableStateFlow(false)
    val noMatch: StateFlow<Boolean> = _noMatch

    fun setNoMatch(v: Boolean) {
        if (_noMatch.value != v) _noMatch.value = v
    }

    /** Clears the whole race; called when a run ends or a new one begins. */
    fun clearGhost() {
        _session.value = null
        _pool.value = emptyList()
        _noMatch.value = false
        _ghostDelta.value = null
    }

    /**
     * The live ghost gap in metres, written by the foreground service so it keeps
     * flowing with no screen attached. Positive means the live effort is AHEAD of
     * the ghost.
     */
    private val _ghostDelta = MutableStateFlow<Int?>(null)
    val ghostDelta: StateFlow<Int?> = _ghostDelta

    fun setGhostDelta(deltaM: Int?) {
        if (_ghostDelta.value != deltaM) _ghostDelta.value = deltaM
    }
}
