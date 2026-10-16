package dev.toufik.trailmark.engine

import dev.toufik.trailmark.core.model.RawFix
import dev.toufik.trailmark.core.model.WaypointType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

private fun walk(dLat: Double, sec: Long, acc: Double = 4.0) =
    RawFix(36.7538 + dLat / 111111.0, 3.1856, sec * 1_000_000_000L, acc)

class RecorderEngineTest {
    private val cfg = SportConfig(Sport.RUN, maxSpeedMps = 6.0)

    @Test
    fun firstFixIsAlwaysStoredAndPointsInsideTheGateAreNot() {
        val e = RecorderEngine(cfg, 0L)
        e.onFix(walk(0.0, 0))
        e.onFix(walk(5.0, 1))
        assertEquals(1, e.storedPoints.size)
        assertEquals(0.0, e.distanceMeters, 1e-9)
    }

    @Test
    fun distanceAccumulatesFromTheLastStoredPoint() {
        val e = RecorderEngine(cfg, 0L)
        e.onFix(walk(0.0, 1))
        e.onFix(walk(5.0, 2))
        e.onFix(walk(12.0, 4))
        assertEquals(12.009, e.distanceMeters, 0.01)
        assertEquals(2, e.storedPoints.size)
    }

    @Test
    fun aJumpAddsZeroAndIsKeptFlagged() {
        val e = RecorderEngine(cfg, 0L)
        e.onFix(walk(0.0, 0))
        e.onFix(walk(20.0, 5))
        val before = e.distanceMeters
        e.onFix(walk(300000.0, 6))
        assertEquals(before, e.distanceMeters, 1e-9)
        assertEquals(1, e.stats.rejectedCount)
        assertEquals(WaypointType.REJECTED, e.storedPoints.last().type)
    }

    @Test
    fun nonMonotonicTimestampsAreDropped() {
        val e = RecorderEngine(cfg, 0L)
        e.onFix(walk(0.0, 5))
        e.onFix(walk(100.0, 3))
        assertEquals(1, e.stats.droppedOutOfOrder)
        assertEquals(5_000_000_000L, e.elapsedNanos)
    }

    @Test
    fun aRejectedJumpNeverBecomesTheYardstick() {
        val e = RecorderEngine(SportConfig.forSport(Sport.WALK), 0L)
        e.onFix(walk(0.0, 0))
        e.onFix(walk(50000.0, 1))
        assertEquals(WaypointType.REJECTED, e.storedPoints.last().type)
        for (k in 2L..6L) e.onFix(walk(1.4 * k, k))
        assertEquals(1, e.stats.rejectedCount)
        assertTrue(e.distanceMeters > 0.0, "accumulation resumes after the jump")
        assertTrue(e.distanceMeters < 30.0, "the 50 km jump is never billed")
    }

    @Test
    fun unmeasuredAccuracyBillsNothing() {
        val e = RecorderEngine(cfg, 0L)
        e.onFix(walk(0.0, 0, UNMEASURED_ACCURACY_M))
        for (s in 1L..20L) e.onFix(walk(5.0 * s, s, UNMEASURED_ACCURACY_M))
        assertEquals(0.0, e.distanceMeters, 1e-9)
        assertEquals(WaypointType.NORMAL, e.storedPoints.first().type)
        assertTrue(e.storedPoints.drop(1).all { it.type == WaypointType.IDLE })
        assertFalse(e.isIdle)
    }

    @Test
    fun theSinkSeesOneVerdictPerAcceptedFixAndNeverADrop() {
        val seen = ArrayList<Pair<Long, WaypointType>>()
        val e = RecorderEngine(cfg, 0L, RawFixSink { f, v -> seen.add(f.timeNanos / 1_000_000_000L to v) })
        e.onFix(walk(0.0, 0))
        e.onFix(walk(3.0, 1))
        e.onFix(walk(300000.0, 2))
        assertEquals(
            listOf(0L to WaypointType.NORMAL, 1L to WaypointType.SKIPPED, 2L to WaypointType.REJECTED),
            seen,
        )
        e.onFix(walk(100000.0, 1))
        assertEquals(1, e.stats.droppedOutOfOrder)
        assertEquals(3, seen.size)
    }
}
