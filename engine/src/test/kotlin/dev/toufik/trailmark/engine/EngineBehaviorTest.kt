package dev.toufik.trailmark.engine

import dev.toufik.trailmark.core.model.RawFix
import dev.toufik.trailmark.core.model.StoredPoint
import dev.toufik.trailmark.core.model.WaypointType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

private fun walk(dLat: Double, sec: Long, acc: Double = 4.0) =
    RawFix(36.7538 + dLat / 111111.0, 3.1856, sec * 1_000_000_000L, acc)

/** A standing phone's fix: metres east/north of the room's centre, at a given second. */
private fun drift(eastM: Double, northM: Double, sec: Long, acc: Double = 5.0) = RawFix(
    lat = 36.7538 + northM / 111111.0,
    lon = 3.1856 + eastM / (111111.0 * 0.801),
    timeNanos = sec * 1_000_000_000L,
    accuracyM = acc,
    speedMps = 0.0,
)

private fun sp(dLat: Double, sec: Long, type: WaypointType) =
    StoredPoint(36.7538 + dLat / 111111.0, 3.1856, sec * 1_000_000_000L, 3.0, type)

class EngineBehaviorTest {
    private val cfg = SportConfig(Sport.RUN, maxSpeedMps = 6.0)

    @Test
    fun heartbeatBelowTheGateAppendsWithoutBilling() {
        val e = RecorderEngine(cfg, 0L)
        e.onFix(walk(0.0, 0))
        e.onFix(walk(6.0, 15))
        assertEquals(2, e.storedPoints.size)
        assertEquals(0.0, e.distanceMeters, 1e-9)
        e.onFix(walk(20.0, 20))
        assertEquals(20.015, e.distanceMeters, 0.01)
        assertEquals(WaypointType.NORMAL, e.storedPoints.last().type)
    }

    @Test
    fun fiveMinutesStandingCostsNoDistanceButFullTime() {
        val e = RecorderEngine(SportConfig.forSport(Sport.WALK), 0L)
        e.onFix(walk(0.0, 0))
        for (s in 1L..300L) e.onFix(walk(0.0, s, 3.0))
        assertEquals(0.0, e.distanceMeters, 1e-9)
        assertTrue(e.isIdle)
        assertEquals(300_000_000_000L, e.elapsedNanos)
        assertTrue(e.storedPoints.any { it.type == WaypointType.IDLE })
    }

    @Test
    fun oneLoneJumpWhileStandingBillsNothing() {
        val e = RecorderEngine(SportConfig.forSport(Sport.WALK), 0L)
        e.onFix(walk(0.0, 0, 5.0))
        for (s in 1L..30L) e.onFix(walk(0.0, s, 5.0))
        assertTrue(e.isIdle)
        // 14 m over 8 s is a plausible walking speed, so the jump test lets it
        // through; only the movement chain may decide whether it is real.
        e.onFix(walk(14.0, 38L, 5.0))
        assertEquals(0.0, e.distanceMeters, 1e-9)
        assertTrue(e.isIdle)
        assertEquals(WaypointType.IDLE, e.storedPoints.last().type)
    }

    @Test
    fun driftThatCrossesTheGateInEveryDirectionIsNeverBilled() {
        val e = RecorderEngine(SportConfig.forSport(Sport.WALK), 0L)
        e.onFix(drift(0.0, 0.0, 0L))
        for (s in 1L..20L) e.onFix(drift(0.0, 0.0, s))
        assertTrue(e.isIdle)
        // Ten minutes in one room: every fix lands 9-14 m from the last one in
        // a new direction, exactly the wander that billed 2 km over a sit.
        val room = listOf(
            Pair(12.0, 0.0), Pair(-9.0, 0.0), Pair(0.0, 12.0), Pair(0.0, -9.0),
            Pair(11.0, 11.0), Pair(-10.0, 9.0), Pair(9.0, -12.0), Pair(-11.0, -10.0),
        )
        var s = 20L
        repeat(40) {
            for ((east, north) in room) {
                s += 8
                e.onFix(drift(east, north, s))
            }
        }
        assertEquals(0.0, e.distanceMeters, 1e-9)
        assertTrue(e.isIdle)
    }

