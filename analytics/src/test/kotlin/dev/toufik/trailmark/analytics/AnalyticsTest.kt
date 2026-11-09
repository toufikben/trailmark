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
            p(0L, 36.7538, alt = 100.0, speed = 1.0),
            p(10_000_000_000L, 36.7548, alt = 110.0, speed = 2.0),
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
        listOf(p(0L, 36.7538), p(10_000_000_000L, 36.7548)),
    )

    @Test
    fun interpolatesDistanceByTime() {
        assertEquals(ghost.lengthM, ghost.distanceAt(10_000_000_000L), 1e-6)
        assertEquals(ghost.lengthM / 2, ghost.distanceAt(5_000_000_000L), 2.0)
        assertEquals(0.0, ghost.distanceAt(-1L), 1e-9)
    }

    @Test
    fun deltaIsPositiveWhenAhead() {
        val mid = ghost.distanceAt(5_000_000_000L)
        assertTrue(ghostDeltaM(ghost, mid + 10, 5_000_000_000L) > 9.0)
        assertTrue(ghostDeltaM(ghost, mid - 10, 5_000_000_000L) < -9.0)
    }

    @Test
    fun pauseGapIsClampedInsteadOfTeleportingTheGhost() {
        // 0..10 s moving, then a 10-minute pause baked into the stored clock.
        val paused = Ghost(
            listOf(
                p(0L, 36.7538),
                p(10_000_000_000L, 36.7548),
                p(610_000_000_000L, 36.7558),
            ),
        )
        // 15 s of clamped race time: the pause adds only the cap, not 10 min.
        assertEquals(25_000_000_000L, paused.durationNanos)
        val atPause = paused.distanceAt(20_000_000_000L)
        assertTrue(atPause > 0.0 && atPause < paused.lengthM, "ghost mid-route, not at the end")
    }

    @Test
    fun positionInterpolatesAndPointsAlongTheLeg() {
        val mid = ghost.positionAt(5_000_000_000L)!!
        assertTrue(mid.lat > 36.7538 && mid.lat < 36.7548)
        assertTrue(mid.bearingDeg!! in 0.0..1.0, "due north, ${mid.bearingDeg}")
        assertEquals(36.7538, ghost.positionAt(-5L)!!.lat, 1e-9)
        assertEquals(36.7548, ghost.positionAt(99_000_000_000L)!!.lat, 1e-9)
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

    private val hill = Segment(1, "Hill", 36.0, 3.0, 36.001, 3.001)

    private fun pt(
        lat: Double,
        lon: Double,
        nanos: Long,
        type: WaypointType = WaypointType.NORMAL,
    ) = StoredPoint(lat, lon, nanos, 5.0, type)

    @Test
    fun fullPassageReturnsElapsedTime() {
        val track = listOf(
            pt(36.0, 3.0, 0),
            pt(36.0005, 3.0005, 5_000_000_000L),
            pt(36.001, 3.001, 12_000_000_000L),
        )
        assertEquals(12_000_000_000L, matchEffort(hill, track))
    }

    @Test
    fun startWithoutEndReturnsNull() {
        val track = listOf(
            pt(36.0, 3.0, 0),
            pt(36.0002, 3.0002, 3_000_000_000L),
        )
        assertNull(matchEffort(hill, track))
    }

    @Test
    fun rejectedPointsDoNotCountAsEntry() {
        val track = listOf(
            pt(36.0, 3.0, 0, WaypointType.REJECTED),
            pt(36.0005, 3.0005, 5_000_000_000L),
            pt(36.001, 3.001, 10_000_000_000L),
        )
        assertNull(matchEffort(hill, track))
    }

    @Test
    fun firstPassageWinsOverFasterSecondRun() {
        val track = listOf(
            pt(36.0, 3.0, 0),
            pt(36.001, 3.001, 10_000_000_000L),
            pt(36.0, 3.0, 20_000_000_000L),
            pt(36.001, 3.001, 25_000_000_000L),
        )
        assertEquals(10_000_000_000L, matchEffort(hill, track))
    }

    @Test
    fun endReachedAfterLeavingStartRegionStillMatches() {
        val track = listOf(
            pt(36.0, 3.0, 0),
            pt(36.005, 3.005, 4_000_000_000L),
            pt(36.001, 3.001, 9_000_000_000L),
        )
        assertEquals(9_000_000_000L, matchEffort(hill, track))
    }
}
