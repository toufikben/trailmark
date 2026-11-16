package dev.toufik.trailmark.app.service

import dev.toufik.trailmark.analytics.Ghost
import dev.toufik.trailmark.analytics.GhostCandidate
import dev.toufik.trailmark.analytics.GhostMatch
import dev.toufik.trailmark.core.model.StoredPoint
import dev.toufik.trailmark.core.model.WaypointType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The ghost race is measured here rather than in the screen, so its clock and its
 * sign are the two things a runner actually feels: a rebuild restarted the clock
 * and reported the whole run as a lead, and the audio said "behind" while the
 * runner was ahead.
 */
class GhostSessionTest {
    private val baseLat = 36.7538
    private val baseLon = 3.1856

    /** A dead-straight 1 km effort that took 300 s, one fix every 20 m. */
    private fun straightPoints(): List<StoredPoint> = (0..50).map { i ->
        StoredPoint(
            lat = baseLat,
            lon = baseLon + i * 20.0 / 89_200.0,
            timeNanos = i * 6_000_000_000L,
            accuracyM = 5.0,
            type = WaypointType.NORMAL,
        )
    }

    private fun session(t0: Long = 0L, baselineM: Double = 0.0): GhostSession {
        val points = straightPoints()
        val ghost = Ghost(points, fromIndex = 0, totalElapsedNanos = 300_000_000_000L)
        val candidate = GhostCandidate(
            activityId = 7L,
            sport = "RUN",
            startWallMillis = 0L,
            distanceM = ghost.lengthM,
            elapsedNanos = ghost.durationNanos,
            points = points,
        )
        return GhostSession(
            activityId = 99L,
            match = GhostMatch(
                candidate = candidate,
                startDistanceM = 0.0,
                startOffsetM = 0.0,
                fromIndex = 0,
                remainingM = ghost.lengthM,
                remainingNanos = ghost.durationNanos,
                headingDeltaDeg = 0.0,
                routeOverlapM = 0.0,
                ghost = ghost,
            ),
            t0Nanos = t0,
            baselineM = baselineM,
        )
    }

    @Test
    fun raceClockStartsAtTheJoinAndNeverRunsBackwards() {
        val s = session(t0 = 60_000_000_000L)
        assertEquals(0L, s.raceElapsed(60_000_000_000L))
        // A fix stamped before the join (a stale cached point) reads as the start.
        assertEquals(0L, s.raceElapsed(10_000_000_000L))
        assertEquals(30_000_000_000L, s.raceElapsed(90_000_000_000L))
    }

    @Test
    fun raceClockStopsAtTheEndOfTheGhostStretch() {
        val s = session()
        val end = s.match.ghost.durationNanos
        assertEquals(end, s.raceElapsed(end + 600_000_000_000L))
        assertFalse(s.ghostFinished(end - 1L))
        assertTrue(s.ghostFinished(end))
    }

    @Test
    fun coveringMoreGroundThanTheGhostReadsAsALead() {
        val s = session() // ghost is 1 km in 300 s
        // 30 s in, the ghost is at ~100 m; the runner has 150 m.
        val ahead = s.deltaM(30_000_000_000L, 150.0)
        assertTrue(ahead > 0.0, "expected a lead, got $ahead")
        assertEquals(50.0, ahead, 6.0)
    }

    @Test
    fun coveringLessGroundThanTheGhostReadsAsABehind() {
        val s = session()
        val behind = s.deltaM(30_000_000_000L, 50.0)
        assertTrue(behind < 0.0, "expected a deficit, got $behind")
        assertEquals(-50.0, behind, 6.0)
    }

    @Test
    fun theGapIsMeasuredFromTheJoinNotFromTheStartOfTheRun() {
        val s = session(t0 = 120_000_000_000L, baselineM = 400.0)
        // The runner already had 400 m when the race joined: it must not count.
        assertEquals(0.0, s.deltaM(120_000_000_000L, 400.0), 0.001)
    }

    @Test
    fun aFinishedGhostKeepsTheNumberItFinishedWith() {
        val s = session()
        val end = s.match.ghost.durationNanos
        // One metre past the end, the ghost is home: the gap is pinned here.
        val first = ghostGap(s, end + 1_000_000_000L, 1_100.0)
        assertTrue(first.session.frozenDelta != null)
        // Later, with the runner far away, the number must not move.
        val later = ghostGap(first.session, end + 900_000_000_000L, 5_000.0)
        assertEquals(first.metres, later.metres)
        assertTrue(later.session === first.session, "a frozen session is returned as is")
    }

    @Test
    fun theGapIsRoundedSoTheDisplayNeverFlattersTheRunner() {
        val s = session()
        val ghostAt = s.match.ghost.distanceAt(30_000_000_000L)
        // 0.4 m behind is under half a metre and reads as level; 0.6 m does not,
        // and rounding must keep the sign instead of flipping to a tiny lead.
        val level = ghostGap(s, 30_000_000_000L, ghostAt - 0.4)
        assertEquals(0, level.metres)
        val behind = ghostGap(s, 30_000_000_000L, ghostAt - 0.6)
        assertEquals(-1, behind.metres)
    }
}
