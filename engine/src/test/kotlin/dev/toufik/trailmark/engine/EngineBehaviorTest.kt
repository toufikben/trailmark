package dev.toufik.trailmark.engine

import dev.toufik.trailmark.core.model.RawFix
import dev.toufik.trailmark.core.model.StoredPoint
import dev.toufik.trailmark.core.model.WaypointType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

private fun walk(dLat: Double, sec: Long, acc: Double = 4.0) =
    RawFix(36.7538 + dLat / 111111.0, 3.1856, sec * 1_000_000_000L, acc)

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
}