    @Test
    fun walkingAfterLongStandStillBillsTheRealDistance() {
        val e = RecorderEngine(SportConfig.forSport(Sport.WALK), 0L)
        e.onFix(walk(0.0, 0, 3.0))
        for (s in 1L..120L) e.onFix(walk(if (s % 2 == 0L) 0.6 else -0.6, s, 3.0))
        assertEquals(0.0, e.distanceMeters, 0.01)
        assertTrue(e.isIdle)
        // Then the runner walks north at 2 m/s for forty seconds.
        for (k in 1L..40L) e.onFix(walk(2.0 * k, 120L + k, 3.0))
        val d = e.distanceMeters
        assertTrue(d > 60.0, "walk after standing must accrue, got $d")
        assertTrue(d < 82.0, "and must not invent metres, got $d")
        assertFalse(e.isIdle)
    }

    @Test
    fun aReportedWalkSpeedEndsIdleAfterTwoConsistentSteps() {
        val e = RecorderEngine(SportConfig.forSport(Sport.WALK), 0L)
        e.onFix(walk(0.0, 0, 3.0))
        for (s in 1L..60L) e.onFix(walk(0.0, s, 3.0))
        assertTrue(e.isIdle)
        for (k in 1L..12L) {
            e.onFix(
                RawFix(
                    36.7538 + 2.0 * k / 111111.0,
                    3.1856,
                    (60 + k) * 1_000_000_000L,
                    3.0,
                    speedMps = 1.5,
                ),
            )
        }
        assertFalse(e.isIdle)
        assertTrue(e.distanceMeters > 15.0, "got ${e.distanceMeters}")
    }

    @Test
    fun honestWalkingAccruesAfterATransportGapThatIsNeverBilled() {
        val e = RecorderEngine(SportConfig.forSport(Sport.WALK), 0L)
        for (t in 0L..30L) e.onFix(walk(1.4 * t, t))
        val pre = e.distanceMeters
        assertEquals(42.03, pre, 0.5)
        e.onFix(walk(42.0 + 500.0, 31L))
        for (k in 1L..30L) e.onFix(walk(542.0 + 1.4 * k, 31L + k))
        val total = e.distanceMeters
        assertTrue(total > pre + 20, "post-transport walking must accrue")
        assertTrue(total < 100.0, "the 500 m gap never enters the odometer")
    }

    @Test
    fun aOneKilometreWalkYieldsOneFullSplit() {
        val e = RecorderEngine(SportConfig.forSport(Sport.WALK), 0L)
        var i = 0
        while (i * 5.0 <= 1000.0) {
            e.onFix(RawFix(36.7538 + (i * 5.0) / 111111.0, 3.1856, i * 4L * 1_000_000_000L, 3.0, speedMps = 1.25))
            i++
        }
        assertTrue(e.distanceMeters > 990.0)
        val splits = computeSplits(e.storedPoints)
        assertEquals(1, splits.size)
        assertEquals(1, splits.first().kmIndex)
        assertTrue(splits.first().paceSecPerKm > 0)
    }

    @Test
    fun splitsSkipRejectedAndSurviveZeroLengthSegments() {
        val pts = listOf(
            sp(0.0, 0, WaypointType.NORMAL),
            sp(60000.0, 10, WaypointType.REJECTED),
            sp(500.0, 20, WaypointType.NORMAL),
            sp(500.0, 35, WaypointType.IDLE),
            sp(1000.0, 60, WaypointType.NORMAL),
        )
        val splits = computeSplits(pts)
        assertEquals(1, splits.size)
        assertEquals(1000.0, splits.single().distanceM, 1e-9)
    }

    @Test
    fun splitsOnEmptyAndSinglePointDoNotThrow() {
        assertTrue(computeSplits(emptyList()).isEmpty())
        assertTrue(computeSplits(listOf(sp(0.0, 0, WaypointType.NORMAL))).isEmpty())
    }

    @Test
    fun splitCarriesSlowerGapPaceOnAClimb() {
        val pts = ArrayList<StoredPoint>()
        var i = 0
        while (i * 5.0 <= 1000.0) {
            val d = i * 5.0
            pts.add(
                StoredPoint(
                    lat = 36.7538 + d / 111111.0,
                    lon = 3.1856,
                    timeNanos = i * 4_000_000_000L,
                    accuracyM = 3.0,
                    type = WaypointType.NORMAL,
                    altitudeM = 100.0 + d * 0.1,
                ),
            )
            i++
        }
        val splits = computeSplits(pts)
        assertEquals(1, splits.size)
        val gap = splits.single().gapPaceSecPerKm
        assertTrue(gap != null)
        assertTrue(
            gap!! > splits.single().paceSecPerKm,
            "a 10% climb must price a slower flat-equivalent pace",
        )
    }
}
