package dev.toufik.trailmark.analytics

import dev.toufik.trailmark.core.model.StoredPoint
import dev.toufik.trailmark.core.model.WaypointType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

private fun p(t: Long, lat: Double, alt: Double? = null, speed: Double? = null) =
    StoredPoint(lat, 3.1856, t, 5.0, WaypointType.NORMAL, altitudeM = alt, speedMps = speed)

class AnalysisTest {
    @Test
    fun computesGainLossAndMaxSpeed() {
        val pts = listOf(
            p(0L, 36.7538, alt = 100.0),
            p(10_000_000_000L, 36.7548, alt = 110.0),
            p(20_000_000_000L, 36.7558, alt = 105.0, speed = 4.0),
        )
        val a = analyze(pts, 20_000_000_000L)
        assertEquals(10.0, a.elevationGainM, 1e-9)
        assertEquals(5.0, a.elevationLossM, 1e-9)
        assertEquals(4.0, a.maxSpeedMps, 1e-9)
        assertTrue(a.distanceM > 200.0)
        assertTrue(a.movingNanos > 0)
    }

    @Test
    fun ignoresRejectedPointsAndNullAltitudes() {
        val rejected = StoredPoint(36.09, 3.0, 5L, 5.0, WaypointType.REJECTED, altitudeM = 900.0)
        val pts = listOf(p(0L, 36.7538, alt = null), rejected, p(5_000_000_000L, 36.7539, alt = null))
        val a = analyze(pts, 5_000_000_000L)
        assertEquals(0.0, a.elevationGainM, 1e-9)
        assertTrue(a.distanceM < 50.0, "the ghost point is not path")
    }

    @Test
    fun emptyTrackIsAllZero() {
        val a = analyze(emptyList(), 0L)
        assertEquals(0.0, a.distanceM, 1e-9)
        assertEquals(0.0, a.maxSpeedMps, 1e-9)
        assertTrue(a.splits.isEmpty())
    }
}

class GhostTest {
    private val ghost = Ghost(
        listOf(p(0L, 36.7538), p(100_000_000_000L, 36.7548)),
        0L,
    )

    @Test
    fun interpolatesDistanceByTime() {
        assertEquals(ghost.lengthM, ghost.distanceAt(100_000_000_000L), 1e-6)
        assertEquals(ghost.lengthM / 2, ghost.distanceAt(50_000_000_000L), 2.0)
        assertEquals(0.0, ghost.distanceAt(-1L), 1e-9)
    }

    @Test
    fun deltaIsPositiveWhenAhead() {
        val mid = ghost.distanceAt(50_000_000_000L)
        assertTrue(ghostDeltaM(ghost, mid + 10, 50_000_000_000L) > 9.0)
        assertTrue(ghostDeltaM(ghost, mid - 10, 50_000_000_000L) < -9.0)
    }
}

class SegmentMatcherTest {
    @Test
    fun matchesTheFirstPassageBetweenStartAndEnd() {
        val seg = Segment(1, "s", 36.7538, 3.1856, 36.7548, 3.1856)
        val track = listOf(
            p(0L, 36.7530),
            p(5_000_000_000L, 36.7538),
            p(50_000_000_000L, 36.7548),
        )
        assertEquals(45_000_000_000L, matchEffort(seg, track))
    }

    @Test
    fun nullWhenTheTrackNeverReachesTheSegment() {
        val seg = Segment(1, "s", 36.79, 3.18, 36.80, 3.18)
        assertNull(matchEffort(seg, listOf(p(0L, 36.7538), p(1L, 36.7539))))
    }
}
